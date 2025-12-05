package com.example.twilio.service;

import com.twilio.rest.api.v2010.account.Call;
import com.twilio.twiml.TwiMLException;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Say;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for injecting TwiML into active Twilio calls
 * This allows us to play AI responses to the caller in real-time
 */
@Service
public class TwilioTwiMLInjectionService {

    private static final Logger logger = LoggerFactory.getLogger(TwilioTwiMLInjectionService.class);

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.callback.base.url:}")
    private String callbackBaseUrl;

    /**
     * Injects TwiML with Say verb into an active call
     * This will interrupt the current call flow and play the message
     * 
     * @param callSid The Call SID of the active call
     * @param message The text message to speak
     * @return true if successful, false otherwise
     */
    public boolean injectSayIntoCall(String callSid, String message) {
        try {
            if (callSid == null || callSid.isEmpty()) {
                logger.warn("Cannot inject TwiML: Call SID is null or empty");
                return false;
            }

            if (message == null || message.trim().isEmpty()) {
                logger.warn("Cannot inject TwiML: Message is null or empty");
                return false;
            }

            // Create TwiML with Say verb
            Say say = new Say.Builder(message)
                    .voice(Say.Voice.ALICE)
                    .build();

            VoiceResponse response = new VoiceResponse.Builder()
                    .say(say)
                    .build();

            String twiml = response.toXml();
            logger.info("Injecting TwiML into call {}: {}", callSid, twiml);

            // Update the call with new TwiML
            Call.updater(callSid)
                    .setTwiml(twiml)
                    .update();

            logger.info("Successfully injected TwiML into call {}", callSid);
            return true;

        } catch (TwiMLException e) {
            logger.error("Error creating TwiML for call {}", callSid, e);
            return false;
        } catch (Exception e) {
            logger.error("Error injecting TwiML into call {}", callSid, e);
            return false;
        }
    }

    /**
     * Injects TwiML that says a message and then continues the stream
     * This maintains bidirectional conversation flow
     * 
     * @param callSid The Call SID
     * @param message The message to speak
     * @param streamUrl The WebSocket stream URL to continue after speaking
     */
    public boolean injectSayAndContinueStream(String callSid, String message, String streamUrl) {
        try {
            if (callSid == null || callSid.isEmpty()) {
                logger.warn("Cannot inject TwiML: Call SID is null or empty");
                return false;
            }

            // Create TwiML that says the message and continues streaming
            // The stream will continue to receive audio from the caller
            // Important: We restart the stream to ensure bidirectional communication continues
            String twiml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<Response>\n" +
                    "    <Stop>\n" +
                    "        <Stream />\n" +
                    "    </Stop>\n" +
                    "    <Say voice=\"alice\">" + escapeXml(message) + "</Say>\n" +
                    "    <Pause length=\"1\" />\n" +
                    "    <Start>\n" +
                    "        <Stream url=\"" + escapeXml(streamUrl) + "\" />\n" +
                    "    </Start>\n" +
                    "    <Pause length=\"60\" />\n" +
                    "</Response>";

            logger.info("Injecting TwiML with Say and Stream into call {}: {}", callSid, message);

            Call.updater(callSid)
                    .setTwiml(twiml)
                    .update();

            logger.info("Successfully injected TwiML into call {}. Stream will continue.", callSid);
            return true;

        } catch (Exception e) {
            logger.error("Error injecting TwiML into call {}", callSid, e);
            return false;
        }
    }

    /**
     * Plays a final message and hangs up the call safely.
     */
    public boolean injectSayAndHangup(String callSid, String message) {
        try {
            if (callSid == null || callSid.isEmpty()) {
                logger.warn("Cannot inject hangup TwiML: Call SID is null or empty");
                return false;
            }

            String safeMessage = (message != null && !message.trim().isEmpty())
                    ? message
                    : "Thank you for calling. Goodbye!";

            String twiml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<Response>\n" +
                    "    <Stop>\n" +
                    "        <Stream />\n" +
                    "    </Stop>\n" +
                    "    <Say voice=\"alice\">" + escapeXml(safeMessage) + "</Say>\n" +
                    "    <Hangup />\n" +
                    "</Response>";

            logger.info("Injecting hangup TwiML into call {} with message: {}", callSid, safeMessage);

            Call.updater(callSid)
                    .setTwiml(twiml)
                    .update();

            logger.info("Successfully injected hangup TwiML into call {}", callSid);
            return true;
        } catch (Exception e) {
            logger.error("Error injecting hangup TwiML into call {}", callSid, e);
            return false;
        }
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
}

