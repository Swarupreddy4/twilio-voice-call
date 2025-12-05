package com.example.twilio.controller;

import com.example.salesforce.dto.CustomerRecord;
import com.example.salesforce.service.SalesforceService;
import com.example.twilio.dto.OutboundCallRequest;
import com.example.twilio.dto.OutboundCallResponse;
import com.example.twilio.service.OutboundCallService;
import com.example.twilio.service.TwilioVoiceService;
import com.example.twilio.service.ConversationLogger;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/twilio")
public class TwilioVoiceController {

    @Autowired
    private TwilioVoiceService twilioVoiceService;

    @Autowired
    private OutboundCallService outboundCallService;

    @Value("${twilio.phone.number}")
    private String twilioPhoneNumber;
    @Autowired
    private SalesforceService salesforceService;

    @Autowired(required = false)
    private ConversationLogger conversationLogger;

    // Track call → Salesforce context for creating Tasks on completion
    private final java.util.concurrent.ConcurrentMap<String, CallTaskContext> callContextBySid =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static class CallTaskContext {
        final String contactId;
        final String accountId;
        final String phone;

        CallTaskContext(String contactId, String accountId, String phone) {
            this.contactId = contactId;
            this.accountId = accountId;
            this.phone = phone;
        }
    }
    /**
     * Endpoint for Twilio to initiate a voice call (inbound or outbound)
     * This endpoint returns TwiML to start the Media Stream
     */
    @PostMapping(value = "/voice", produces = MediaType.APPLICATION_XML_VALUE)
    public String handleVoiceCall(HttpServletRequest request) {
        // Get custom message from query parameter if provided
        String customMessage = request.getParameter("message");
        String callSid = request.getParameter("CallSid");
        String callStatus = request.getParameter("CallStatus");
        
        // Log call information
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TwilioVoiceController.class);
        logger.info(">>> Voice call webhook received - Call SID: {}, Status: {}", callSid, callStatus);
        logger.info(">>> Generating TwiML with WebSocket stream for bidirectional conversation");
        
        String twiml = twilioVoiceService.generateVoiceTwiML(request.getRequestURL().toString(), customMessage);
        
        logger.info(">>> TwiML generated and sent to Twilio for call {}", callSid);
        return twiml;
    }

    /**
     * Make an outbound call with AI voice agent
     * POST /twilio/outbound/call
     * 
     * Request body:
     * {
     *   "toNumber": "+1234567890",
     *   "fromNumber": "+1234567890" (optional),
     *   "customMessage": "Hello! This is a test call." (optional)
     * }
     */
    @PostMapping("/outbound/call")
    public ResponseEntity<OutboundCallResponse> makeOutboundCall(
            @Valid @RequestBody OutboundCallRequest request) {
        // If a Salesforce Contact ID is provided, fetch the Contact and
        // populate the outbound call details from Salesforce.
        if (request.getContactId() != null && !request.getContactId().isEmpty()) {
            CustomerRecord contact = salesforceService.getContact(request.getContactId())
                    .doOnError(error -> {
                        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TwilioVoiceController.class);
                        logger.error("Failed to fetch Salesforce contact for outbound call. ContactId={}", request.getContactId(), error);
                    })
                    .block(); // Simple blocking bridge from reactive to sync for this controller

            if (contact == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new OutboundCallResponse(
                                null,
                                "failed",
                                "Failed to fetch Salesforce Contact for ID: " + request.getContactId(),
                                request.getToNumber(),
                                request.getFromNumber() != null ? request.getFromNumber() : twilioPhoneNumber
                        ));
            }

            // Determine the destination number from Contact (prefer MobilePhone, then Phone)
            String destinationNumber = contact.getMobilePhone() != null && !contact.getMobilePhone().isEmpty()
                    ? contact.getMobilePhone()
                    : contact.getPhone();

            if (destinationNumber == null || destinationNumber.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new OutboundCallResponse(
                                null,
                                "failed",
                                "Salesforce Contact does not have a MobilePhone or Phone number",
                                null,
                                request.getFromNumber() != null ? request.getFromNumber() : twilioPhoneNumber
                        ));
            }

            request.setToNumber(destinationNumber);

            // Use Contact Description as the custom message if not explicitly provided
            if (request.getCustomMessage() == null || request.getCustomMessage().isEmpty()) {
                StringBuilder messageBuilder = new StringBuilder();
                if (contact.getDescription() != null && !contact.getDescription().isEmpty()) {
                	 messageBuilder.append("Hello, ")
                    .append(contact.getFirstName() != null ? contact.getFirstName() + " " : "")
                    .append(contact.getLastName() != null ? contact.getLastName() : "the contact");
                    
                    messageBuilder.append(", "+contact.getDescription());
                } else {
                    messageBuilder.append("This is an automated call for ")
                            .append(contact.getFirstName() != null ? contact.getFirstName() + " " : "")
                            .append(contact.getLastName() != null ? contact.getLastName() : "the contact")
                            .append(".");
                }
                request.setCustomMessage(messageBuilder.toString());

            }
        }

        OutboundCallResponse response = outboundCallService.makeOutboundCall(request);

        // Store call context so we can create a Salesforce Task when the call completes
        if (response.getCallSid() != null && request.getContactId() != null && !request.getContactId().isEmpty()) {
            String phone = request.getToNumber();
            CallTaskContext ctx = new CallTaskContext(request.getContactId(), request.getAccountId(), phone);
            callContextBySid.put(response.getCallSid(), ctx);
            if (conversationLogger != null) {
                conversationLogger.logConversation(response.getCallSid(),
                		response.getCallSid() ,
                        "AI",
                        request.getCustomMessage());
            }
        }

        if (response.getCallSid() != null) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get call status by Call SID
     * GET /twilio/call/{callSid}/status
     */
    @GetMapping("/call/{callSid}/status")
    public ResponseEntity<?> getCallStatus(@PathVariable String callSid) {
        var call = outboundCallService.getCallStatus(callSid);
        if (call != null) {
            return ResponseEntity.ok(call);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * WebSocket endpoint URL for Twilio Media Stream
     * This is called by Twilio to establish the WebSocket connection
     */
    @GetMapping("/media-stream-url")
    public String getMediaStreamUrl(HttpServletRequest request) {
        String baseUrl = request.getRequestURL().toString()
                .replace(request.getRequestURI(), "");
        return baseUrl + "/twilio/media-stream";
    }

    /**
     * Status callback endpoint for call status updates
     */
    @PostMapping("/status")
    public void handleCallStatus(@RequestParam("CallSid") String callSid,
                                 @RequestParam("CallStatus") String callStatus) {
        twilioVoiceService.handleCallStatus(callSid, callStatus);

        // Create a Salesforce Task for any terminal call status (answered or not)
        String normalizedStatus = callStatus != null ? callStatus.toLowerCase() : "";
        boolean isTerminalStatus = normalizedStatus.equals("completed")
                || normalizedStatus.equals("no-answer")
                || normalizedStatus.equals("busy")
                || normalizedStatus.equals("failed")
                || normalizedStatus.equals("canceled")
                || normalizedStatus.equals("completed-remote");

        if (isTerminalStatus) {
            CallTaskContext ctx = callContextBySid.remove(callSid);
            if (ctx != null) {
                String conversationLog = conversationLogger != null
                        ? conversationLogger.getFormattedConversationLogByCallSid(callSid)
                        : "Conversation log service not available.";

                salesforceService.createCallTaskForContactAndAccount(
                                ctx.contactId,
                                ctx.accountId,
                                ctx.phone,
                                callStatus,
                                callSid,
                                conversationLog
                        )
                        .subscribe(result -> {
                            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TwilioVoiceController.class);
                            if (result.isSuccess()) {
                                logger.info("Successfully created Salesforce Call Task {} for CallSid={} with status={}",
                                        result.getId(), callSid, callStatus);
                            } else {
                                logger.warn("Failed to create Salesforce Call Task for CallSid={} (status={}): {}",
                                        callSid, callStatus, result.getMessage());
                            }
                        }, error -> {
                            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TwilioVoiceController.class);
                            logger.error("Error while creating Salesforce Call Task for CallSid={} (status={})",
                                    callSid, callStatus, error);
                        });
            }
        }
    }
}


