# Silence Detection Configuration

## Overview

The silence detection timeout controls how long the system waits after the user stops speaking before processing their audio and generating an AI response.

## Configuration

### Property: `conversation.silence.timeout.ms`

**Location:** `src/main/resources/application.properties`

**Default Value:** `1500` (1.5 seconds)

**Current Value:** `1500` milliseconds

### How It Works

1. **Audio Buffering**: As the user speaks, audio chunks are continuously added to a buffer
2. **Silence Detection**: The system checks every 500ms if no new audio has arrived for the timeout period
3. **Processing Trigger**: When silence is detected (no audio for >= timeout), the buffered audio is processed
4. **AI Response**: The system transcribes the audio, generates an AI response, and sends it back

## Timing Flow

```
User starts speaking
  ↓
Audio chunks arrive → Buffered
  ↓
User stops speaking
  ↓
[Silence Timeout Period]
  ↓ (e.g., 1500ms)
Silence detected!
  ↓
Process buffered audio
  ↓
Generate AI response
  ↓
Send response to user
```

## Adjusting the Timeout

### Shorter Timeout (Faster Response)
```properties
conversation.silence.timeout.ms=1000  # 1 second
```
**Pros:**
- Faster response time
- More responsive conversation

**Cons:**
- May interrupt user if they pause mid-sentence
- Might process incomplete thoughts

### Longer Timeout (More Accurate)
```properties
conversation.silence.timeout.ms=2000  # 2 seconds
conversation.silence.timeout.ms=3000  # 3 seconds
```
**Pros:**
- Waits for user to finish complete thoughts
- Reduces false triggers from natural pauses

**Cons:**
- Slower response time
- User might think system isn't listening

### Recommended Values

- **Fast-paced conversations**: 1000-1500ms
- **Normal conversations**: 1500-2000ms (default)
- **Careful/formal conversations**: 2000-3000ms
- **Very slow speakers**: 3000-4000ms

## Current Implementation

```java
@Value("${conversation.silence.timeout.ms:1500}")
private long silenceTimeoutMs;
```

The timeout is:
- Configurable via `application.properties`
- Applied per WebSocket session
- Checked every 500ms by a background thread
- Used in `AudioBuffer.hasSilence()` method

## Debugging Silence Detection

### Enable Debug Logging

Add to `application.properties`:
```properties
logging.level.com.example.twilio.websocket=DEBUG
```

### What to Look For

1. **Silence Detection Progress:**
   ```
   Silence detection: 500ms / 1500ms (33%) - Session: abc123
   Silence detection: 1000ms / 1500ms (66%) - Session: abc123
   Silence detection: 1500ms / 1500ms (100%) - Session: abc123
   ```

2. **Silence Detected:**
   ```
   >>> Silence detected! Processing buffered audio for session abc123 (1500ms since last audio)
   ```

3. **Audio Chunking:**
   - Audio chunks should be arriving while user speaks
   - Buffer should accumulate chunks
   - Silence should trigger after timeout

## Troubleshooting

### AI Responds Too Early

**Symptom:** AI interrupts user mid-sentence

**Solution:** Increase timeout
```properties
conversation.silence.timeout.ms=2000
```

### AI Responds Too Late

**Symptom:** Long delay after user finishes speaking

**Solution:** Decrease timeout
```properties
conversation.silence.timeout.ms=1000
```

### No Response at All

**Check:**
1. Is audio being buffered? (Look for "Audio chunk buffered" logs)
2. Is silence being detected? (Look for "Silence detected" logs)
3. Is processing happening? (Look for "Processing buffered audio" logs)

## Advanced: Dynamic Timeout

For more sophisticated behavior, you could implement dynamic timeouts based on:
- User speaking patterns
- Conversation context
- Audio energy levels
- Previous response times

This would require custom implementation beyond the current configuration.

## Related Configuration

- `MIN_RESPONSE_INTERVAL_MS`: Minimum time between AI responses (3 seconds)
- `ai.agent.test.mode`: Enable test mode for faster testing
- Silence checker interval: 500ms (hardcoded, checks every half second)

