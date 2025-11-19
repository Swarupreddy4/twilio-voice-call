package com.example.twilio.websocket;

import com.example.twilio.service.AiAgentService;
import com.example.twilio.service.TwilioTwiMLInjectionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class TwilioMediaStreamHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(TwilioMediaStreamHandler.class);
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    // Map to store Call SID for each session
    private final ConcurrentMap<String, String> sessionToCallSid = new ConcurrentHashMap<>();
    // Map to store stream URL for each session
    private final ConcurrentMap<String, String> sessionToStreamUrl = new ConcurrentHashMap<>();
    // Map to track last response time for each session (for debouncing)
    private final ConcurrentMap<String, AtomicLong> lastResponseTime = new ConcurrentHashMap<>();
    // Minimum time between responses (in milliseconds) - 3 seconds
    private static final long MIN_RESPONSE_INTERVAL_MS = 3000;
    
    // Audio buffers for each session - accumulate audio before processing
    private final ConcurrentMap<String, AudioBuffer> audioBuffers = new ConcurrentHashMap<>();
    // Silence detection timeout - wait for silence before processing (configurable)
    @Value("${conversation.silence.timeout.ms:1500}")
    private long silenceTimeoutMs;
    
    // Executor for periodic silence checking
    private final ScheduledExecutorService silenceChecker = Executors.newScheduledThreadPool(2);
    
    // Track if we're currently processing/responding for each session
    private final ConcurrentMap<String, Boolean> isProcessing = new ConcurrentHashMap<>();
    
    @Autowired
    private AiAgentService aiAgentService;
    
    @Autowired
    private TwilioTwiMLInjectionService twilioTwiMLInjectionService;
    
    @Autowired
    private AudioEnergyDetector audioEnergyDetector;
    
    @Value("${twilio.callback.base.url:}")
    private String callbackBaseUrl;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("WebSocket connection established: {}", session.getId());
        sessions.put(session.getId(), session);
        
        // Initialize audio buffer for this session
        audioBuffers.put(session.getId(), new AudioBuffer(silenceTimeoutMs));
        isProcessing.put(session.getId(), false);
        
        // Start silence checker for this session
        startSilenceChecker(session.getId());
    }
    
    /**
     * Starts a periodic task to check for silence and process buffered audio
     * Simple logic: If buffer has speech chunks AND no new speech for timeout period → process
     */
    private void startSilenceChecker(String sessionId) {
        silenceChecker.scheduleAtFixedRate(() -> {
            try {
                AudioBuffer buffer = audioBuffers.get(sessionId);
                if (buffer == null) {
                    return;
                }
                
                // Skip if already processing
                if (isProcessing.getOrDefault(sessionId, false)) {
                    return;
                }
                
                // Check if buffer has speech chunks
                if (buffer.isEmpty()) {
                    // Log occasionally to show we're waiting for speech
                    long timeSinceLastCheck = System.currentTimeMillis() % 5000; // Log every 5 seconds
                    if (timeSinceLastCheck < 500) {
                        logger.debug(">>> Waiting for user speech... Buffer is empty - Session: {}", sessionId);
                    }
                    return; // No speech captured yet
                }
                
                // Check time since last speech was captured
                long timeSinceLastSpeech = buffer.getTimeSinceLastAudio();
                int chunkCount = buffer.getChunkCount();
                long totalBytes = buffer.getTotalBytes();
                
                // Log buffer status periodically
                if (timeSinceLastSpeech % 1000 == 0 && timeSinceLastSpeech > 0) {
                    logger.info(">>> Buffer status: {} chunks, {} bytes, {}ms since last speech (need {}ms) - Session: {}", 
                               chunkCount, totalBytes, timeSinceLastSpeech, silenceTimeoutMs, sessionId);
                }
                
                // If silence timeout reached, process the buffered speech
                if (timeSinceLastSpeech >= silenceTimeoutMs) {
                    logger.info(">>> ===== SILENCE DETECTED - PROCESSING USER SPEECH ===== Session: {}, {}ms since last speech, {} chunks, {} bytes", 
                               sessionId, timeSinceLastSpeech, chunkCount, totalBytes);
                    
                    // Process the buffered speech
                    processBufferedAudio(sessionId);
                } else {
                    // Log progress every 500ms
                    if (timeSinceLastSpeech % 500 == 0 && timeSinceLastSpeech > 0) {
                        int progress = (int) ((timeSinceLastSpeech * 100) / silenceTimeoutMs);
                        logger.info(">>> Waiting for silence... {}ms / {}ms ({}%) - {} chunks buffered - Session: {}", 
                                   timeSinceLastSpeech, silenceTimeoutMs, progress, chunkCount, sessionId);
                    }
                }
            } catch (Exception e) {
                logger.error("Error in silence checker for session {}", sessionId, e);
            }
        }, 500, 500, TimeUnit.MILLISECONDS); // Check every 500ms
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String payload = message.getPayload();
            JsonNode jsonNode = objectMapper.readTree(payload);
            
            String event = jsonNode.has("event") ? jsonNode.get("event").asText() : "";
            
            if (event.isEmpty()) {
                logger.warn("WebSocket message received without 'event' field for session {}: {}", 
                           session.getId(), payload.substring(0, Math.min(200, payload.length())));
                return;
            }
            
            // Only log non-media events (media events are too frequent)
            if (!event.equals("media")) {
                logger.info("Processing WebSocket event '{}' for session {}", event, session.getId());
            }
            
            switch (event) {
                case "connected":
                    handleConnectedEvent(session, jsonNode);
                    break;
                case "start":
                    handleStartEvent(session, jsonNode);
                    break;
                case "media":
                    handleMediaEvent(session, jsonNode);
                    break;
                case "stop":
                    handleStopEvent(session, jsonNode);
                    break;
                default:
                    logger.info("Unknown event type '{}' received for session {}", event, session.getId());
            }
        } catch (Exception e) {
            logger.error("Error handling WebSocket message for session {}", session.getId(), e);
            logger.error("Message payload: {}", message.getPayload().substring(0, Math.min(500, message.getPayload().length())));
        }
    }

    private void handleConnectedEvent(WebSocketSession session, JsonNode jsonNode) {
        logger.info(">>> Connected event received for session: {}", session.getId());
        if (jsonNode.has("protocol")) {
            logger.info(">>> WebSocket protocol: {}", jsonNode.get("protocol").asText());
        }
    }

    private void handleStartEvent(WebSocketSession session, JsonNode jsonNode) {
        logger.info("Start event received for session: {}", session.getId());
        if (jsonNode.has("start")) {
            JsonNode startNode = jsonNode.get("start");
            String callSid = startNode.has("callSid") ? startNode.get("callSid").asText() : null;
            String streamSid = startNode.has("streamSid") ? startNode.get("streamSid").asText() : null;
            
            if (callSid != null && !callSid.equals("unknown")) {
                sessionToCallSid.put(session.getId(), callSid);
                logger.info("Stored Call SID {} for session {}", callSid, session.getId());
                logger.info(">>> Conversation started - Call SID: {}, Session: {}", callSid, session.getId());
            }
            
            if (streamSid != null) {
                // Build stream URL for this session
                String streamUrl = buildStreamUrl();
                sessionToStreamUrl.put(session.getId(), streamUrl);
                logger.info("Stored Stream URL for session {}", session.getId());
            }
        }
    }
    
    private String buildStreamUrl() {
        if (callbackBaseUrl != null && !callbackBaseUrl.trim().isEmpty()) {
            String baseUrl = callbackBaseUrl.trim();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            return baseUrl.replace("http://", "wss://")
                        .replace("https://", "wss://") + "/twilio/media-stream";
        }
        return "wss://synodically-spongioblastic-guadalupe.ngrok-free.dev/twilio/media-stream";
    }

    private void handleMediaEvent(WebSocketSession session, JsonNode jsonNode) {
        if (jsonNode.has("media")) {
            JsonNode mediaNode = jsonNode.get("media");
            String payload = mediaNode.has("payload") ? mediaNode.get("payload").asText() : "";
            
            if (!payload.isEmpty()) {
                try {
                    // Decode base64 audio payload
                    byte[] audioData = Base64.getDecoder().decode(payload);
                    
                    // Check if we're currently processing (AI is responding)
                    boolean currentlyProcessing = isProcessing.getOrDefault(session.getId(), false);
                    
                    // Skip processing if AI is currently responding
                    if (currentlyProcessing) {
                        return; // Ignore audio while AI is speaking
                    }
                    
                    // Detect if this chunk has actual speech (not silence/noise)
                    double energy = audioEnergyDetector.calculateEnergy(audioData);
                    double nonSilencePercent = audioEnergyDetector.getNonSilencePercentage(audioData);
                    boolean hasEnergy = audioEnergyDetector.hasAudioEnergy(audioData);
                    
                    // Get buffer for this session
                    AudioBuffer buffer = audioBuffers.get(session.getId());
                    if (buffer == null) {
                        logger.error(">>> CRITICAL: No audio buffer found for session {} - audio chunk dropped!", session.getId());
                        return;
                    }
                    
                    // Track chunk count for diagnostic logging
                    int currentChunkCount = buffer.getChunkCount();
                    
                    // ONLY buffer chunks that contain actual speech
                    if (hasEnergy) {
                        // Additional check: If energy is suspiciously constant (like 127.00), it might be noise/feedback
                        // Real speech has varying energy levels, not constant values
                        boolean suspiciousConstantEnergy = Math.abs(energy - 127.0) < 1.0 && nonSilencePercent >= 99.0;
                        
                        if (suspiciousConstantEnergy) {
                            // This looks like constant noise/feedback, not real speech
                            // Only log this warning occasionally to avoid spam
                            if (currentChunkCount % 500 == 0) {
                                logger.warn(">>> SUSPICIOUS: Constant energy detected (Energy: {}, NonSilence: {}%) - likely noise/feedback, NOT buffering - Session: {}", 
                                           String.format("%.2f", energy), String.format("%.1f", nonSilencePercent), session.getId());
                            }
                            return; // Don't buffer constant noise
                        }
                        
                        // This is real speech - add to buffer and update timestamp
                        buffer.addChunk(audioData, true);
                        
                        // Log user speech detection prominently (but not every chunk to avoid spam)
                        if (currentChunkCount % 10 == 0 || currentChunkCount < 10) {
                            logger.info(">>> ===== USER SPEECH CAPTURED ===== Session: {}, Energy: {}, NonSilence: {}%, Chunks: {}, Total: {} bytes", 
                                       session.getId(), String.format("%.2f", energy), 
                                       String.format("%.1f", nonSilencePercent), 
                                       buffer.getChunkCount(), buffer.getTotalBytes());
                        }
                    } else {
                        // Log when chunks are close to threshold to help diagnose why speech isn't detected
                        if (energy > 80.0 && energy < audioEnergyDetector.getMinEnergyThreshold()) {
                            // Close but below threshold - log occasionally
                            if (currentChunkCount % 100 == 0) {
                                logger.info(">>> Audio close to threshold but rejected - Energy: {} (need >{}), NonSilence: {}% (need >{}%) - Session: {}", 
                                           String.format("%.2f", energy),
                                           String.format("%.2f", audioEnergyDetector.getMinEnergyThreshold()),
                                           String.format("%.1f", nonSilencePercent),
                                           String.format("%.1f", audioEnergyDetector.getMinNonSilencePercent()),
                                           session.getId());
                            }
                        }
                    }
                    
                    // Log energy analysis when speech is detected or occasionally for diagnostics
                    if (hasEnergy) {
                        logger.info(">>> Audio Analysis [Session: {}] - Energy: {}, NonSilence: {}%, HasEnergy: true, Threshold: {}, MinPercent: {}%", 
                                   session.getId(), String.format("%.2f", energy), 
                                   String.format("%.1f", nonSilencePercent),
                                   String.format("%.2f", audioEnergyDetector.getMinEnergyThreshold()),
                                   String.format("%.1f", audioEnergyDetector.getMinNonSilencePercent()));
                    } else if (currentChunkCount % 500 == 0) {
                        logger.debug(">>> Audio Analysis [Session: {}] - Energy: {}, NonSilence: {}%, HasEnergy: false (filtered) - Threshold: {}, MinPercent: {}%", 
                                   session.getId(), String.format("%.2f", energy), 
                                   String.format("%.1f", nonSilencePercent),
                                   String.format("%.2f", audioEnergyDetector.getMinEnergyThreshold()),
                                   String.format("%.1f", audioEnergyDetector.getMinNonSilencePercent()));
                    }
                    // Silence/noise chunks are completely ignored - not added to buffer
                    
                } catch (IllegalArgumentException e) {
                    logger.error(">>> Failed to decode base64 audio payload for session {}: {}", 
                               session.getId(), e.getMessage());
                } catch (Exception e) {
                    logger.error(">>> Error processing media event for session {}", session.getId(), e);
                }
            }
        }
    }
    
    /**
     * Processes buffered audio when silence is detected
     * Simple flow: Get buffered speech → Process → Generate response → Send → Reset
     */
    private void processBufferedAudio(String sessionId) {
        AudioBuffer buffer = audioBuffers.get(sessionId);
        if (buffer == null || buffer.isEmpty()) {
            logger.warn(">>> Cannot process: Buffer is NULL or EMPTY for session {}", sessionId);
            return;
        }
        
        // Mark as processing to prevent new processing
        if (isProcessing.put(sessionId, true)) {
            logger.warn(">>> Already processing for session {} - skipping", sessionId);
            return;
        }
        
        logger.info(">>> Processing user speech for session {}", sessionId);
        
        try {
            // Get all buffered speech (this clears the buffer)
            byte[] bufferedAudio = buffer.getBufferedAudio();
            
            if (bufferedAudio == null || bufferedAudio.length == 0) {
                logger.warn(">>> No audio data to process for session {}", sessionId);
                isProcessing.put(sessionId, false);
                return;
            }
            
            logger.info(">>> Processing {} bytes of user speech for session {}", bufferedAudio.length, sessionId);
            
            // Process audio with AI agent (transcribes and generates response)
            String response = aiAgentService.processAudio(bufferedAudio, sessionId);
            
            // Send AI response
            if (response != null && !response.isEmpty()) {
                logger.info(">>> Sending AI response for session {}", sessionId);
                sendAiResponse(sessionId, response);
                
                // Reset processing flag after delay to allow AI to speak
                silenceChecker.schedule(() -> {
                    isProcessing.put(sessionId, false);
                    logger.info(">>> Ready for next user speech - Session: {}", sessionId);
                }, 3, TimeUnit.SECONDS);
            } else {
                logger.warn(">>> No AI response generated for session {}", sessionId);
                isProcessing.put(sessionId, false);
            }
        } catch (Exception e) {
            logger.error(">>> Error processing audio for session {}", sessionId, e);
            isProcessing.put(sessionId, false);
        }
    }
    
    /**
     * Sends AI response to the caller by injecting TwiML into the active call
     * Includes debouncing to prevent too many rapid responses
     */
    public void sendAiResponse(String sessionId, String textResponse) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null) {
            logger.warn("Session not found for session ID: {}", sessionId);
            return;
        }
        
        String callSid = sessionToCallSid.get(sessionId);
        String streamUrl = sessionToStreamUrl.get(sessionId);
        
        if (callSid == null || callSid.isEmpty()) {
            logger.warn("Cannot send AI response: Call SID not found for session {}", sessionId);
            return;
        }
        
        // Debouncing: Check if enough time has passed since last response
        AtomicLong lastTime = lastResponseTime.computeIfAbsent(sessionId, k -> new AtomicLong(0));
        long currentTime = System.currentTimeMillis();
        long timeSinceLastResponse = currentTime - lastTime.get();
        
        if (timeSinceLastResponse < MIN_RESPONSE_INTERVAL_MS) {
            logger.debug("Skipping response - too soon since last response ({} ms ago)", timeSinceLastResponse);
            return;
        }
        
        // Update last response time
        lastTime.set(currentTime);
        
        String timestamp = java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        logger.info("[{}] Sending AI response to call {}: {}", timestamp, callSid, textResponse);
        
        // Always inject Say and continue streaming to maintain bidirectional conversation
        boolean success;
        if (streamUrl != null && !streamUrl.isEmpty()) {
            logger.info(">>> Injecting TwiML with Say and Stream continuation for call {}", callSid);
            logger.info(">>> Stream URL: {}", streamUrl);
            success = twilioTwiMLInjectionService.injectSayAndContinueStream(callSid, textResponse, streamUrl);
        } else {
            logger.warn(">>> No stream URL available - using fallback (Say only, no stream continuation)");
            // Fallback: just say the message
            success = twilioTwiMLInjectionService.injectSayIntoCall(callSid, textResponse);
        }
        
        if (!success) {
            logger.error(">>> FAILED to inject TwiML response into call {}", callSid);
        } else {
            logger.info(">>> Successfully sent AI response. Stream will continue after speaking.");
            logger.info(">>> Waiting for user to speak again...");
        }
    }

    private void handleStopEvent(WebSocketSession session, JsonNode jsonNode) {
        logger.info("Stop event received for session: {}", session.getId());
        cleanupSession(session.getId());
    }
    
    /**
     * Cleans up resources for a session
     */
    private void cleanupSession(String sessionId) {
        sessions.remove(sessionId);
        sessionToCallSid.remove(sessionId);
        sessionToStreamUrl.remove(sessionId);
        lastResponseTime.remove(sessionId);
        audioBuffers.remove(sessionId);
        isProcessing.remove(sessionId);
    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        logger.info("WebSocket connection closed: {} with status: {}", session.getId(), status);
        cleanupSession(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket transport error for session: {}", session.getId(), exception);
        cleanupSession(session.getId());
    }
}


