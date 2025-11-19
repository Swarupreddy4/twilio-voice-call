package com.example.twilio.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * AI Agent Service for processing audio and generating responses
 * This is a placeholder implementation that can be extended with actual AI services
 * like OpenAI Whisper for speech-to-text and GPT for generating responses
 */
@Service
public class AiAgentService {

    private static final Logger logger = LoggerFactory.getLogger(AiAgentService.class);

    @Value("${ai.agent.enabled:true}")
    private boolean aiAgentEnabled;
    
   // @Value("${ai.agent.test.mode:false}")
    private boolean testMode=true;
    
    @Autowired(required = false)
    private ConversationLogger conversationLogger;

    /**
     * Process incoming audio data from Twilio Media Stream
     * 
     * @param audioData Raw audio bytes (mu-law encoded)
     * @param sessionId WebSocket session ID
     * @return Text response to be converted to speech
     */
    public String processAudio(byte[] audioData, String sessionId) {
        //if (!aiAgentEnabled) {
         //   return null;
       // }

        try {
            // Convert mu-law audio to PCM (if needed)
            // Twilio sends audio in mu-law format (8-bit, 8000 Hz)
            byte[] pcmAudio = convertMuLawToPCM(audioData);

            // TODO: Integrate with speech-to-text service (e.g., OpenAI Whisper, Google Speech-to-Text)
            String transcribedText = transcribeAudio(pcmAudio);
System.out.println(transcribedText);
            if (transcribedText != null && !transcribedText.trim().isEmpty()) {
                // Log user speech prominently with timestamp
                String timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                logger.info("=".repeat(80));
                logger.info("[{}] USER SPEECH [Session: {}]: {}", timestamp, sessionId, transcribedText);
                logger.info("=".repeat(80));
                
                // Log to conversation logger if available
                if (conversationLogger != null) {
                    conversationLogger.logConversation(sessionId, sessionId, "USER", transcribedText);
                }

                // TODO: Integrate with AI service (e.g., OpenAI GPT, Anthropic Claude)
                String aiResponse = generateAiResponse(transcribedText, sessionId);

                // Log AI response prominently with timestamp
                if (aiResponse != null && !aiResponse.trim().isEmpty()) {
                    timestamp = java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    logger.info("-".repeat(80));
                    logger.info("[{}] AI RESPONSE [Session: {}]: {}", timestamp, sessionId, aiResponse);
                    logger.info("-".repeat(80));
                    
                    // Log to conversation logger if available
                    if (conversationLogger != null) {
                        conversationLogger.logConversation(sessionId, sessionId, "AI", aiResponse);
                    }
                    
                    return aiResponse;
                }
            } else if (testMode) {
                // Test mode: Generate a response even without transcription
                // This allows testing the response playback mechanism
                String testUserText = "[Audio received - " + audioData.length + " bytes, no transcription available]";
                String timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                logger.info("=".repeat(80));
                logger.info("[{}] USER SPEECH [Session: {}]: {}", timestamp, sessionId, testUserText);
                logger.info("=".repeat(80));
                
                // Log to conversation logger if available
                if (conversationLogger != null) {
                    conversationLogger.logConversation(sessionId, sessionId, "USER", testUserText);
                }
                
                String testResponse = "I received your audio. This is a test response. Please integrate speech-to-text to enable real transcription.";
                
                timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                logger.info("-".repeat(80));
                logger.info("[{}] AI RESPONSE [Session: {}]: {}", timestamp, sessionId, testResponse);
                logger.info("-".repeat(80));
                
                // Log to conversation logger if available
                if (conversationLogger != null) {
                    conversationLogger.logConversation(sessionId, sessionId, "AI", testResponse);
                }
                
                return testResponse;
            }

        } catch (Exception e) {
            logger.error("Error processing audio for session: " + sessionId, e);
        }

        return null;
    }

    /**
     * Convert mu-law encoded audio to PCM format
     */
    private byte[] convertMuLawToPCM(byte[] muLawData) {
        // Mu-law to PCM conversion
        // This is a simplified conversion - in production, use a proper audio library
        short[] pcmData = new short[muLawData.length];
        for (int i = 0; i < muLawData.length; i++) {
            pcmData[i] = muLawToLinear(muLawData[i] & 0xFF);
        }
        
        // Convert to byte array (little-endian)
        ByteBuffer buffer = ByteBuffer.allocate(pcmData.length * 2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : pcmData) {
            buffer.putShort(sample);
        }
        return buffer.array();
    }

    /**
     * Convert mu-law sample to linear PCM
     */
    private short muLawToLinear(int muLawByte) {
        muLawByte = ~muLawByte;
        int sign = muLawByte & 0x80;
        int exponent = (muLawByte >> 4) & 0x07;
        int mantissa = muLawByte & 0x0F;
        
        int sample = mantissa << (exponent + 3);
        if (sign != 0) {
            sample = -sample;
        }
        return (short) sample;
    }

    /**
     * Transcribe audio to text using speech-to-text service
     * TODO: Integrate with actual STT service
     * 
     * For now, this is a placeholder that returns test text when audio is detected
     * Replace this with actual STT integration
     */
    private String transcribeAudio(byte[] pcmAudio) {
        // Placeholder implementation
        // In production, integrate with:
        // - OpenAI Whisper API
        // - Google Cloud Speech-to-Text
        // - AWS Transcribe
        // - Azure Speech Services
        
        // For testing: return a test transcription if audio data is present
        // In production, remove this and implement actual STT
        if (pcmAudio != null && pcmAudio.length > 0) {
            // This is a placeholder - replace with actual STT
            logger.debug("Audio received ({} bytes) - STT integration needed", pcmAudio.length);
            
            // For testing purposes, you can uncomment the line below to see logging in action
            // This will simulate a transcription for testing the logging system
            // return "Hello, this is a test transcription of user speech";
            
            return "Hello User"; // Return null until you implement actual STT
        }
        
        return "Hello user";
    }

    /**
     * Generate AI response based on transcribed text
     * TODO: Integrate with actual AI service
     */
    private String generateAiResponse(String userInput, String sessionId) {
        // Placeholder implementation
        // In production, integrate with:
        // - OpenAI GPT-4
        // - Anthropic Claude
        // - Google Gemini
        // - Custom LLM
        
        logger.debug("Generating AI response for input: {}", userInput);
        
        // Simple echo response for demonstration
        // In production, replace this with actual AI service call
        String response = "I heard you say: " + userInput + ". This is a placeholder response.";
        
        return response;
    }
}


