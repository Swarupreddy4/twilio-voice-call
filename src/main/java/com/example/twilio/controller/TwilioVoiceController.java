package com.example.twilio.controller;

import com.example.twilio.dto.OutboundCallRequest;
import com.example.twilio.dto.OutboundCallResponse;
import com.example.twilio.service.OutboundCallService;
import com.example.twilio.service.TwilioVoiceService;
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
        OutboundCallResponse response = outboundCallService.makeOutboundCall(request);
        
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
    }
}


