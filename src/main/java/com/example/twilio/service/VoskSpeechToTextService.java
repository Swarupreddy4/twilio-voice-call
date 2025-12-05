package com.example.twilio.service;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

// Vosk imports - Using reflection to load dynamically
// Download from: https://github.com/alphacep/vosk-api/releases
// Add vosk.jar to classpath and native library to library path

/**
 * Open Source Speech-to-Text Service using Vosk
 * 
 * Vosk is an offline speech recognition toolkit that supports multiple languages.
 * 
 * Setup Instructions:
 * 1. Download Vosk model from: https://alphacephei.com/vosk/models
 *    Recommended: vosk-model-small-en-us-0.15 (for English, ~40MB)
 * 2. Extract the model to a directory (e.g., ./models/vosk-model-small-en-us-0.15)
 * 3. Set the model path in application.properties: vosk.model.path=./models/vosk-model-small-en-us-0.15
 * 
 * Alternative: Use CMU Sphinx (see SphinxSpeechToTextService.java)
 */
@Service
public class VoskSpeechToTextService {

    private static final Logger logger = LoggerFactory.getLogger(VoskSpeechToTextService.class);

    @Value("${vosk.model.path:./models/vosk-model-small-en-us-0.15}")
    private String modelPath;

    @Value("${vosk.enabled:false}")
    private boolean enabled;

    // Vosk types - Using reflection to load dynamically
    private Object model;
    private Object recognizer;
    private Class<?> modelClass;
    private Class<?> recognizerClass;
    private java.lang.reflect.Method recognizerAcceptWaveForm;
    private java.lang.reflect.Method recognizerGetResult;
    private java.lang.reflect.Method recognizerGetPartialResult;
    private java.lang.reflect.Method recognizerGetFinalResult;
    private boolean initialized = false;
    private boolean libraryAvailable = false;

    /**
     * Initialize Vosk model using reflection
     */
    @PostConstruct
    public void init() {
        if (!enabled) {
            logger.info("Vosk speech-to-text is disabled");
            return;
        }

        try {
            logger.info("Initializing Vosk model from path: {}", modelPath);
            
            // Check if model directory exists
            File modelDir = new File(modelPath);
            if (!modelDir.exists() || !modelDir.isDirectory()) {
                logger.error("Vosk model directory not found: {}. Please download a model from https://alphacephei.com/vosk/models", modelPath);
                logger.error("Expected path: {}", modelDir.getAbsolutePath());
                return;
            }

            // Try to load Vosk classes using reflection
            // Vosk uses org.vosk package (not org.kaldi)
            try {
                // Try to load LibVosk first (for native library loading)
                try {
                    Class<?> libVoskClass = Class.forName("org.vosk.LibVosk");
                    java.lang.reflect.Method loadMethod = libVoskClass.getMethod("load");
                    loadMethod.invoke(null); // Static method
                    logger.info("Vosk native library loaded successfully");
                } catch (ClassNotFoundException e) {
                    logger.debug("LibVosk class not found, trying without explicit load");
                } catch (Exception e) {
                    logger.warn("Failed to load Vosk native library explicitly: {}", e.getMessage());
                    // Continue anyway - native library might load automatically
                }
                
                // Load main Vosk classes
                modelClass = Class.forName("org.vosk.Model");
                recognizerClass = Class.forName("org.vosk.Recognizer");
                
                // Verify constructors exist (for validation)
                modelClass.getConstructor(String.class);
                recognizerClass.getConstructor(modelClass, float.class);
                
                // Get methods we need
                // Note: Vosk uses acceptWaveForm(byte[], int) not acceptWaveForm(float[], int)
                recognizerAcceptWaveForm = recognizerClass.getMethod("acceptWaveForm", byte[].class, int.class);
                recognizerGetResult = recognizerClass.getMethod("getResult");
                recognizerGetPartialResult = recognizerClass.getMethod("getPartialResult");
                recognizerGetFinalResult = recognizerClass.getMethod("getFinalResult");
                
                libraryAvailable = true;
                logger.info("Vosk library classes loaded successfully");
            } catch (ClassNotFoundException e) {
                logger.error("Vosk library not found. The Maven dependency may not be available.");
                logger.error("Try adding the dependency to pom.xml:");
                logger.error("  <dependency>");
                logger.error("    <groupId>com.alphacephei</groupId>");
                logger.error("    <artifactId>vosk</artifactId>");
                logger.error("    <version>0.3.45</version>");
                logger.error("  </dependency>");
                logger.error("Or download from: https://github.com/alphacep/vosk-api/releases");
                logger.error("The JAR file should be in the Maven repository or you may need to build it from source.");
                return;
            } catch (Exception e) {
                logger.error("Failed to load Vosk library methods: {}", e.getMessage(), e);
                return;
            }

            // Load Vosk model
            try {
                java.lang.reflect.Constructor<?> modelConstructor = modelClass.getConstructor(String.class);
                model = modelConstructor.newInstance(modelPath);
                
                // Create recognizer with sample rate 16000 Hz (Vosk models expect 16000 Hz)
                // We'll resample 8000 Hz audio to 16000 Hz before processing
                java.lang.reflect.Constructor<?> recognizerConstructor = recognizerClass.getConstructor(modelClass, float.class);
                recognizer = recognizerConstructor.newInstance(model, 16000.0f);
                
                initialized = true;
                logger.info("Vosk model initialized successfully from: {}", modelPath);
            } catch (Exception e) {
                logger.error("Failed to load Vosk model: {}", e.getMessage(), e);
                logger.error("Make sure the model path is correct and the model files are valid");
                initialized = false;
            }
            
        } catch (Exception e) {
            logger.error("Failed to initialize Vosk model", e);
            initialized = false;
        }
    }

    /**
     * Transcribe audio bytes to text
     * 
     * @param pcmAudio PCM audio data (16-bit, little-endian, mono)
     * @param sampleRate Sample rate in Hz (default: 8000 for Twilio)
     * @return Transcribed text, or null if transcription fails
     */
    public String transcribe(byte[] pcmAudio, int sampleRate) {
        if (!initialized || !enabled || !libraryAvailable) {
            if (!libraryAvailable) {
                logger.warn("Vosk library not available. Please download from https://github.com/alphacep/vosk-api/releases");
            } else if (!enabled) {
                logger.debug("Vosk is disabled in configuration");
            } else if (!initialized) {
                logger.warn("Vosk is not initialized. Check model path: {}", modelPath);
            }
            return null;
        }

        if (pcmAudio == null || pcmAudio.length == 0) {
            logger.debug("Empty audio provided to Vosk");
            return null;
        }

        try {
            // Validate audio data
            if (pcmAudio.length < 2) {
                logger.warn("Audio data too short for Vosk (minimum 2 bytes for one sample)");
                return null;
            }
            
            // Vosk models expect 16000 Hz, but Twilio sends 8000 Hz
            // Resample audio if needed
            byte[] audioToProcess = pcmAudio;
            int bytesToProcess = pcmAudio.length;
            
            if (sampleRate == 8000) {
                // Resample from 8000 Hz to 16000 Hz (upsample by 2x)
                audioToProcess = resampleAudio8000To16000(pcmAudio);
                bytesToProcess = audioToProcess.length;
                logger.debug("Resampled audio from 8000 Hz to 16000 Hz: {} bytes -> {} bytes", pcmAudio.length, bytesToProcess);
                
                // Validate resampled audio
                if (audioToProcess == null || audioToProcess.length < 2) {
                    logger.error("Resampling failed or produced invalid audio");
                    return null;
                }
            }
            
            // Create a new recognizer for each transcription to avoid state issues
            // Vosk recognizers can have internal state that gets corrupted if reused incorrectly
            Object currentRecognizer;
            try {
                logger.debug("Creating new Vosk recognizer with 16000 Hz for this transcription");
                java.lang.reflect.Constructor<?> recognizerConstructor = recognizerClass.getConstructor(modelClass, float.class);
                currentRecognizer = recognizerConstructor.newInstance(model, 16000.0f);
            } catch (Exception e) {
                logger.error("Failed to create Vosk recognizer: {}", e.getMessage(), e);
                return null;
            }

            // Validate audio format - must be even number of bytes (16-bit samples)
            if (bytesToProcess % 2 != 0) {
                logger.warn("Audio data length is not even ({} bytes), truncating by 1 byte", bytesToProcess);
                bytesToProcess = bytesToProcess - 1;
            }
            
            // Vosk accepts both byte[] and short[] formats
            // Try short[] format first (more common), fallback to byte[] if needed
            logger.debug("Processing {} bytes of audio with Vosk ({} samples)", bytesToProcess, bytesToProcess / 2);
            
            // Convert to short array (16-bit samples)
            int numSamples = bytesToProcess / 2;
            short[] audioShorts = new short[numSamples];
            ByteBuffer buffer = ByteBuffer.wrap(audioToProcess, 0, bytesToProcess);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < numSamples; i++) {
                audioShorts[i] = buffer.getShort();
            }
            
            // Try to use acceptWaveForm(short[], int) method
            Boolean hasResult = false;
            try {
                // Check if acceptWaveForm(short[], int) method exists
                java.lang.reflect.Method acceptWaveFormShorts = null;
                try {
                    acceptWaveFormShorts = recognizerClass.getMethod("acceptWaveForm", short[].class, int.class);
                } catch (NoSuchMethodException e) {
                    // Method doesn't exist, will use byte[] version
                }
                
                if (acceptWaveFormShorts != null) {
                    // Use short[] version (preferred)
                    logger.debug("Using acceptWaveForm(short[], int) method");
                    hasResult = (Boolean) acceptWaveFormShorts.invoke(currentRecognizer, audioShorts, numSamples);
                } else {
                    // Fallback to byte[] version
                    logger.debug("Using acceptWaveForm(byte[], int) method");
                    hasResult = (Boolean) recognizerAcceptWaveForm.invoke(currentRecognizer, audioToProcess, bytesToProcess);
                }
            } catch (Exception e) {
                logger.error("Error calling acceptWaveForm: {}", e.getMessage(), e);
                // Try byte[] version as fallback
                try {
                    logger.debug("Falling back to byte[] version");
                    hasResult = (Boolean) recognizerAcceptWaveForm.invoke(currentRecognizer, audioToProcess, bytesToProcess);
                } catch (Exception e2) {
                    logger.error("Both acceptWaveForm methods failed: {}", e2.getMessage(), e2);
                    return null;
                }
            }
            
            // Always try to get final result first (even if hasResult is false)
            // This ensures we get any text that was recognized
            String finalResult = (String) recognizerGetFinalResult.invoke(currentRecognizer);
            if (finalResult != null) {
                logger.info("Vosk final result: {}", finalResult);
                String extracted = extractTextFromResult(finalResult);
                if (extracted != null && !extracted.trim().isEmpty()) {
                    logger.info("Vosk extracted text (final): {}", extracted);
                    return extracted;
                }
            }
            
            if (hasResult) {
                // Final result from getResult() method
                String result = (String) recognizerGetResult.invoke(currentRecognizer);
                logger.info("Vosk transcription result (getResult): {}", result);
                String extracted = extractTextFromResult(result);
                if (extracted != null && !extracted.trim().isEmpty()) {
                    logger.info("Vosk extracted text (getResult): {}", extracted);
                    return extracted;
                } else {
                    logger.warn("Vosk getResult() returned empty. Raw result: {}", result);
                }
            }
            
            // Get partial result as fallback
            String partial = (String) recognizerGetPartialResult.invoke(currentRecognizer);
            logger.info("Vosk transcription result (partial): {}", partial);
            String extracted = extractTextFromResult(partial);
            if (extracted != null && !extracted.trim().isEmpty()) {
                logger.info("Vosk extracted text (partial): {}", extracted);
                return extracted;
            } else {
                logger.warn("Vosk returned no transcription. Partial result: {}", partial);
                logger.warn("This might indicate: audio too short, poor quality, or no speech detected");
                return null;
            }
            
        } catch (Exception e) {
            logger.error("Error during Vosk transcription: {}", e.getMessage(), e);
            logger.error("Stack trace:", e);
            return null;
        }
    }

    /**
     * Transcribe audio with default 8000 Hz sample rate (Twilio standard)
     */
    public String transcribe(byte[] pcmAudio) {
        return transcribe(pcmAudio, 8000);
    }

    /**
     * Finalize recognition and get final result
     * Call this when audio stream ends
     */
    public String finalizeRecognition() {
        if (!initialized || recognizer == null || !libraryAvailable) {
            return null;
        }

        try {
            String finalResult = (String) recognizerGetFinalResult.invoke(recognizer);
            logger.debug("Vosk final result: {}", finalResult);
            return extractTextFromResult(finalResult);
        } catch (Exception e) {
            logger.error("Error finalizing Vosk recognition: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Resample audio from 8000 Hz to 16000 Hz (upsample by 2x)
     * Uses linear interpolation to double the sample rate
     * 
     * @param pcm8000 PCM audio at 8000 Hz (16-bit, little-endian)
     * @return PCM audio at 16000 Hz (16-bit, little-endian)
     */
    private byte[] resampleAudio8000To16000(byte[] pcm8000) {
        if (pcm8000 == null || pcm8000.length == 0) {
            return pcm8000;
        }
        
        // PCM is 16-bit (2 bytes per sample), little-endian
        int numSamples8000 = pcm8000.length / 2;
        int numSamples16000 = numSamples8000 * 2; // Double the samples
        
        // Read input samples
        short[] samples8000 = new short[numSamples8000];
        ByteBuffer inputBuffer = ByteBuffer.wrap(pcm8000);
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < numSamples8000; i++) {
            samples8000[i] = inputBuffer.getShort();
        }
        
        // Resample using linear interpolation
        short[] samples16000 = new short[numSamples16000];
        for (int i = 0; i < numSamples16000; i++) {
            // Map output index to input index (0.5x because we're upsampling 2x)
            double inputIndex = i * 0.5;
            int inputIndexInt = (int) inputIndex;
            double fraction = inputIndex - inputIndexInt;
            
            if (inputIndexInt >= numSamples8000 - 1) {
                // Last sample, just repeat it
                samples16000[i] = samples8000[numSamples8000 - 1];
            } else {
                // Linear interpolation between two samples
                short sample1 = samples8000[inputIndexInt];
                short sample2 = samples8000[inputIndexInt + 1];
                samples16000[i] = (short) (sample1 + (sample2 - sample1) * fraction);
            }
        }
        
        // Convert back to byte array
        ByteBuffer outputBuffer = ByteBuffer.allocate(numSamples16000 * 2);
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : samples16000) {
            outputBuffer.putShort(sample);
        }
        
        return outputBuffer.array();
    }
    
    /**
     * Convert PCM byte array to float array (not needed for Vosk, but kept for compatibility)
     * Vosk accepts byte arrays directly via acceptWaveForm(byte[], int)
     * This method is kept in case we need it for other purposes
     */
    @SuppressWarnings("unused")
    private float[] convertBytesToFloats(byte[] pcmBytes) {
        // PCM is 16-bit (2 bytes per sample), little-endian
        int numSamples = pcmBytes.length / 2;
        float[] floats = new float[numSamples];
        
        ByteBuffer buffer = ByteBuffer.wrap(pcmBytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        for (int i = 0; i < numSamples; i++) {
            short sample = buffer.getShort();
            // Normalize to [-1.0, 1.0] range
            floats[i] = sample / 32768.0f;
        }
        
        return floats;
    }

    /**
     * Extract text from Vosk JSON result
     * Vosk returns JSON in two formats:
     * - Final result: {"text": "hello world", "partial": false}
     * - Partial result: {"partial": "hello wor"}
     */
    private String extractTextFromResult(String jsonResult) {
        if (jsonResult == null || jsonResult.trim().isEmpty()) {
            logger.debug("Vosk JSON result is null or empty");
            return null;
        }
        
        logger.debug("Extracting text from Vosk JSON: {}", jsonResult);
        
        try {
            // Try to extract from "text" field first (final results)
            String text = extractField(jsonResult, "text");
            if (text != null && !text.trim().isEmpty()) {
                logger.debug("Successfully extracted text from 'text' field: {}", text);
                return text.trim();
            }
            
            // If no "text" field, try "partial" field (partial results)
            String partial = extractField(jsonResult, "partial");
            if (partial != null && !partial.trim().isEmpty()) {
                logger.debug("Successfully extracted text from 'partial' field: {}", partial);
                return partial.trim();
            }
            
            logger.debug("No text found in either 'text' or 'partial' fields");
            return null;
        } catch (Exception e) {
            logger.warn("Failed to parse Vosk result: {}", jsonResult, e);
        }
        
        return null;
    }
    
    /**
     * Extract a field value from JSON string
     * @param json JSON string
     * @param fieldName Field name to extract (e.g., "text", "partial")
     * @return Field value or null if not found
     */
    private String extractField(String json, String fieldName) {
        try {
            String searchPattern = "\"" + fieldName + "\"";
            int fieldStart = json.indexOf(searchPattern);
            if (fieldStart == -1) {
                return null;
            }
            
            int colonIndex = json.indexOf(":", fieldStart);
            if (colonIndex == -1) {
                return null;
            }
            
            // Skip whitespace after colon
            int valueStart = colonIndex + 1;
            while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
                valueStart++;
            }
            
            // Check if value is a string (starts with quote)
            if (valueStart >= json.length() || json.charAt(valueStart) != '"') {
                return null;
            }
            
            int quoteStart = valueStart + 1; // Move past opening quote
            int quoteEnd = json.indexOf("\"", quoteStart);
            if (quoteEnd == -1) {
                return null;
            }
            
            if (quoteStart < quoteEnd) {
                return json.substring(quoteStart, quoteEnd);
            }
        } catch (Exception e) {
            logger.debug("Error extracting field '{}': {}", fieldName, e.getMessage());
        }
        
        return null;
    }

    /**
     * Check if service is ready
     */
    public boolean isReady() {
        return initialized && enabled && libraryAvailable;
    }
    
    /**
     * Check if Vosk library is available (even if not initialized)
     */
    public boolean isLibraryAvailable() {
        return libraryAvailable;
    }

    /**
     * Cleanup resources
     */
    @PreDestroy
    public void cleanup() {
        if (recognizer != null) {
            try {
                recognizer = null;
            } catch (Exception e) {
                logger.warn("Error cleaning up Vosk recognizer", e);
            }
        }
        
        if (model != null) {
            try {
                model = null;
            } catch (Exception e) {
                logger.warn("Error cleaning up Vosk model", e);
            }
        }
        
        logger.info("Vosk service cleaned up");
    }
}

