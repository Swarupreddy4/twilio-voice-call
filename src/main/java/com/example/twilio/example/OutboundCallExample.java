package com.example.twilio.example;

import com.example.twilio.dto.OutboundCallRequest;
import com.example.twilio.dto.OutboundCallResponse;
import com.example.twilio.service.OutboundCallService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Example usage of outbound call functionality
 * This demonstrates how to make outbound calls programmatically
 */
@RestController
@RequestMapping("/example")
public class OutboundCallExample {

    @Autowired
    private OutboundCallService outboundCallService;

    /**
     * Example: Make an outbound call
     * 
     * POST /example/make-call
     * {
     *   "toNumber": "+1234567890",
     *   "fromNumber": "+18026590229",
     *   "customMessage": "Hello! This is a test call from our AI agent."
     * }
     */
    @PostMapping("/make-call")
    public ResponseEntity<OutboundCallResponse> exampleMakeCall(@RequestBody OutboundCallRequest request) {
        OutboundCallResponse response = outboundCallService.makeOutboundCall(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Example: Make multiple outbound calls
     * 
     * This could be used for:
     * - Automated appointment reminders
     * - Emergency notifications
     * - Marketing campaigns
     * - Customer service follow-ups
     */
    public void makeMultipleCalls(String[] phoneNumbers, String message) {
        for (String phoneNumber : phoneNumbers) {
            OutboundCallRequest request = new OutboundCallRequest(
                    phoneNumber,
                    null, // Use default from number
                    message
            );
            
            OutboundCallResponse response = outboundCallService.makeOutboundCall(request);
            System.out.println("Call to " + phoneNumber + ": " + response.getCallSid());
        }
    }
}

