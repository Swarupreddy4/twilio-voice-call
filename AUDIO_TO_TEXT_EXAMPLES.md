# Audio-to-Text Conversion Examples

This directory contains examples and implementations for audio-to-text conversion using open source Java libraries.

## Files Created

### 1. Service Classes

- **`VoskSpeechToTextService.java`** - Service for Vosk (recommended open source option)
  - Offline speech recognition
  - Supports multiple languages
  - Good accuracy with proper models
  - Location: `src/main/java/com/example/twilio/service/VoskSpeechToTextService.java`

- **`SphinxSpeechToTextService.java`** - Alternative using CMU Sphinx
  - Fully Java-based
  - More configuration required
  - Location: `src/main/java/com/example/twilio/service/SphinxSpeechToTextService.java`

### 2. Example Classes

- **`SimpleAudioToTextExample.java`** - Working example with audio format conversion
  - Demonstrates mu-law to PCM conversion (working code)
  - Shows the structure for STT integration
  - Can be run immediately
  - Location: `src/main/java/com/example/twilio/service/example/SimpleAudioToTextExample.java`

- **`OpenSourceSpeechToTextExample.java`** - Complete integration example
  - Shows how to use Vosk service
  - Demonstrates file and byte array processing
  - Location: `src/main/java/com/example/twilio/service/example/OpenSourceSpeechToTextExample.java`

### 3. Documentation

- **`VOSK_SETUP.md`** - Complete setup guide for Vosk
  - Step-by-step instructions
  - Model download links
  - Configuration examples
  - Troubleshooting tips

## Quick Start

### Option 1: Use the Working Example (No Library Required)

```java
// This works immediately - demonstrates audio conversion
SimpleAudioToTextExample.main(args);
```

This example shows:
- Mu-law to PCM conversion (working code)
- Audio processing structure
- How to prepare audio for STT libraries

### Option 2: Set Up Vosk (Recommended)

1. **Download Vosk Model:**
   ```
   Visit: https://alphacephei.com/vosk/models
   Download: vosk-model-small-en-us-0.15 (40MB)
   Extract to: ./models/vosk-model-small-en-us-0.15/
   ```

2. **Download Vosk Library:**
   ```
   Visit: https://github.com/alphacep/vosk-api/releases
   Download: vosk-win64-0.3.45.zip (or your OS version)
   Extract and add to classpath
   ```

3. **Configure:**
   ```properties
   # In application.properties
   vosk.enabled=true
   vosk.model.path=./models/vosk-model-small-en-us-0.15
   ```

4. **Uncomment Code:**
   - Open `VoskSpeechToTextService.java`
   - Uncomment the Vosk imports and implementation code
   - Replace `Object` placeholders with actual types

5. **Use in Your Code:**
   ```java
   @Autowired
   private VoskSpeechToTextService voskService;
   
   String transcription = voskService.transcribe(pcmAudio, 8000);
   ```

## Integration with Existing Code

To integrate with your `AiAgentService`, update the `transcribeAudio` method:

```java
@Autowired(required = false)
private VoskSpeechToTextService voskService;

private String transcribeAudio(byte[] pcmAudio) {
    // Try Vosk first (open source)
    if (voskService != null && voskService.isReady()) {
        String transcription = voskService.transcribe(pcmAudio, 8000);
        if (transcription != null && !transcription.trim().isEmpty()) {
            return transcription;
        }
    }
    
    // Fallback to Google Cloud (if configured)
    try {
        return transcribeWithGoogleCloud(pcmAudio);
    } catch (Exception e) {
        logger.error("Error transcribing with Google Cloud", e);
    }
    
    return null;
}
```

## Audio Format

All examples handle:
- **Input**: Mu-law encoded audio (Twilio format) or PCM
- **Output**: PCM 16-bit, little-endian, mono
- **Sample Rate**: 8000 Hz (Twilio default) or 16000 Hz

## Dependencies

The `pom.xml` has been updated with:
- Vosk dependency (may need manual download)
- Sphinx4 dependencies (commented out, uncomment to use)

## Next Steps

1. **For Testing**: Run `SimpleAudioToTextExample` to see audio conversion
2. **For Production**: Set up Vosk following `VOSK_SETUP.md`
3. **For Integration**: Update `AiAgentService` to use `VoskSpeechToTextService`

## Resources

- Vosk Documentation: https://alphacephei.com/vosk/
- Vosk Models: https://alphacephei.com/vosk/models
- Vosk GitHub: https://github.com/alphacep/vosk-api
- CMU Sphinx: https://cmusphinx.github.io/

## Notes

- The service classes are structured but require the actual libraries to be functional
- All placeholder code is clearly marked with comments
- The `SimpleAudioToTextExample` works immediately without any external libraries
- See `VOSK_SETUP.md` for detailed setup instructions





