package com.example.twilio.service;

import com.example.twilio.dto.OutboundCallRequest;
import com.example.twilio.dto.OutboundCallResponse;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Service for making outbound calls using Twilio
 */
@Service
public class OutboundCallService {

    private static final Logger logger = LoggerFactory.getLogger(OutboundCallService.class);

    @Value("${twilio.phone.number}")
    private String defaultFromNumber;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${twilio.callback.base.url:}")
    private String callbackBaseUrl;

    /**
     * Makes an outbound call to the specified number
     * The call will connect to the WebSocket media stream endpoint
     *
     * @param request Outbound call request containing destination number
     * @return Response with call SID and status
     */
    public OutboundCallResponse makeOutboundCall(OutboundCallRequest request) {
        try {
            String toNumber = request.getToNumber();
            String fromNumber = request.getFromNumber() != null && !request.getFromNumber().isEmpty()
                    ? request.getFromNumber()
                    : defaultFromNumber;

            String encode = URLEncoder.encode(request.getCustomMessage(),StandardCharsets.UTF_8);
            // Build the TwiML URL that will start the WebSocket stream
            String twimlUrl = buildTwiMLUrl()+"?message="+encode;

            logger.info("=".repeat(80));
            logger.info(">>> INITIATING OUTBOUND CALL");
            logger.info(">>> From: {}", fromNumber);
            logger.info(">>> To: {}", toNumber);
            logger.info(">>> TwiML URL: {}", twimlUrl);
            logger.info("=".repeat(80));

            // Create the call using Twilio SDK
            Call call = Call.creator(
                    new PhoneNumber(toNumber),
                    new PhoneNumber(fromNumber),
                    URI.create(twimlUrl)
            )
                    .setStatusCallback(URI.create(buildStatusCallbackUrl()))
                    .setStatusCallbackEvent(java.util.Arrays.asList(
                            "initiated", "ringing", "answered", "completed"
                    ))
                    .create();

            logger.info(">>> Outbound call created successfully");
            logger.info(">>> Call SID: {}", call.getSid());
            logger.info(">>> Call Status: {}", call.getStatus());
            logger.info(">>> Waiting for call to be answered and WebSocket connection...");
            logger.info("=".repeat(80));

            return new OutboundCallResponse(
                    call.getSid(),
                    call.getStatus().toString(),
                    "Call initiated successfully",
                    toNumber,
                    fromNumber
            );

        } catch (Exception e) {
            logger.error("Error making outbound call", e);
            return new OutboundCallResponse(
                    null,
                    "failed",
                    "Failed to initiate call: " + e.getMessage(),
                    request.getToNumber(),
                    request.getFromNumber() != null ? request.getFromNumber() : defaultFromNumber
            );
        }
    }

    /**
     * Builds the TwiML URL that will be called when the outbound call is answered
     * This URL must be publicly accessible (use ngrok for local development)
     */
    private String buildTwiMLUrl() {
        if (callbackBaseUrl != null && !callbackBaseUrl.trim().isEmpty()) {
            // Ensure URL doesn't end with slash
            String baseUrl = callbackBaseUrl.trim();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            return baseUrl + "/twilio/voice";
        }
        // Fallback: construct from server port (for local development)
        // WARNING: This will only work if Twilio can reach localhost
        // For local development, you MUST use ngrok and set twilio.callback.base.url
        logger.warn("twilio.callback.base.url is not set. Using localhost fallback. " +
                   "This will only work if Twilio can reach your localhost. " +
                   "For local development, use ngrok and set the callback URL.");
        return "http://localhost:" + serverPort + "/twilio/voice";
    }

    /**
     * Builds the status callback URL for call status updates
     */
    private String buildStatusCallbackUrl() {
        if (callbackBaseUrl != null && !callbackBaseUrl.trim().isEmpty()) {
            // Ensure URL doesn't end with slash
            String baseUrl = callbackBaseUrl.trim();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            return baseUrl + "/twilio/status";
        }
        return "http://localhost:" + serverPort + "/twilio/status";
    }

    /**
     * Gets call status by Call SID
     */
    public Call getCallStatus(String callSid) {
        try {
            return Call.fetcher(callSid).fetch();
        } catch (Exception e) {
            logger.error("Error fetching call status for SID: {}", callSid, e);
            return null;
        }
    }
}

