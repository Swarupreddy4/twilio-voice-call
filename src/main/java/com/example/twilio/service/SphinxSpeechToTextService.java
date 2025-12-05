package com.example.twilio.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

// CMU Sphinx imports - Uncomment after adding Sphinx4 dependencies to pom.xml
// import edu.cmu.sphinx.api.Configuration;
// import edu.cmu.sphinx.api.LiveSpeechRecognizer;
// import edu.cmu.sphinx.api.SpeechResult;
// import edu.cmu.sphinx.api.StreamSpeechRecognizer;

/**
 * Alternative Open Source Speech-to-Text Service using CMU Sphinx
 * 
 * CMU Sphinx is another popular open source speech recognition toolkit.
 * 
 * Setup Instructions:
 * 1. Add Sphinx4 dependencies to pom.xml (see example below)
 * 2. Download acoustic model and language model
 *    - Acoustic model: http://www.speech.cs.cmu.edu/sphinx/models/
 *    - Language model: Can use default or download custom
 * 3. Configure paths in application.properties
 * 
 * Note: Sphinx4 requires more configuration but is fully Java-based
 */
@Service
public class SphinxSpeechToTextService {

    private static final Logger logger = LoggerFactory.getLogger(SphinxSpeechToTextService.class);

    @Value("${sphinx.acoustic.model.path:./models/sphinx4-en-us}")
    private String acousticModelPath;

    @Value("${sphinx.dictionary.path:./models/cmudict-en-us.dict}")
    private String dictionaryPath;

    @Value("${sphinx.language.model.path:./models/en-us.lm.bin}")
    private String languageModelPath;

    @Value("${sphinx.enabled:false}")
    private boolean enabled;

    // Sphinx types - Uncomment after adding Sphinx4 library
    // private Configuration configuration;
    // private StreamSpeechRecognizer recognizer;
    private Object configuration; // Placeholder
    private Object recognizer; // Placeholder
    private boolean initialized = false;

    /**
     * Initialize CMU Sphinx
     */
    @PostConstruct
    public void init() {
        if (!enabled) {
            logger.info("CMU Sphinx speech-to-text is disabled");
            return;
        }

        try {
            logger.info("Initializing CMU Sphinx with acoustic model: {}", acousticModelPath);
            
            // Uncomment after adding Sphinx4 library:
            /*
            configuration = new Configuration();
            configuration.setAcousticModelPath(acousticModelPath);
            configuration.setDictionaryPath(dictionaryPath);
            configuration.setLanguageModelPath(languageModelPath);
            configuration.setSampleRate(8000);
            recognizer = new StreamSpeechRecognizer(configuration);
            */
            
            logger.warn("Sphinx4 library not loaded. Please add dependencies to pom.xml");
            // Once Sphinx4 is properly set up, uncomment the configuration code above
            // and set initialized = true here
            return;
            
        } catch (Exception e) {
            logger.error("Failed to initialize CMU Sphinx", e);
            initialized = false;
        }
    }

    /**
     * Transcribe audio bytes to text
     * 
     * @param pcmAudio PCM audio data (16-bit, little-endian, mono)
     * @return Transcribed text, or null if transcription fails
     */
    public String transcribe(byte[] pcmAudio) {
        if (!initialized || !enabled) {
            logger.warn("CMU Sphinx is not initialized or disabled");
            return null;
        }

        if (pcmAudio == null || pcmAudio.length == 0) {
            return null;
        }

        try {
            // Uncomment after adding Sphinx4 library:
            /*
            recognizer.startRecognition(new ByteArrayInputStream(pcmAudio));
            SpeechResult result = recognizer.getResult();
            if (result != null) {
                String hypothesis = result.getHypothesis();
                logger.debug("Sphinx transcription: {}", hypothesis);
                return hypothesis;
            }
            recognizer.stopRecognition();
            */
            
            logger.warn("Sphinx not properly initialized");
            return null;
            
        } catch (Exception e) {
            logger.error("Error during Sphinx transcription", e);
            return null;
        }
    }

    /**
     * Check if service is ready
     */
    public boolean isReady() {
        return initialized && enabled;
    }

    /**
     * Cleanup resources
     */
    @PreDestroy
    public void cleanup() {
        if (recognizer != null) {
            try {
                // Uncomment after adding Sphinx4 library:
                // recognizer.stopRecognition();
                recognizer = null;
            } catch (Exception e) {
                logger.warn("Error cleaning up Sphinx recognizer", e);
            }
        }
        
        logger.info("CMU Sphinx service cleaned up");
    }
}

