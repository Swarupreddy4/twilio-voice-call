package com.example.twilio.service.example;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.twilio.service.VoskSpeechToTextService;

/**
 * Example demonstrating audio-to-text conversion using open source libraries
 * 
 * This example shows how to:
 * 1. Use Vosk for offline speech recognition
 * 2. Process audio files or byte arrays
 * 3. Integrate with your existing audio processing pipeline
 * 
 * Prerequisites:
 * 1. Download Vosk model from: https://alphacephei.com/vosk/models
 *    Recommended models:
 *    - vosk-model-small-en-us-0.15 (40MB, good for testing)
 *    - vosk-model-en-us-0.22 (1.8GB, better accuracy)
 * 2. Extract model to ./models/vosk-model-small-en-us-0.15/
 * 3. Add Vosk dependency to pom.xml (see pom.xml)
 */
public class OpenSourceSpeechToTextExample {

    private static final Logger logger = LoggerFactory.getLogger(OpenSourceSpeechToTextExample.class);

    /**
     * Example 1: Transcribe audio from a byte array (PCM format)
     */
    public static String transcribeFromBytes(byte[] pcmAudio, int sampleRate) {
        // Initialize Vosk service
        VoskSpeechToTextService voskService = new VoskSpeechToTextService();
        
        // Note: In Spring Boot, you would inject this as a @Service
        // For this example, we'll show manual initialization
        
        try {
            // Transcribe audio
            String transcription = voskService.transcribe(pcmAudio, sampleRate);
            return transcription;
        } catch (Exception e) {
            logger.error("Error transcribing audio", e);
            return null;
        }
    }

    /**
     * Example 2: Transcribe audio from a WAV file
     */
    public static String transcribeFromWavFile(String wavFilePath) {
        try {
            // Read WAV file
            File wavFile = new File(wavFilePath);
            if (!wavFile.exists()) {
                logger.error("WAV file not found: {}", wavFilePath);
                return null;
            }

            // Read file bytes
            byte[] audioBytes = new byte[(int) wavFile.length()];
            try (FileInputStream fis = new FileInputStream(wavFile)) {
                fis.read(audioBytes);
            }

            // Extract PCM data from WAV (skip WAV header, typically 44 bytes)
            // For a proper implementation, you should parse the WAV header
            int wavHeaderSize = 44;
            if (audioBytes.length > wavHeaderSize) {
                byte[] pcmData = new byte[audioBytes.length - wavHeaderSize];
                System.arraycopy(audioBytes, wavHeaderSize, pcmData, 0, pcmData.length);
                
                // Transcribe (assuming 16kHz sample rate for WAV files)
                return transcribeFromBytes(pcmData, 16000);
            }

        } catch (IOException e) {
            logger.error("Error reading WAV file", e);
        }

        return null;
    }

    /**
     * Example 3: Convert mu-law audio (Twilio format) to PCM and transcribe
     */
    public static String transcribeMuLawAudio(byte[] muLawAudio) {
        // Convert mu-law to PCM
        byte[] pcmAudio = convertMuLawToPCM(muLawAudio);
        
        // Transcribe PCM audio (Twilio uses 8000 Hz)
        return transcribeFromBytes(pcmAudio, 8000);
    }

    /**
     * Convert mu-law encoded audio to PCM format
     * (Same conversion as in AiAgentService)
     */
    private static byte[] convertMuLawToPCM(byte[] muLawData) {
        short[] pcmData = new short[muLawData.length];
        for (int i = 0; i < muLawData.length; i++) {
            pcmData[i] = muLawToLinear(muLawData[i] & 0xFF);
        }
        
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
    private static short muLawToLinear(int muLawByte) {
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
     * Main method for testing
     */
    public static void main(String[] args) {
        logger.info("Open Source Speech-to-Text Example");
        logger.info("====================================");
        
        // Example: Transcribe from byte array
        // In a real scenario, this would come from Twilio WebSocket stream
        byte[] testAudio = new byte[1000]; // Placeholder
        
        String transcription = transcribeMuLawAudio(testAudio);
        if (transcription != null) {
            logger.info("Transcription: {}", transcription);
        } else {
            logger.info("No transcription available (this is expected with placeholder audio)");
        }
        
        logger.info("\nTo use this in production:");
        logger.info("1. Download Vosk model from https://alphacephei.com/vosk/models");
        logger.info("2. Extract to ./models/vosk-model-small-en-us-0.15/");
        logger.info("3. Configure in application.properties:");
        logger.info("   vosk.model.path=./models/vosk-model-small-en-us-0.15");
        logger.info("   vosk.enabled=true");
        logger.info("4. Inject VoskSpeechToTextService in your AiAgentService");
    }
}





