# Open Source Audio-to-Text Setup Guide

This guide explains how to set up open source speech-to-text conversion using Vosk in your Java application.

## Overview

Two open source options are provided:
1. **Vosk** (Recommended) - Modern, accurate, supports multiple languages
2. **CMU Sphinx** - Fully Java-based, more configuration required

## Option 1: Vosk Setup (Recommended)

### Step 1: Download Vosk Model

1. Visit https://alphacephei.com/vosk/models
2. Download a model (recommended for English):
   - **vosk-model-small-en-us-0.15** (40MB) - Good for testing
   - **vosk-model-en-us-0.22** (1.8GB) - Better accuracy
   - **vosk-model-en-us-0.22-lgraph** (128MB) - Good balance

3. Extract the model to your project directory:
   ```
   ./models/vosk-model-small-en-us-0.15/
   ```

### Step 2: Add Vosk Dependency

The `pom.xml` already includes Vosk dependency. However, Vosk Java bindings may need to be downloaded manually:

**Option A: Use Maven (if available)**
```xml
<dependency>
    <groupId>com.alphacephei</groupId>
    <artifactId>vosk</artifactId>
    <version>0.3.45</version>
</dependency>
```

**Option B: Manual Download**
1. Download from: https://github.com/alphacep/vosk-api/releases
2. Download `vosk-win64-0.3.45.zip` (or appropriate OS version)
3. Extract and add `vosk.dll` (Windows) or `libvosk.so` (Linux) to your library path
4. Add the JAR file to your classpath

### Step 3: Configure Application

Update `application.properties`:
```properties
vosk.enabled=true
vosk.model.path=./models/vosk-model-small-en-us-0.15
```

### Step 4: Use in Your Code

```java
@Autowired
private VoskSpeechToTextService voskService;

public String transcribeAudio(byte[] pcmAudio) {
    if (voskService.isReady()) {
        return voskService.transcribe(pcmAudio, 8000); // 8000 Hz for Twilio
    }
    return null;
}
```

## Option 2: CMU Sphinx Setup

### Step 1: Add Dependencies

Uncomment Sphinx dependencies in `pom.xml`:
```xml
<dependency>
    <groupId>edu.cmu.sphinx</groupId>
    <artifactId>sphinx4-core</artifactId>
    <version>5prealpha</version>
</dependency>
<dependency>
    <groupId>edu.cmu.sphinx</groupId>
    <artifactId>sphinx4-data</artifactId>
    <version>5prealpha</version>
</dependency>
```

### Step 2: Download Models

1. Download acoustic model from: http://www.speech.cs.cmu.edu/sphinx/models/
2. Download language model and dictionary
3. Extract to `./models/sphinx4-en-us/`

### Step 3: Configure Application

Update `application.properties`:
```properties
sphinx.enabled=true
sphinx.acoustic.model.path=./models/sphinx4-en-us
sphinx.dictionary.path=./models/cmudict-en-us.dict
sphinx.language.model.path=./models/en-us.lm.bin
```

## Integration with AiAgentService

To use Vosk in your existing `AiAgentService`, update the `transcribeAudio` method:

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

## Audio Format Requirements

- **Input Format**: PCM (16-bit, little-endian, mono)
- **Sample Rate**: 8000 Hz (Twilio default) or 16000 Hz (better accuracy)
- **Mu-law Conversion**: The service handles mu-law to PCM conversion automatically

## Testing

1. Run the example:
   ```java
   OpenSourceSpeechToTextExample.main(args);
   ```

2. Test with your Twilio audio stream:
   - The `VoskSpeechToTextService` will automatically process audio from your WebSocket handler
   - Check logs for transcription results

## Troubleshooting

### Vosk Model Not Found
- Ensure model path is correct in `application.properties`
- Check that model directory exists and contains model files
- Verify path is relative to project root or use absolute path

### Vosk Library Not Loading
- Ensure native library (`.dll` or `.so`) is in your library path
- For Windows, add `vosk.dll` to `PATH` or project root
- For Linux, ensure `libvosk.so` is accessible

### Low Accuracy
- Try a larger model (e.g., `vosk-model-en-us-0.22`)
- Ensure audio sample rate matches (8000 Hz for Twilio)
- Check audio quality - Vosk works best with clear speech

### Performance Issues
- Smaller models are faster but less accurate
- Consider using streaming recognition for real-time processing
- Cache the model instance (already done in the service)

## Comparison: Vosk vs Google Cloud Speech-to-Text

| Feature | Vosk (Open Source) | Google Cloud |
|---------|-------------------|--------------|
| Cost | Free | Pay per use |
| Offline | Yes | No (requires internet) |
| Setup | Model download required | API key required |
| Accuracy | Good (depends on model) | Excellent |
| Languages | 20+ languages | 100+ languages |
| Latency | Low (local processing) | Network dependent |
| Privacy | Data stays local | Data sent to Google |

## Additional Resources

- Vosk Documentation: https://alphacephei.com/vosk/
- Vosk GitHub: https://github.com/alphacep/vosk-api
- CMU Sphinx: https://cmusphinx.github.io/
- Model Downloads: https://alphacephei.com/vosk/models





