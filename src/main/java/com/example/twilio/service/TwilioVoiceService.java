package com.example.twilio.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TwilioVoiceService {

    @Value("${twilio.phone.number}")
    private String twilioPhoneNumber;

    /**
     * Generates TwiML to start a Media Stream WebSocket connection
     * 
     * @param baseUrl Base URL of the request
     * @param customMessage Optional custom greeting message
     */
    public String generateVoiceTwiML(String baseUrl, String customMessage) {
        // Convert HTTP URL to WebSocket URL
		/*
		 * String wsUrl = baseUrl.replace("http://", "wss://") .replace("https://",
		 * "wss://") .replace("/twilio/voice", "/twilio/media-stream");
		 */
    	String wsUrl = "wss://synodically-spongioblastic-guadalupe.ngrok-free.dev/twilio/media-stream";
        // Use custom message if provided, otherwise use default
        String greetingMessage = (customMessage != null && !customMessage.trim().isEmpty())
                ? customMessage
                : "Hello! I'm your AI assistant. How can I help you today?";
        
        // TwiML with Stream configured to receive audio from caller
        // The Stream verb will send audio data to the WebSocket URL
        // Important: Stream must be started BEFORE Say to capture user audio during greeting
        // The Pause allows time for user to respond after greeting
        String twiml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Response>\n" +
                "    <Start>\n" +
                "        <Stream url=\"" + wsUrl + "\" />\n" +
                "    </Start>\n" +
                "    <Say voice=\"alice\">" + escapeXml(greetingMessage) + "</Say>\n" +
                "    <Pause length=\"60\" />\n" +
                "</Response>";
        
        // Log the TwiML being generated (for debugging)
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TwilioVoiceService.class);
        logger.info(">>> Generated TwiML for voice call with WebSocket stream");
        logger.debug("TwiML: {}", twiml);
        
        return twiml;
    }

    /**
     * Generates TwiML to start a Media Stream WebSocket connection (default message)
     */
    public String generateVoiceTwiML(String baseUrl) {
        return generateVoiceTwiML(baseUrl, null);
    }

    /**
     * Escapes XML special characters
     */
    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&apos;");
    }

    /**
     * Handles call status updates from Twilio
     */
    public void handleCallStatus(String callSid, String callStatus) {
        System.out.println("Call SID: " + callSid + ", Status: " + callStatus);
        // Add your call status handling logic here
    }

    /**
     * Extracts host from URL for WebSocket connection
     * In production, you should use your actual domain or ngrok URL
     */
    private String extractHost(String url) {
        // Remove http:// or https://
        url = url.replace("http://", "").replace("https://", "");
        // Remove path if present
        int pathIndex = url.indexOf("/");
        if (pathIndex > 0) {
            url = url.substring(0, pathIndex);
        }
        return url;
    }
}

