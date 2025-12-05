package com.example.twilio.service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognizeRequest;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.protobuf.ByteString;
import com.example.twilio.service.dto.AiAgentResult;

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
    
    @Autowired(required = false)
    private VoskSpeechToTextService voskService;
    
    // Google Cloud Speech-to-Text configuration
    @Value("${google.cloud.speech.enabled:true}")
    private boolean googleCloudSpeechEnabled;
    
    @Value("${google.cloud.speech.language.code:en-US}")
    private String languageCode;
    
    @Value("${google.cloud.speech.sample.rate.hertz:8000}")
    private int sampleRateHertz;
    
    @Value("${google.cloud.speech.enable.automatic.punctuation:true}")
    private boolean enableAutomaticPunctuation;
    
    @Value("${google.cloud.speech.enable.speaker.diarization:false}")
    private boolean enableSpeakerDiarization;
    
    @Value("${google.cloud.speech.max.alternatives:1}")
    private int maxAlternatives;
    
    @Value("${google.cloud.speech.enable.word.confidence:false}")
    private boolean enableWordConfidence;
    
    @Value("${google.cloud.speech.use.enhanced.model:false}")
    private boolean useEnhancedModel;
    
    // Optional: Path to Google Cloud service account JSON file
    // If not set, will use Application Default Credentials (GOOGLE_APPLICATION_CREDENTIALS env var)
    @Value("${google.cloud.speech.credentials.path:}")
    private String credentialsPath;
    /**
     * Process incoming audio data from Twilio Media Stream
     *
     * @param audioData Raw audio bytes (mu-law encoded)
     * @param sessionId WebSocket session ID
     * @param callSid   Twilio Call SID (for logging / Salesforce Task linkage)
     * @return Text response to be converted to speech
     */
    public AiAgentResult processAudio(byte[] audioData, String sessionId, String callSid) {
        //if (!aiAgentEnabled) {
         //   return null;
       // }

        try {
            // Convert mu-law audio to PCM (if needed)
            // Twilio sends audio in mu-law format (8-bit, 8000 Hz)
            byte[] pcmAudio = convertMuLawToPCM(audioData);

            // Transcribe audio to text using available STT service
            String transcribedText = transcribeAudio(pcmAudio);
            
            if (transcribedText != null && !transcribedText.trim().isEmpty()) {
                // Log user speech prominently with timestamp
                String timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                logger.info("=".repeat(80));
                logger.info("[{}] USER SPEECH [Session: {}]: {}", timestamp, sessionId, transcribedText);
                logger.info("=".repeat(80));
                
                // Log to conversation logger if available
                if (conversationLogger != null) {
                    conversationLogger.logConversation(sessionId,
                            callSid != null ? callSid : sessionId,
                            "USER",
                            transcribedText);
                }

                // Check if user wants to end the call
                if (shouldEndCall(transcribedText)) {
                    String farewellResponse = "Thank you for calling. Goodbye!";
                    
                    String farewellTimestamp = java.time.LocalDateTime.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    logger.info("-".repeat(80));
                    logger.info("[{}] AI RESPONSE [Session: {}]: {}", farewellTimestamp, sessionId, farewellResponse);
                    logger.info("-".repeat(80));
                    
                    if (conversationLogger != null) {
                        conversationLogger.logConversation(sessionId,
                                callSid != null ? callSid : sessionId,
                                "AI",
                                farewellResponse);
                    }
                    
                    return new AiAgentResult(farewellResponse, true);
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
                        conversationLogger.logConversation(sessionId,
                                callSid != null ? callSid : sessionId,
                                "AI",
                                aiResponse);
                    }
                    
                    return new AiAgentResult(aiResponse, false);
                }
            } else if (testMode) {/*
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
                    conversationLogger.logConversation(sessionId,
                            callSid != null ? callSid : sessionId,
                            "USER",
                            testUserText);
                }
                
                String testResponse = "I received your audio. This is a test response. Please integrate speech-to-text to enable real transcription.";
                
                timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                logger.info("-".repeat(80));
                logger.info("[{}] AI RESPONSE [Session: {}]: {}", timestamp, sessionId, testResponse);
                logger.info("-".repeat(80));
                
                // Log to conversation logger if available
                if (conversationLogger != null) {
                    conversationLogger.logConversation(sessionId,
                            callSid != null ? callSid : sessionId,
                            "AI",
                            testResponse);
                }
                
                return new AiAgentResult(testResponse, false);
            */}

        } catch (Exception e) {
            logger.error("Error processing audio for session: " + sessionId, e);
        }

        return null;
    }

    /**
     * Convert mu-law encoded audio to PCM format
     * Also applies normalization/amplification if audio is too quiet
     */
    private byte[] convertMuLawToPCM(byte[] muLawData) {
        if (muLawData == null || muLawData.length == 0) {
            return new byte[0];
        }
        
        // Mu-law to PCM conversion
        short[] pcmData = new short[muLawData.length];
        for (int i = 0; i < muLawData.length; i++) {
            pcmData[i] = muLawToLinear(muLawData[i] & 0xFF);
        }
        
        // Find maximum amplitude for normalization
        int maxAmplitude = 0;
        for (short sample : pcmData) {
            int abs = Math.abs(sample);
            if (abs > maxAmplitude) {
                maxAmplitude = abs;
            }
        }
        
        // Normalize/amplify if audio is too quiet (max amplitude < 1000)
        // This helps Google Cloud Speech-to-Text recognize quiet audio
        if (maxAmplitude > 0 && maxAmplitude < 1000) {
            double amplificationFactor = 1000.0 / maxAmplitude;
            // Limit amplification to avoid distortion (max 10x)
            if (amplificationFactor > 10.0) {
                amplificationFactor = 10.0;
            }
            
            logger.debug("Audio is quiet (max amplitude: {}), applying {}x amplification", 
                        maxAmplitude, String.format("%.2f", amplificationFactor));
            
            for (int i = 0; i < pcmData.length; i++) {
                int amplified = (int) (pcmData[i] * amplificationFactor);
                // Clamp to 16-bit range
                if (amplified > 32767) amplified = 32767;
                if (amplified < -32768) amplified = -32768;
                pcmData[i] = (short) amplified;
            }
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
     * Standard ITU-T G.711 mu-law algorithm
     * Formula: linear = sign * (((mantissa << 1) + 33) << exponent - 33)
     */
    private short muLawToLinear(int muLawByte) {
        // Complement all bits (invert)
        muLawByte = ~muLawByte;
        
        // Extract sign bit (bit 7) - 0 for positive, 1 for negative
        int sign = (muLawByte & 0x80) >> 7;
        
        // Extract exponent (bits 4-6)
        int exponent = (muLawByte >> 4) & 0x07;
        
        // Extract mantissa (bits 0-3)
        int mantissa = muLawByte & 0x0F;
        
        // Calculate linear value using ITU-T G.711 formula
        // Bias = 33 (0x21)
        int bias = 33;
        int sample = ((mantissa << 1) + bias) << exponent;
        sample = sample - bias;
        
        // Apply sign: if sign bit is 1, make negative
        if (sign == 1) {
            sample = -sample;
        }
        
        // Clamp to 16-bit signed range
        if (sample > 32767) sample = 32767;
        if (sample < -32768) sample = -32768;
        
        return (short) sample;
    }
    
    /**
     * Analyze audio content to check if it contains actual speech
     */
    private void analyzeAudioContent(byte[] pcmAudio) {
        if (pcmAudio == null || pcmAudio.length < 4) {
            return;
        }
        
        // Convert bytes to 16-bit samples (little-endian)
        int sampleCount = pcmAudio.length / 2;
        long sum = 0;
        long sumSquares = 0;
        int zeroCount = 0;
        int maxSample = 0;
        int minSample = 0;
        
        ByteBuffer buffer = ByteBuffer.wrap(pcmAudio);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        for (int i = 0; i < sampleCount && i < 1000; i++) { // Analyze first 1000 samples
            short sample = buffer.getShort();
            int absSample = Math.abs(sample);
            
            sum += absSample;
            sumSquares += (long) absSample * absSample;
            
            if (absSample == 0) {
                zeroCount++;
            }
            
            if (absSample > maxSample) {
                maxSample = absSample;
            }
            if (absSample < minSample || i == 0) {
                minSample = absSample;
            }
        }
        
        double avgAmplitude = (double) sum / Math.min(sampleCount, 1000);
        double variance = ((double) sumSquares / Math.min(sampleCount, 1000)) - (avgAmplitude * avgAmplitude);
        double stdDev = Math.sqrt(variance);
        double zeroPercent = (double) zeroCount * 100.0 / Math.min(sampleCount, 1000);
        
        logger.info("Audio analysis: avg={}, stdDev={}, max={}, min={}, zeroPercent={}%, samples={}", 
                   String.format("%.2f", avgAmplitude),
                   String.format("%.2f", stdDev),
                   maxSample,
                   minSample,
                   String.format("%.2f", zeroPercent),
                   sampleCount);
        
        // Warn if audio looks like silence
        if (avgAmplitude < 100 && stdDev < 50) {
            logger.warn("Audio appears to be mostly silence (avg amplitude: {}, std dev: {})", 
                       String.format("%.2f", avgAmplitude), String.format("%.2f", stdDev));
        }
        
        // Warn if audio is all zeros
        if (zeroPercent > 95.0) {
            logger.warn("Audio appears to be all zeros ({}% zero samples) - conversion may have failed", 
                       String.format("%.2f", zeroPercent));
        }
    }
    
    /**
     * Transcribe audio to text using available speech-to-text services
     * Tries Vosk first (open source), falls back to Google Cloud Speech-to-Text
     */
    public String transcribeAudio(byte[] pcmAudio) {
        if (pcmAudio == null || pcmAudio.length == 0) {
            logger.warn("No audio data provided for transcription");
            return null;
        }
        
        // Try Vosk first if available
        if (voskService != null) {
            if (voskService.isReady()) {
                try {
                    logger.debug("Attempting Vosk transcription for {} bytes of audio", pcmAudio.length);
                    String transcription = voskService.transcribe(pcmAudio, sampleRateHertz);
                    if (transcription != null && !transcription.trim().isEmpty()) {
                        logger.info("Vosk transcription successful: {}", transcription);
                        return transcription;
                    } else {
                        logger.warn("Vosk returned null or empty transcription");
                    }
                } catch (Exception e) {
                    logger.error("Vosk transcription failed: {}", e.getMessage(), e);
                }
            } else {
                logger.debug("Vosk service not ready. Library available: {}", voskService.isLibraryAvailable());
            }
        } else {
            logger.debug("VoskSpeechToTextService bean not available - skipping Vosk transcription");
        }
        
        // Fallback to Google Cloud Speech-to-Text (if enabled and credentials are configured)
        if (googleCloudSpeechEnabled) {
            logger.debug("Attempting Google Cloud Speech-to-Text transcription");
            try {
                String transcription = transcribeWithGoogleCloud(pcmAudio);
                if (transcription != null && !transcription.trim().isEmpty()) {
                    logger.info("Google Cloud transcription successful: {}", transcription);
                    return transcription;
                } else {
                    logger.warn("Google Cloud returned null or empty transcription");
                }
            } catch (java.io.IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("default credentials were not found")) {
                    logger.warn("Google Cloud credentials not configured. To use Google Cloud Speech-to-Text:");
                    logger.warn("1. Set GOOGLE_APPLICATION_CREDENTIALS environment variable to your service account JSON file, OR");
                    logger.warn("2. Run 'gcloud auth application-default login' to set up Application Default Credentials");
                    logger.warn("3. Or set the credentials in application.properties using google.cloud.credentials.path");
                    logger.warn("Skipping Google Cloud - using Vosk only or test mode");
                } else {
                    logger.error("Google Cloud Speech-to-Text failed: {}", e.getMessage(), e);
                }
            } catch (Exception e) {
                logger.error("Google Cloud Speech-to-Text failed: {}", e.getMessage(), e);
            }
        } else {
            logger.debug("Google Cloud Speech-to-Text is disabled in configuration");
        }
        
        logger.warn("All speech-to-text services failed or returned empty result");
        return null;
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

    /**
     * Determines if the user intends to end the call
     */
    private boolean shouldEndCall(String transcribedText) {
        if (transcribedText == null) {
            return false;
        }
        
        String normalized = transcribedText.trim().toLowerCase();
        return normalized.contains("goodbye") ||
               normalized.contains("good bye") ||
               normalized.contains("bye bye") ||
               normalized.equals("bye") ||
               normalized.equals("bye.") ||
               normalized.equals("bye!") ||
               normalized.contains("that's all") ||
               normalized.contains("nothing else") ||
               normalized.contains("end the call");
    }
    /**
     * Transcribe audio using Google Cloud Speech-to-Text API
     * 
     * @param audioData PCM audio data (16-bit linear)
     * @return Transcribed text or null if transcription fails
     * @throws Exception if API call fails
     */
    private String transcribeWithGoogleCloud(byte[] audioData) throws Exception {
        if (audioData == null || audioData.length == 0) {
            logger.warn("Empty audio data provided to Google Cloud Speech-to-Text");
            return null;
        }
        
        // Validate audio data size and format
        // For 16-bit PCM at 8000 Hz: 2 bytes per sample, so 8000 samples/second = 16000 bytes/second
        // Minimum recommended: ~500ms = 8000 bytes
        int minRecommendedBytes = (sampleRateHertz * 2) / 2; // 0.5 seconds worth of audio
        double audioDurationSeconds = (double) audioData.length / (sampleRateHertz * 2);
        
        logger.info("Google Cloud Speech-to-Text: {} bytes of audio ({} seconds at {} Hz, {} samples)", 
                   audioData.length, 
                   String.format("%.2f", audioDurationSeconds),
                   sampleRateHertz,
                   audioData.length / 2);
        
        if (audioData.length < minRecommendedBytes) {
            logger.warn("Audio data is very short ({} bytes, {} seconds). Google Cloud may not return results for audio shorter than ~0.5 seconds.", 
                       audioData.length, 
                       String.format("%.2f", audioDurationSeconds));
        }
        
        // Check if audio data looks valid (should be even number of bytes for 16-bit PCM)
        if (audioData.length % 2 != 0) {
            logger.warn("Audio data length is not even ({} bytes). PCM 16-bit audio should have even byte count.", audioData.length);
        }
        
        // Analyze audio content to check if it contains actual audio (not just silence)
        analyzeAudioContent(audioData);
        
        // Create SpeechClient with explicit credentials if provided, otherwise use default
        SpeechClient speechClient;
        if (credentialsPath != null && !credentialsPath.trim().isEmpty()) {
            logger.debug("Using explicit credentials from: {}", credentialsPath);
            try (FileInputStream credentialsStream = new FileInputStream(credentialsPath)) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
                SpeechSettings speechSettings = SpeechSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();
                speechClient = SpeechClient.create(speechSettings);
            } catch (IOException e) {
                logger.error("Failed to load credentials from {}: {}", credentialsPath, e.getMessage());
                throw new IOException("Failed to load Google Cloud credentials from: " + credentialsPath, e);
            }
        } else {
            logger.debug("Using Application Default Credentials (GOOGLE_APPLICATION_CREDENTIALS or gcloud auth)");
            speechClient = SpeechClient.create();
        }
        
        try {
            ByteString audioBytes = ByteString.copyFrom(audioData);
            
            // Build recognition config with configured options
            RecognitionConfig.Builder configBuilder = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setSampleRateHertz(sampleRateHertz)
                .setLanguageCode(languageCode)
                .setEnableAutomaticPunctuation(enableAutomaticPunctuation)
                .setMaxAlternatives(maxAlternatives);
            
            // Enable word-level confidence if configured
            if (enableWordConfidence) {
                configBuilder.setEnableWordConfidence(true);
            }
            
            // Use enhanced model if configured
            if (useEnhancedModel) {
                configBuilder.setModel("phone_call"); // Enhanced model for phone calls
            }
            
            // Note: Speaker diarization requires streaming recognition or long-running operations
            // and is not available in the synchronous recognize API
            // If needed, implement using StreamingRecognizeRequest or LongRunningRecognizeRequest
            if (enableSpeakerDiarization) {
                logger.warn("Speaker diarization is not supported in synchronous recognition. " +
                           "Use streaming recognition for speaker diarization support.");
            }
            
            RecognitionConfig config = configBuilder.build();
            
            RecognitionAudio audio = RecognitionAudio.newBuilder()
                .setContent(audioBytes)
                .build();
            
            RecognizeRequest request = RecognizeRequest.newBuilder()
                .setConfig(config)
                .setAudio(audio)
                .build();
            
            logger.info("Sending {} bytes of audio to Google Cloud Speech-to-Text (sample rate: {} Hz, language: {}, duration: {}s)", 
                        audioData.length, 
                        sampleRateHertz, 
                        languageCode,
                        String.format("%.2f", audioDurationSeconds));
            
            RecognizeResponse response = speechClient.recognize(request);
            
            // Process results
            if (response.getResultsList().isEmpty()) {
                logger.warn("Google Cloud Speech-to-Text returned no results. Possible reasons:");
                logger.warn("  - Audio too short (current: {}s, recommended: >0.5s)", String.format("%.2f", audioDurationSeconds));
                logger.warn("  - Audio contains only silence or noise");
                logger.warn("  - Audio format mismatch (expected: LINEAR16, {} Hz)", sampleRateHertz);
                logger.warn("  - Language code mismatch (current: {})", languageCode);
                return null;
            }
            
            // Get the best transcription result
            SpeechRecognitionResult bestResult = response.getResults(0);
            if (bestResult.getAlternativesList().isEmpty()) {
                logger.warn("Google Cloud Speech-to-Text result has no alternatives");
                return null;
            }
            
            String transcript = bestResult.getAlternatives(0).getTranscript();
            float confidence = bestResult.getAlternatives(0).getConfidence();
            
            logger.info("Google Cloud transcription successful: '{}' (confidence: {}%)", 
                       transcript,
                       String.format("%.2f", confidence * 100));
            
            // Log alternative transcriptions if available
            if (bestResult.getAlternativesList().size() > 1) {
                logger.debug("Alternative transcriptions available:");
                for (int i = 1; i < bestResult.getAlternativesList().size(); i++) {
                    float altConfidence = bestResult.getAlternatives(i).getConfidence();
                    logger.debug("  Alternative {}: {} (confidence: {}%)", 
                                i, 
                                bestResult.getAlternatives(i).getTranscript(),
                                String.format("%.2f", altConfidence * 100));
                }
            }
            
            return transcript;
        } finally {
            if (speechClient != null) {
                speechClient.close();
            }
        }
    }
}


