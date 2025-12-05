package com.example.salesforce.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.salesforce.dto.AccountRequest;
import com.example.salesforce.dto.CommentUpdateRequest;
import com.example.salesforce.dto.CustomerRecord;
import com.example.salesforce.dto.SalesforceResponse;
import com.example.salesforce.dto.TaskRequest;
import com.fasterxml.jackson.databind.JsonNode;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import java.time.Duration;

@Service
public class SalesforceService {

    private static final Logger logger = LoggerFactory.getLogger(SalesforceService.class);
    private final WebClient webClient;
    private String accessToken;
    private LocalDateTime tokenExpiry;
    private RSAPrivateKey jwtPrivateKey;

    @Value("${salesforce.client.id:}")
    private String clientId;

    @Value("${salesforce.client.secret:}")
    private String clientSecret;

    @Value("${salesforce.username:}")
    private String username;

    @Value("${salesforce.password:}")
    private String password;

    @Value("${salesforce.security.token:}")
    private String securityToken;

    @Value("${salesforce.instance.url:}")
    private String instanceUrl;

    @Value("${salesforce.login.url:https://playful-shark-jecwtk-dev-ed.trailblaze.my.salesforce.com}")
    private String loginUrl;

    // OAuth scopes for Client Credentials flow
    // Common scopes: api, refresh_token, full, chatter_api, visualforce, web, openid
    // Default: api (Access and manage your data)
    @Value("${salesforce.oauth.scope:api}")
    private String oauthScope;

    // JWT Bearer Flow configuration
    // Typically: issuer = Connected App's clientId, subject = Salesforce username
    @Value("${salesforce.jwt.subject:}")
    private String jwtSubject;

    // Optional: override audience if using test.salesforce.com or a different domain
    // Default: https://login.salesforce.com (for production) or https://test.salesforce.com (for sandbox)
    @Value("${salesforce.jwt.audience:https://playful-shark-jecwtk-dev-ed.trailblaze.my.salesforce.com}")
    private String jwtAudience;

    // Path to the RSA private key (PKCS#8 PEM) used to sign the JWT
    @Value("${salesforce.jwt.private-key-path:}")
    private String jwtPrivateKeyPath;

    @Autowired
    public SalesforceService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Lazily load and cache the RSA private key used for JWT signing.
     */
    private synchronized RSAPrivateKey getJwtPrivateKey() throws Exception {
        if (jwtPrivateKey != null) {
            return jwtPrivateKey;
        }
        if (jwtPrivateKeyPath == null || jwtPrivateKeyPath.isBlank()) {
            throw new IllegalStateException("salesforce.jwt.private-key-path is not configured");
        }

        logger.info("Loading Salesforce JWT private key from path: {}", jwtPrivateKeyPath);
        String pem = Files.readString(Path.of(jwtPrivateKeyPath), StandardCharsets.UTF_8);
        pem = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                 .replace("-----END PRIVATE KEY-----", "")
                 .replaceAll("\\s+", "");

        byte[] decoded = Base64.getDecoder().decode(pem);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        jwtPrivateKey = (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
        logger.info("Salesforce JWT private key loaded successfully");
        return jwtPrivateKey;
    }

    /**
     * Authenticate with Salesforce and get access token using JWT Bearer Flow (grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer)
     * 
     * This flow uses a signed JWT (JSON Web Token) to authenticate without passwords.
     * 
     * Requires:
     * - Connected App with "Enable OAuth Settings" and "Use digital signatures" enabled
     * - RSA private key (PKCS#8 PEM format) for signing the JWT
     * - Public certificate uploaded to Connected App (matches the private key)
     * - Connected App Consumer Key (client_id) - used as JWT issuer
     * - Salesforce username (jwt.subject) - the user to authenticate as
     * 
     * Setup Steps:
     * 1. Generate RSA key pair: openssl genrsa -out salesforce-jwt.key 2048
     * 2. Convert to PKCS#8: openssl pkcs8 -topk8 -inform PEM -outform PEM -in salesforce-jwt.key -out salesforce-jwt-pkcs8.key -nocrypt
     * 3. Generate certificate: openssl req -new -x509 -key salesforce-jwt.key -out salesforce-jwt.crt -days 365 -subj "//CN=SalesforceJWTIntegration"
     * 4. Upload salesforce-jwt.crt to Connected App: Setup → App Manager → Your Connected App → Edit → Enable "Use digital signatures" → Upload certificate
     * 5. Configure in application.properties: salesforce.jwt.private-key-path, salesforce.jwt.subject, salesforce.jwt.audience
     */
    private Mono<String> getAccessToken() {
        // If we have a valid token, return it
        if (accessToken != null && tokenExpiry != null && LocalDateTime.now().isBefore(tokenExpiry)) {
            return Mono.just(accessToken);
        }

        logger.info("Authenticating with Salesforce using JWT Bearer Flow");
        logger.debug("Using login URL: {}", loginUrl);
        logger.debug("JWT Subject (username): {}", jwtSubject);
        logger.debug("JWT Audience: {}", jwtAudience);
        logger.debug("Client ID (issuer): {}", clientId != null && !clientId.isEmpty() ? "***configured***" : "NOT CONFIGURED");

        // Validate required JWT configuration
        if (jwtSubject == null || jwtSubject.trim().isEmpty()) {
            logger.error("JWT subject (username) is not configured. Please set salesforce.jwt.subject in application.properties");
            return Mono.error(new IllegalStateException("JWT subject not configured"));
        }
        if (jwtPrivateKeyPath == null || jwtPrivateKeyPath.trim().isEmpty()) {
            logger.error("JWT private key path is not configured. Please set salesforce.jwt.private-key-path in application.properties");
            return Mono.error(new IllegalStateException("JWT private key path not configured"));
        }

        return Mono.fromCallable(() -> {
                    // Load private key
                    RSAPrivateKey privateKey = getJwtPrivateKey();

                    // Create JWT claims
                    Date now = new Date();
                    Date exp = Date.from(now.toInstant().plusSeconds(3 * 60)); // JWT expires in 3 minutes

                    logger.debug("Creating JWT with issuer: {}, subject: {}, audience: {}, issued: {}, expires: {}", 
                            clientId, jwtSubject, jwtAudience, now, exp);

                    JWTClaimsSet claims = new JWTClaimsSet.Builder()
                            .issuer(clientId)          // Connected App Consumer Key
                            .subject(jwtSubject)       // Salesforce username
                            .audience(jwtAudience)      // login URL (https://login.salesforce.com or https://test.salesforce.com)
                            .issueTime(now)
                            .expirationTime(exp)
                            .build();

                    // Sign JWT with RSA private key
                    SignedJWT signedJWT = new SignedJWT(
                            new JWSHeader(JWSAlgorithm.RS256),
                            claims
                    );

                    JWSSigner signer = new RSASSASigner(privateKey);
                    signedJWT.sign(signer);

                    String assertion = signedJWT.serialize();
                    logger.debug("JWT assertion created successfully (length: {}, first 50 chars: {})", 
                            assertion.length(), assertion.substring(0, Math.min(50, assertion.length())));
                    return assertion;
                })
                .flatMap(assertion -> {
                        // For JWT Bearer flow, Salesforce requires using standard login endpoints
                        // Custom domains often don't work reliably for JWT Bearer token exchange
                        String tokenEndpoint;
                        boolean isCustomDomain = loginUrl.contains("trailblaze.my.salesforce.com") || 
                                                (loginUrl.contains(".salesforce.com") && 
                                                 !loginUrl.contains("login.salesforce.com") && 
                                                 !loginUrl.contains("test.salesforce.com"));
                        
                        // Determine token endpoint - JWT Bearer flow requires standard endpoints
                        // Match the endpoint to the JWT audience for best compatibility
                        if (jwtAudience.contains("test.salesforce.com")) {
                            tokenEndpoint = "https://login.salesforce.com/services/oauth2/token";
                            logger.info("Using test.salesforce.com endpoint (matches JWT audience)");
                        } else if (jwtAudience.contains("login.salesforce.com")) {
                            tokenEndpoint = "https://login.salesforce.com/services/oauth2/token";
                            logger.info("Using login.salesforce.com endpoint (matches JWT audience)");
                        } else if (isCustomDomain) {
                            // Fallback: if audience doesn't specify, use test.salesforce.com for dev orgs
                            tokenEndpoint = "https://login.salesforce.com/services/oauth2/token";
                            logger.warn("Custom domain detected but JWT audience doesn't specify standard endpoint.");
                            logger.warn("Using test.salesforce.com as fallback. Consider setting salesforce.jwt.audience=https://test.salesforce.com");
                        } else if (loginUrl.contains("login.salesforce.com") || loginUrl.contains("test.salesforce.com")) {
                            tokenEndpoint = loginUrl + "/services/oauth2/token";
                        } else {
                            tokenEndpoint = loginUrl + "/services/oauth2/token";
                        }
                        
                        logger.info("Sending JWT assertion to token endpoint: {}", tokenEndpoint);
                        logger.info("JWT audience configured: {}", jwtAudience);
                        logger.debug("JWT assertion length: {} characters", assertion.length());
                        
                        // Critical validation: JWT audience MUST match the token endpoint
                        boolean audienceMatches = (tokenEndpoint.contains("test.salesforce.com") && jwtAudience.contains("test.salesforce.com")) ||
                                                 (tokenEndpoint.contains("login.salesforce.com") && jwtAudience.contains("login.salesforce.com"));
                        
                        if (!audienceMatches) {
                            logger.error("CRITICAL: JWT audience ({}) does NOT match token endpoint ({})", jwtAudience, tokenEndpoint);
                            logger.error("This will cause authentication to fail!");
                            logger.error("SOLUTION: Set salesforce.jwt.audience to match the endpoint:");
                            if (tokenEndpoint.contains("test.salesforce.com")) {
                                logger.error("  salesforce.jwt.audience=https://test.salesforce.com");
                            } else {
                                logger.error("  salesforce.jwt.audience=https://login.salesforce.com");
                            }
                        }
                        
                        return webClient.post()
                                .uri(tokenEndpoint)
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .body(BodyInserters.fromFormData("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                                        .with("assertion", assertion))
                                .retrieve()
                                .onStatus(status -> status.isError(), response -> {
                                    return response.bodyToMono(String.class)
                                            .flatMap(body -> {
                                                logger.error("Salesforce JWT token exchange failed. Status: {}, Body: {}", 
                                                        response.statusCode(), body);
                                                logger.error("Token endpoint used: {}", tokenEndpoint);
                                                logger.error("JWT audience: {}", jwtAudience);
                                                logger.error("NOTE: Login history shows success, but token exchange failed. This suggests:");
                                                logger.error("  - JWT authentication is working (login accepted)");
                                                logger.error("  - Token endpoint may be having issues or endpoint URL may be incorrect");
                                                
                                                // Provide helpful troubleshooting information
                                                if (body.contains("unknown_error")) {
                                                    logger.error("ERROR: Salesforce returned 'unknown_error' during token exchange");
                                                    logger.error("CRITICAL CHECK: Verify JWT audience matches token endpoint exactly!");
                                                    logger.error("Current configuration:");
                                                    logger.error("  JWT Audience: {}", jwtAudience);
                                                    logger.error("  Token Endpoint: {}", tokenEndpoint);
                                                    logger.error("SOLUTION:");
                                                    logger.error("1. Ensure salesforce.jwt.audience EXACTLY matches the token endpoint:");
                                                    if (tokenEndpoint.contains("test.salesforce.com")) {
                                                        logger.error("   salesforce.jwt.audience=https://test.salesforce.com");
                                                    } else {
                                                        logger.error("   salesforce.jwt.audience=https://login.salesforce.com");
                                                    }
                                                    logger.error("2. For Trailhead/Dev orgs, use: salesforce.jwt.audience=https://test.salesforce.com");
                                                    logger.error("3. Restart application after changing configuration");
                                                    logger.error("4. Wait 30-60 seconds between retries (Salesforce may need time to process)");
                                                } else if (body.contains("invalid_grant")) {
                                                    if (body.contains("user hasn't approved this consumer")) {
                                                        logger.error("ERROR: User '{}' has not approved/authorized this Connected App", jwtSubject);
                                                        logger.error("SOLUTION - Choose ONE of these options:");
                                                        logger.error("OPTION 1 (Easiest): Change Connected App OAuth Policy:");
                                                        logger.error("  - Setup → App Manager → Your Connected App → Edit");
                                                        logger.error("  - OAuth Policies → Permitted Users → Select 'All users may self-authorize'");
                                                        logger.error("  - Save the Connected App");
                                                        logger.error("OPTION 2: Assign user via Permission Set:");
                                                        logger.error("  - Setup → App Manager → Your Connected App → Manage → Manage Permissions → Permission Sets");
                                                        logger.error("  - Create/Edit Permission Set → Connected App Access → Add your Connected App");
                                                        logger.error("  - Assign Permission Set to user: {} (Setup → Users → Permission Set Assignments)", jwtSubject);
                                                    } else {
                                                        logger.error("TROUBLESHOOTING: 'invalid_grant' with JWT usually means:");
                                                        logger.error("1. Certificate not uploaded - Upload public cert to Connected App: Setup → App Manager → Edit → Use digital signatures");
                                                        logger.error("2. Wrong certificate - Ensure uploaded cert matches the private key used to sign JWT");
                                                        logger.error("3. Wrong audience - Verify salesforce.jwt.audience matches login URL (login.salesforce.com or test.salesforce.com)");
                                                        logger.error("4. Wrong subject - Verify salesforce.jwt.subject is correct Salesforce username");
                                                        logger.error("5. Wrong issuer - Verify salesforce.client.id matches Connected App Consumer Key");
                                                        logger.error("6. JWT expired - JWT expires in 3 minutes, ensure system clock is correct");
                                                        logger.error("7. User not authorized - Assign user to Connected App via Permission Set or Profile");
                                                    }
                                                }
                                                
                                                return Mono.error(new RuntimeException(
                                                        "Salesforce JWT authentication failed: " + response.statusCode() + " - " + body));
                                            });
                                })
                                .bodyToMono(JsonNode.class)
                                .retryWhen(Retry.backoff(2, Duration.ofSeconds(10))
                                        .maxBackoff(Duration.ofSeconds(30))
                                        .filter(throwable -> {
                                            if (throwable instanceof RuntimeException ex) {
                                                String message = ex.getMessage();
                                                // Retry on unknown_error (transient Salesforce issues)
                                                // Since login history shows success, this is likely a token endpoint timing issue
                                                return message != null && message.contains("unknown_error");
                                            }
                                            return false;
                                        })
                                        .doBeforeRetry(retrySignal -> {
                                                long totalRetries = retrySignal.totalRetries();
                                                int attempt = (int) totalRetries + 1;
                                                long waitSeconds = Math.min(10L * attempt, 30L);
                                                logger.warn("Retrying JWT token exchange (attempt {}/2) due to unknown_error. Waiting {} seconds...", 
                                                        attempt, waitSeconds);
                                                logger.warn("NOTE: Login history shows success - JWT is accepted, but token exchange needs retry");
                                                logger.warn("If this continues to fail, verify JWT audience matches token endpoint exactly");
                                                logger.warn("Current: audience={}, endpoint={}", jwtAudience, tokenEndpoint);
                                        })
                                );
                })
                .map(response -> {
                    accessToken = response.get("access_token").asText();
                    // Extract instance URL from authentication response
                    if (response.has("instance_url")) {
                        instanceUrl = response.get("instance_url").asText();
                        logger.info("Salesforce instance URL set to: {}", instanceUrl);
                    }
                    if (response.has("expires_in")) {
                        String expiresIn = response.get("expires_in").asText();
                        // Set expiry to 5 minutes before actual expiry for safety
                        tokenExpiry = LocalDateTime.now().plusSeconds(Long.parseLong(expiresIn) - 300);
                        logger.info("Successfully authenticated with Salesforce using JWT Bearer Flow. Token expires at: {}", tokenExpiry);
                    } else {
                        // Fallback: token without explicit expiry, set a default duration
                        tokenExpiry = LocalDateTime.now().plusHours(1);
                        logger.info("Successfully authenticated with Salesforce. Token expiry not provided, defaulting to 1 hour.");
                    }
                    return accessToken;
                })
                .doOnError(error -> {
                    if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException ex) {
                        logger.error("Failed to authenticate with Salesforce using JWT. Status: {}, Body: {}", 
                                ex.getStatusCode(), ex.getResponseBodyAsString());
                    } else {
                        logger.error("Failed to authenticate with Salesforce using JWT", error);
                    }
                });
    }

    /**
     * Create a Salesforce Account
     */
    public Mono<SalesforceResponse> createAccount(AccountRequest accountRequest) {
        return getAccessToken()
                .flatMap(token -> {
                    logger.info("Creating Salesforce Account: {}", accountRequest.getName());

                    Map<String, Object> accountData = new HashMap<>();
                    accountData.put("Name", accountRequest.getName());

                    // Core fields
                    if (accountRequest.getAccountNumber() != null) accountData.put("AccountNumber", accountRequest.getAccountNumber());
                    if (accountRequest.getOwnerId() != null) accountData.put("OwnerId", accountRequest.getOwnerId());
                    if (accountRequest.getSite() != null) accountData.put("Site", accountRequest.getSite());
                    if (accountRequest.getAccountSource() != null) accountData.put("AccountSource", accountRequest.getAccountSource());
                    if (accountRequest.getActive() != null) accountData.put("Active__c", accountRequest.getActive());
                    if (accountRequest.getAnnualRevenue() != null) accountData.put("AnnualRevenue", accountRequest.getAnnualRevenue());

                    // Billing address
                    if (accountRequest.getBillingStreet() != null) accountData.put("BillingStreet", accountRequest.getBillingStreet());
                    if (accountRequest.getBillingCity() != null) accountData.put("BillingCity", accountRequest.getBillingCity());
                    if (accountRequest.getBillingState() != null) accountData.put("BillingState", accountRequest.getBillingState());
                    if (accountRequest.getBillingPostalCode() != null) accountData.put("BillingPostalCode", accountRequest.getBillingPostalCode());
                    if (accountRequest.getBillingCountry() != null) accountData.put("BillingCountry", accountRequest.getBillingCountry());

                    // Shipping address
                    if (accountRequest.getShippingStreet() != null) accountData.put("ShippingStreet", accountRequest.getShippingStreet());
                    if (accountRequest.getShippingCity() != null) accountData.put("ShippingCity", accountRequest.getShippingCity());
                    if (accountRequest.getShippingState() != null) accountData.put("ShippingState", accountRequest.getShippingState());
                    if (accountRequest.getShippingPostalCode() != null) accountData.put("ShippingPostalCode", accountRequest.getShippingPostalCode());
                    if (accountRequest.getShippingCountry() != null) accountData.put("ShippingCountry", accountRequest.getShippingCountry());

                    // Descriptive / business fields
                    if (accountRequest.getIndustry() != null) accountData.put("Industry", accountRequest.getIndustry());
                    if (accountRequest.getPhone() != null) accountData.put("Phone", accountRequest.getPhone());
                    if (accountRequest.getFax() != null) accountData.put("Fax", accountRequest.getFax());
                    if (accountRequest.getWebsite() != null) accountData.put("Website", accountRequest.getWebsite());
                    if (accountRequest.getDescription() != null) accountData.put("Description", accountRequest.getDescription());
                    if (accountRequest.getRating() != null) accountData.put("Rating", accountRequest.getRating());
                    if (accountRequest.getOwnership() != null) accountData.put("Ownership", accountRequest.getOwnership());
                    if (accountRequest.getTickerSymbol() != null) accountData.put("TickerSymbol", accountRequest.getTickerSymbol());
                    if (accountRequest.getType() != null) accountData.put("Type", accountRequest.getType());

                    // Custom business fields
                    if (accountRequest.getCustomerPriority() != null) accountData.put("CustomerPriority__c", accountRequest.getCustomerPriority());
                    if (accountRequest.getSla() != null) accountData.put("SLA__c", accountRequest.getSla());
                    if (accountRequest.getSlaExpirationDate() != null) accountData.put("SLAExpirationDate__c", accountRequest.getSlaExpirationDate());
                    if (accountRequest.getSlaSerialNumber() != null) accountData.put("SLASerialNumber__c", accountRequest.getSlaSerialNumber());
                    if (accountRequest.getUpsellOpportunity() != null) accountData.put("UpsellOpportunity__c", accountRequest.getUpsellOpportunity());
                    if (accountRequest.getTopAccount() != null) accountData.put("Top_Account__c", accountRequest.getTopAccount());
                    if (accountRequest.getPotentialValue() != null) accountData.put("Potential_Value__c", accountRequest.getPotentialValue());
                    if (accountRequest.getSumOfOpportunities() != null) accountData.put("Sum_of_Opportunities__c", accountRequest.getSumOfOpportunities());
                    if (accountRequest.getNumberOfContacts() != null) accountData.put("Number_Of_Contacts__c", accountRequest.getNumberOfContacts());
                    if (accountRequest.getNumberOfLocations() != null) accountData.put("NumberofLocations__c", accountRequest.getNumberOfLocations());
                    if (accountRequest.getMatchBillingAddress() != null) accountData.put("Match_Billing_Address__c", accountRequest.getMatchBillingAddress());

                    // NAICS / SIC
                    if (accountRequest.getNaicsCode() != null) accountData.put("NaicsCode", accountRequest.getNaicsCode());
                    if (accountRequest.getNaicsDescription() != null) accountData.put("NaicsDesc", accountRequest.getNaicsDescription());
                    if (accountRequest.getSicCode() != null) accountData.put("Sic", accountRequest.getSicCode());
                    if (accountRequest.getSicDescription() != null) accountData.put("SicDesc", accountRequest.getSicDescription());

                    // Hierarchy / relationships
                    if (accountRequest.getParentId() != null) accountData.put("ParentId", accountRequest.getParentId());
                    if (accountRequest.getDandbCompanyId() != null) accountData.put("DandbCompanyId", accountRequest.getDandbCompanyId());

                    // Misc fields
                    if (accountRequest.getDunsNumber() != null) accountData.put("DunsNumber", accountRequest.getDunsNumber());
                    if (accountRequest.getJigsaw() != null) accountData.put("Jigsaw", accountRequest.getJigsaw());
                    if (accountRequest.getTier() != null) accountData.put("Tier", accountRequest.getTier());
                    if (accountRequest.getTradestyle() != null) accountData.put("Tradestyle", accountRequest.getTradestyle());
                    if (accountRequest.getYearStarted() != null) accountData.put("YearStarted", accountRequest.getYearStarted());

                    // Custom dynamic fields
                    if (accountRequest.getCustomFields() != null) {
                        accountData.putAll(accountRequest.getCustomFields());
                    }

                    return webClient.post()
                            .uri(instanceUrl + "/services/data/v58.0/sobjects/Account/")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .bodyValue(accountData)
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .map(response -> {
                                SalesforceResponse salesforceResponse = new SalesforceResponse();
                                salesforceResponse.setId(response.get("id").asText());
                                salesforceResponse.setSuccess(response.get("success") != null && response.get("success").asBoolean());
                                salesforceResponse.setMessage("Account created successfully");
                                logger.info("Account created with ID: {}", salesforceResponse.getId());
                                return salesforceResponse;
                            })
                            .onErrorResume(error -> {
                                logger.error("Failed to create Account", error);
                                SalesforceResponse errorResponse = new SalesforceResponse();
                                errorResponse.setSuccess(false);
                                errorResponse.setMessage("Failed to create Account: " + error.getMessage());
                                return Mono.just(errorResponse);
                            });
                });
    }

    /**
     * Create a Contact (Customer Record) with personal details
     */
    public Mono<SalesforceResponse> createContact(CustomerRecord customerRecord, String accountId) {
        return getAccessToken()
                .flatMap(token -> {
                    logger.info("Creating Salesforce Contact: {} {}", customerRecord.getFirstName(), customerRecord.getLastName());

                    Map<String, Object> contactData = new HashMap<>();
                    contactData.put("FirstName", customerRecord.getFirstName());
                    contactData.put("LastName", customerRecord.getLastName());
                    if (customerRecord.getEmail() != null) contactData.put("Email", customerRecord.getEmail());
                    if (customerRecord.getPhone() != null) contactData.put("Phone", customerRecord.getPhone());
                    if (customerRecord.getMobilePhone() != null) contactData.put("MobilePhone", customerRecord.getMobilePhone());
                    if (customerRecord.getMailingStreet() != null) contactData.put("MailingStreet", customerRecord.getMailingStreet());
                    if (customerRecord.getMailingCity() != null) contactData.put("MailingCity", customerRecord.getMailingCity());
                    if (customerRecord.getMailingState() != null) contactData.put("MailingState", customerRecord.getMailingState());
                    if (customerRecord.getMailingPostalCode() != null) contactData.put("MailingPostalCode", customerRecord.getMailingPostalCode());
                    if (customerRecord.getMailingCountry() != null) contactData.put("MailingCountry", customerRecord.getMailingCountry());
                    if (customerRecord.getTitle() != null) contactData.put("Title", customerRecord.getTitle());
                    if (customerRecord.getDepartment() != null) contactData.put("Department", customerRecord.getDepartment());
                    if (customerRecord.getBirthdate() != null) contactData.put("Birthdate", customerRecord.getBirthdate());
                    if (customerRecord.getDescription() != null) contactData.put("Description", customerRecord.getDescription());
                    if (customerRecord.getComments() != null) contactData.put("Comments", customerRecord.getComments());
                    if (accountId != null && !accountId.isEmpty()) {
                        contactData.put("AccountId", accountId);
                    }
                    if (customerRecord.getCustomFields() != null) {
                        contactData.putAll(customerRecord.getCustomFields());
                    }

                    return webClient.post()
                            .uri(instanceUrl + "/services/data/v58.0/sobjects/Contact/")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .bodyValue(contactData)
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .map(response -> {
                                SalesforceResponse salesforceResponse = new SalesforceResponse();
                                salesforceResponse.setId(response.get("id").asText());
                                salesforceResponse.setSuccess(response.get("success") != null && response.get("success").asBoolean());
                                salesforceResponse.setMessage("Contact created successfully");
                                logger.info("Contact created with ID: {}", salesforceResponse.getId());
                                return salesforceResponse;
                            })
                            .onErrorResume(error -> {
                                logger.error("Failed to create Contact", error);
                                SalesforceResponse errorResponse = new SalesforceResponse();
                                errorResponse.setSuccess(false);
                                errorResponse.setMessage("Failed to create Contact: " + error.getMessage());
                                return Mono.just(errorResponse);
                            });
                });
    }

    /**
     * Create a Task associated with a Contact
     */
    public Mono<SalesforceResponse> createTaskForContact(String contactId, TaskRequest taskRequest) {
        return getAccessToken()
                .flatMap(token -> {
                	System.out.println(token+" ===== token ======");
                    logger.info("Creating Task '{}' for Contact ID: {}", taskRequest.getSubject(), contactId);

                    Map<String, Object> taskData = new HashMap<>();
                    taskData.put("Subject", taskRequest.getSubject());
                    taskData.put("WhoId", contactId);
                    taskData.put("Status", taskRequest.getStatus() != null ? taskRequest.getStatus() : "Not Started");

                    if (taskRequest.getDescription() != null) {
                        taskData.put("Description", taskRequest.getDescription());
                    }
                    if (taskRequest.getPriority() != null) {
                        taskData.put("Priority", taskRequest.getPriority());
                    }
                    if (taskRequest.getActivityDate() != null) {
                        taskData.put("ActivityDate", taskRequest.getActivityDate());
                    }
                    if (taskRequest.getType() != null) {
                        taskData.put("Type", taskRequest.getType());
                    }

                    return webClient.post()
                            .uri(instanceUrl + "/services/data/v58.0/sobjects/Task/")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .bodyValue(taskData)
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .map(response -> {
                                SalesforceResponse salesforceResponse = new SalesforceResponse();
                                salesforceResponse.setId(response.get("id").asText());
                                salesforceResponse.setSuccess(response.get("success") != null && response.get("success").asBoolean());
                                salesforceResponse.setMessage("Task created successfully");
                                logger.info("Task created with ID: {}", salesforceResponse.getId());
                                return salesforceResponse;
                            })
                            .onErrorResume(error -> {
                                logger.error("Failed to create Task for Contact ID: {}", contactId, error);
                                SalesforceResponse errorResponse = new SalesforceResponse();
                                errorResponse.setSuccess(false);
                                errorResponse.setMessage("Failed to create Task: " + error.getMessage());
                                return Mono.just(errorResponse);
                            });
                });
    }

    /**
     * Create a call-related Task linked to a Contact and optionally an Account.
     * The Task will contain call status, call SID, phone, and conversation log
     * (user speech + AI responses) in the Description.
     */
    public Mono<SalesforceResponse> createCallTaskForContactAndAccount(
            String contactId,
            String accountId,
            String phone,
            String callStatus,
            String callSid,
            String conversationLog
    ) {
        return getAccessToken()
                .flatMap(token -> {
                    logger.info("Creating call Task for Contact {}, Account {}, CallSid={}",
                            contactId, accountId, callSid);

                    Map<String, Object> taskData = new HashMap<>();

                    // Linkage
                    if (contactId != null && !contactId.isEmpty()) {
                        taskData.put("WhoId", contactId);
                    }
                    if (accountId != null && !accountId.isEmpty()) {
                        taskData.put("WhatId", accountId);
                    }

                    // Basic Task fields
                    String normalizedStatus = callStatus != null ? callStatus.trim().toLowerCase() : "completed";
                    String subject = "AI Voice Call - " + (callStatus != null ? callStatus : "Completed");
                    taskData.put("Subject", subject);

                    // Map Twilio call status → valid Salesforce Task Status picklist values
                    // Valid values (from org): Not Started, In Progress, Completed, Waiting on someone else, Deferred, Not Answered
                    String taskStatus;
                    switch (normalizedStatus) {
                        case "completed":
                        case "completed-remote":
                            taskStatus = "Completed";
                            break;
                        case "in-progress":
                        case "ringing":
                        case "queued":
                            taskStatus = "In Progress";
                            break;
                        case "no-answer":
                        case "busy":
                        case "failed":
                        case "canceled":
                            taskStatus = "Not Answered";
                            break;
                        default:
                            taskStatus = "Not Started";
                            break;
                    }
                    taskData.put("Status", taskStatus);
                    taskData.put("TaskSubtype", "Call");
                    taskData.put("CallType", "Outbound");
                    if (callStatus != null && !callStatus.isEmpty()) {
                        taskData.put("CallDisposition", callStatus);
                    }

                    if (callSid != null && !callSid.isEmpty()) {
                        taskData.put("CallObject", callSid);
                    }
            

                    // Conversation log in Comments/Description
                    if (conversationLog != null && !conversationLog.isEmpty()) {
                        taskData.put("Description", conversationLog);
                    }

                    // Set Due Date to today
                    taskData.put("ActivityDate", java.time.LocalDate.now().toString());

                    return webClient.post()
                            .uri(instanceUrl + "/services/data/v58.0/sobjects/Task/")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .bodyValue(taskData)
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .map(response -> {
                                SalesforceResponse salesforceResponse = new SalesforceResponse();
                                salesforceResponse.setId(response.get("id").asText());
                                salesforceResponse.setSuccess(response.get("success") != null && response.get("success").asBoolean());
                                salesforceResponse.setMessage("Call Task created successfully");
                                logger.info("Call Task created with ID: {}", salesforceResponse.getId());
                                return salesforceResponse;
                            })
                            .onErrorResume(error -> {
                                logger.error("Failed to create Call Task", error);
                                SalesforceResponse errorResponse = new SalesforceResponse();
                                errorResponse.setSuccess(false);
                                errorResponse.setMessage("Failed to create Call Task: " + error.getMessage());
                                return Mono.just(errorResponse);
                            });
                });
    }

    /**
     * Update comments dynamically on a customer record (Contact)
     */
    public Mono<SalesforceResponse> updateComments(CommentUpdateRequest commentUpdateRequest) {
        return getAccessToken()
                .flatMap(token -> {
                    logger.info("Updating comments for record ID: {}", commentUpdateRequest.getRecordId());

                    Map<String, Object> updateData = new HashMap<>();
                    
                    // Append new comment with timestamp
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    String newComment = String.format("[%s] %s", timestamp, commentUpdateRequest.getComments());
                    
                    // First, get existing comments
                    return webClient.get()
                            .uri(instanceUrl + "/services/data/v58.0/sobjects/Contact/" + commentUpdateRequest.getRecordId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .flatMap(existingRecord -> {
                                String existingComments = existingRecord.has("Comments") && !existingRecord.get("Comments").isNull() 
                                        ? existingRecord.get("Comments").asText() 
                                        : "";
                                
                                // Append new comment to existing comments
                                String updatedComments = existingComments.isEmpty() 
                                        ? newComment 
                                        : existingComments + "\n\n" + newComment;
                                
                                updateData.put("Comments", updatedComments);
                                if (commentUpdateRequest.getDescription() != null) {
                                    updateData.put("Description", commentUpdateRequest.getDescription());
                                }

                                return webClient.patch()
                                        .uri(instanceUrl + "/services/data/v58.0/sobjects/Contact/" + commentUpdateRequest.getRecordId())
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                        .bodyValue(updateData)
                                        .retrieve()
                                        .bodyToMono(JsonNode.class)
                                        .map(response -> {
                                            SalesforceResponse salesforceResponse = new SalesforceResponse();
                                            salesforceResponse.setId(commentUpdateRequest.getRecordId());
                                            salesforceResponse.setSuccess(true);
                                            salesforceResponse.setMessage("Comments updated successfully");
                                            logger.info("Comments updated for record ID: {}", commentUpdateRequest.getRecordId());
                                            return salesforceResponse;
                                        });
                            })
                            .onErrorResume(error -> {
                                logger.error("Failed to update comments", error);
                                SalesforceResponse errorResponse = new SalesforceResponse();
                                errorResponse.setSuccess(false);
                                errorResponse.setMessage("Failed to update comments: " + error.getMessage());
                                return Mono.just(errorResponse);
                            });
                });
    }

    /**
     * Get a Contact by ID
     */
    public Mono<CustomerRecord> getContact(String contactId) {
        return getAccessToken()
                .flatMap(token -> {
                    logger.info("Retrieving Contact with ID: {}", contactId);

                    return webClient.get()
                            .uri(instanceUrl + "/services/data/v58.0/sobjects/Contact/" + contactId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .map(response -> {
                                CustomerRecord customerRecord = new CustomerRecord();
                                customerRecord.setId(response.get("Id").asText());
                                if (response.has("FirstName")) customerRecord.setFirstName(response.get("FirstName").asText());
                                if (response.has("LastName")) customerRecord.setLastName(response.get("LastName").asText());
                                if (response.has("Email")) customerRecord.setEmail(response.get("Email").asText());
                                if (response.has("Phone")) customerRecord.setPhone(response.get("Phone").asText());
                                if (response.has("MobilePhone")) customerRecord.setMobilePhone(response.get("MobilePhone").asText());
                                if (response.has("Comments")) customerRecord.setComments(response.get("Comments").asText());
                                if (response.has("Description")) customerRecord.setDescription(response.get("Description").asText());
                                logger.info("Contact retrieved: {} {}", customerRecord.getFirstName(), customerRecord.getLastName());
                                return customerRecord;
                            })
                            .onErrorResume(error -> {
                                logger.error("Failed to retrieve Contact", error);
                                return Mono.error(error);
                            });
                });
    }

    /**
     * Get Account details along with related Contacts (personal details)
     */
    public Mono<JsonNode> getAccountWithContacts(String accountId) {
        return getAccessToken()
                .flatMap(token -> {
                    logger.info("Retrieving Account with Contacts. Account ID: {}", accountId);

                    String soql = "SELECT Id, Name, Phone, Website, BillingStreet, BillingCity, " +
                            "BillingState, BillingPostalCode, BillingCountry, Description, " +
                            "(SELECT Id, FirstName, LastName, Email, Phone, MobilePhone, MailingStreet, " +
                            "MailingCity, MailingState, MailingPostalCode, MailingCountry, Title, Department " +
                            "FROM Contacts) " +
                            "FROM Account WHERE Id = '" + accountId + "'";

                    String encodedSoql = URLEncoder.encode(soql, StandardCharsets.UTF_8);

                    return webClient.get()
                            .uri(instanceUrl + "/services/data/v58.0/query?q=" + encodedSoql)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .doOnNext(response -> logger.info("Successfully retrieved Account with Contacts for ID: {}", accountId))
                            .onErrorResume(error -> {
                                logger.error("Failed to retrieve Account with Contacts", error);
                                return Mono.error(error);
                            });
                });
    }
}

