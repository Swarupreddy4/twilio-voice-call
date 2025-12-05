package com.example.salesforce.controller;

import com.example.salesforce.dto.AccountRequest;
import com.example.salesforce.dto.AccountWithCustomersRequest;
import com.example.salesforce.dto.CommentUpdateRequest;
import com.example.salesforce.dto.CustomerRecord;
import com.example.salesforce.dto.SalesforceResponse;
import com.example.salesforce.dto.TaskRequest;
import com.example.salesforce.service.SalesforceService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/salesforce")
public class SalesforceController {

    private static final Logger logger = LoggerFactory.getLogger(SalesforceController.class);

    @Autowired
    private SalesforceService salesforceService;

    /**
     * Create a Salesforce Account
     * POST /api/salesforce/account
     */
    @PostMapping("/account")
    public Mono<ResponseEntity<SalesforceResponse>> createAccount(@Valid @RequestBody AccountRequest accountRequest) {
        logger.info("Received request to create Account: {}", accountRequest.getName());
        return salesforceService.createAccount(accountRequest)
                .map(response -> {
                    if (response.isSuccess()) {
                        return ResponseEntity.status(HttpStatus.CREATED).body(response);
                    } else {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                    }
                })
                .onErrorResume(error -> {
                    logger.error("Error creating Account", error);
                    SalesforceResponse errorResponse = new SalesforceResponse();
                    errorResponse.setSuccess(false);
                    errorResponse.setMessage("Error creating Account: " + error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
                });
    }

    /**
     * Create a Contact (Customer Record) with personal details
     * POST /api/salesforce/contact
     */
    @PostMapping("/contact")
    public Mono<ResponseEntity<SalesforceResponse>> createContact(
            @Valid @RequestBody CustomerRecord customerRecord,
            @RequestParam(required = false) String accountId) {
        logger.info("Received request to create Contact: {} {}", customerRecord.getFirstName(), customerRecord.getLastName());
        return salesforceService.createContact(customerRecord, accountId)
                .map(response -> {
                    if (response.isSuccess()) {
                        return ResponseEntity.status(HttpStatus.CREATED).body(response);
                    } else {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                    }
                })
                .onErrorResume(error -> {
                    logger.error("Error creating Contact", error);
                    SalesforceResponse errorResponse = new SalesforceResponse();
                    errorResponse.setSuccess(false);
                    errorResponse.setMessage("Error creating Contact: " + error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
                });
    }

    /**
     * Create Account with Customer Records in one operation
     * POST /api/salesforce/account-with-customers
     */
    @PostMapping("/account-with-customers")
    public Mono<ResponseEntity<Map<String, Object>>> createAccountWithCustomers(
            @Valid @RequestBody AccountWithCustomersRequest request) {
        AccountRequest accountRequest = request.getAccount();
        java.util.List<CustomerRecord> customers = request.getCustomers();
        logger.info("Received request to create Account with {} customers", customers != null ? customers.size() : 0);
        
        return salesforceService.createAccount(accountRequest)
                .flatMap(accountResponse -> {
                    if (!accountResponse.isSuccess()) {
                        Map<String, Object> errorResponse = new HashMap<>();
                        errorResponse.put("success", false);
                        errorResponse.put("message", "Failed to create Account");
                        errorResponse.put("account", accountResponse);
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
                    }

                    String accountId = accountResponse.getId();
                    Map<String, Object> response = new HashMap<>();
                    response.put("account", accountResponse);

                    if (customers != null && !customers.isEmpty()) {
                        // Create all contacts
                        return Mono.when(
                                customers.stream()
                                        .map(customer -> salesforceService.createContact(customer, accountId))
                                        .toList()
                        )
                        .then(Mono.just(ResponseEntity.status(HttpStatus.CREATED).body(response)))
                        .onErrorResume(error -> {
                            logger.error("Error creating Contacts", error);
                            response.put("success", false);
                            response.put("message", "Account created but failed to create some Contacts: " + error.getMessage());
                            return Mono.just(ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(response));
                        });
                    } else {
                        response.put("success", true);
                        response.put("message", "Account created successfully");
                        return Mono.just(ResponseEntity.status(HttpStatus.CREATED).body(response));
                    }
                })
                .onErrorResume(error -> {
                    logger.error("Error creating Account with Customers", error);
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("message", "Error: " + error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
                });
    }

    /**
     * Update comments dynamically on a customer record
     * PUT /api/salesforce/contact/{contactId}/comments
     */
    @PutMapping("/contact/{contactId}/comments")
    public Mono<ResponseEntity<SalesforceResponse>> updateComments(
            @PathVariable String contactId,
            @Valid @RequestBody CommentUpdateRequest commentUpdateRequest) {
        logger.info("Received request to update comments for Contact ID: {}", contactId);
        commentUpdateRequest.setRecordId(contactId);
        
        return salesforceService.updateComments(commentUpdateRequest)
                .map(response -> {
                    if (response.isSuccess()) {
                        return ResponseEntity.ok(response);
                    } else {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                    }
                })
                .onErrorResume(error -> {
                    logger.error("Error updating comments", error);
                    SalesforceResponse errorResponse = new SalesforceResponse();
                    errorResponse.setSuccess(false);
                    errorResponse.setMessage("Error updating comments: " + error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
                });
    }

    /**
     * Get a Contact by ID
     * GET /api/salesforce/contact/{contactId}
     */
    @GetMapping("/contact/{contactId}")
    public Mono<ResponseEntity<CustomerRecord>> getContact(@PathVariable String contactId) {
        logger.info("Received request to get Contact ID: {}", contactId);
        return salesforceService.getContact(contactId)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    logger.error("Error retrieving Contact", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                });
    }

    /**
     * Create a Task for a Contact
     * POST /api/salesforce/contact/{contactId}/task
     */
    @PostMapping("/contact/{contactId}/task")
    public Mono<ResponseEntity<Map<String, Object>>> createTaskForContact(
            @PathVariable String contactId,
            @Valid @RequestBody TaskRequest taskRequest) {
        logger.info("Received request to create Task for Contact ID: {}", contactId);

        return salesforceService.getContact(contactId)
                .flatMap(contact -> salesforceService.createTaskForContact(contactId, taskRequest)
                        .map(taskResponse -> {
                            Map<String, Object> response = new HashMap<>();
                            response.put("contact", contact);
                            response.put("task", taskResponse);

                            HttpStatus status = taskResponse.isSuccess() ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST;
                            return ResponseEntity.status(status).body(response);
                        })
                )
                .onErrorResume(error -> {
                    logger.error("Error creating Task for Contact", error);
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("message", "Error creating Task: " + error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
                });
    }

    /**
     * Health check endpoint
     * GET /api/salesforce/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Salesforce Integration");
        return ResponseEntity.ok(response);
    }

    /**
     * Get Account details with related Contacts (personal details)
     * GET /api/salesforce/account/{accountId}
     */
    @GetMapping("/account/{accountId}")
    public Mono<ResponseEntity<JsonNode>> getAccountWithContacts(@PathVariable String accountId) {
        logger.info("Received request to get Account with Contacts. Account ID: {}", accountId);
        return salesforceService.getAccountWithContacts(accountId)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    logger.error("Error retrieving Account with Contacts", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                });
    }
}

