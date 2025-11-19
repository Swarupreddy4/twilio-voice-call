# Outbound Call Bidirectional Conversation Fix

## Problem
Outbound AI calls were not having bidirectional conversations - when the user spoke, there was no response back.

## Root Causes

1. **Stream Continuation Issue**: After injecting TwiML with AI response, the stream pause was too short (5 seconds)
2. **Insufficient Logging**: Hard to diagnose where the conversation was breaking
3. **Stream URL Not Properly Maintained**: Stream URL might not be correctly stored/retrieved for outbound calls

## Fixes Implemented

### 1. Extended Stream Pause
- Changed pause from 5 seconds to 60 seconds after AI response
- Gives more time for user to speak and continue conversation
- Ensures stream stays active longer

### 2. Enhanced Logging
- **Outbound Call Initiation**: Clear logging when outbound call starts
- **User Speech**: Timestamped logging with `[YYYY-MM-DD HH:mm:ss] USER SPEECH`
- **AI Response**: Timestamped logging with `[YYYY-MM-DD HH:mm:ss] AI RESPONSE`
- **Stream Continuation**: Logs when stream is restarted after AI response
- **Call Webhook**: Logs when TwiML is requested for outbound calls

### 3. Stream URL Management
- Better tracking of stream URL per session
- Logs stream URL when injecting TwiML
- Warns if stream URL is missing

### 4. Conversation Flow Logging
- Logs each step of the conversation
- Shows when waiting for user to speak again
- Tracks call SID throughout the conversation

## Expected Log Flow for Outbound Call

```
================================================================================
>>> INITIATING OUTBOUND CALL
>>> From: +18026590229
>>> To: +1234567890
>>> TwiML URL: https://your-ngrok-url.ngrok.io/twilio/voice
================================================================================
>>> Outbound call created successfully
>>> Call SID: CAxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
>>> Call Status: queued
>>> Waiting for call to be answered and WebSocket connection...
================================================================================

[When call is answered]
>>> Voice call webhook received - Call SID: CAxxx, Status: answered
>>> Generating TwiML with WebSocket stream for bidirectional conversation
>>> Generated TwiML for voice call with WebSocket stream
>>> TwiML generated and sent to Twilio for call CAxxx

[WebSocket connection]
>>> Connected event received for session: {sessionId}
Start event received for session: {sessionId}
>>> Conversation started - Call SID: CAxxx, Session: {sessionId}

[User speaks]
>>> Media event received - Session: {sessionId}, Track: inbound, Payload size: 160
>>> Audio chunk buffered - Session: {sessionId}, Size: 160 bytes, Chunks: 1, Total: 160 bytes
... (more chunks) ...

[After silence detected]
>>> Processing buffered audio for session {sessionId} (3200 bytes)
================================================================================
[2024-01-15 10:30:45] USER SPEECH [Session: {sessionId}]: Hello, how are you?
================================================================================
--------------------------------------------------------------------------------
[2024-01-15 10:30:46] AI RESPONSE [Session: {sessionId}]: I heard you say: Hello, how are you?. This is a placeholder response.
--------------------------------------------------------------------------------
>>> Sending AI response to caller for session {sessionId}
[2024-01-15 10:30:46] Sending AI response to call CAxxx: I heard you say: Hello, how are you?...
>>> Injecting TwiML with Say and Stream continuation for call CAxxx
>>> Stream URL: wss://your-ngrok-url.ngrok.io/twilio/media-stream
>>> Successfully sent AI response. Stream will continue after speaking.
>>> Waiting for user to speak again...

[User speaks again - conversation continues]
>>> Media event received - Session: {sessionId}, Track: inbound, Payload size: 160
... (cycle repeats) ...
```

## Key Changes

### TwilioTwiMLInjectionService.java
- Extended pause from 5 to 60 seconds in `injectSayAndContinueStream()`
- Better logging of stream URL and injection status

### TwilioMediaStreamHandler.java
- Enhanced logging with timestamps
- Better tracking of stream URL
- Clearer messages about conversation state

### OutboundCallService.java
- Comprehensive logging of outbound call initiation
- Clear visibility into call creation process

### TwilioVoiceController.java
- Logs when TwiML webhook is called
- Tracks call SID and status

### AiAgentService.java
- Timestamped user speech and AI response logging
- Integration with ConversationLogger (if available)

## Testing Outbound Calls

1. **Make an outbound call:**
   ```bash
   curl -X POST http://localhost:8080/twilio/outbound/call \
     -H "Content-Type: application/json" \
     -d '{"toNumber": "+1234567890", "fromNumber": "+18026590229"}'
   ```

2. **Answer the call** on the destination phone

3. **Watch the logs** for:
   - Call initiation
   - WebSocket connection
   - Media events when you speak
   - User speech logging
   - AI response logging
   - Stream continuation

4. **Speak into the phone** and verify:
   - Audio chunks are being buffered
   - After 1.5 seconds of silence, AI responds
   - AI response is logged
   - Stream continues for next turn

## Troubleshooting

### No Response After User Speaks

**Check logs for:**
1. `>>> Media event received` - Is audio being received?
2. `>>> Audio chunk buffered` - Is audio being buffered?
3. `>>> Processing buffered audio` - Is silence detection working?
4. `USER SPEECH` - Is transcription working (or test mode enabled)?
5. `AI RESPONSE` - Is AI generating a response?
6. `>>> Injecting TwiML` - Is TwiML being injected?
7. `>>> Successfully sent AI response` - Did injection succeed?

### Stream Not Continuing

**Check:**
- Stream URL is logged correctly
- `injectSayAndContinueStream` is being called (not fallback)
- TwiML injection is successful
- No errors in TwilioTwiMLInjectionService logs

### No Media Events

**Check:**
- WebSocket connection is established
- Stream is started in initial TwiML
- Call status is "answered"
- ngrok is running and accessible

## Configuration

Ensure these are set in `application.properties`:

```properties
# Test mode to see responses without STT
ai.agent.test.mode=true

# Callback URL for outbound calls (use ngrok)
twilio.callback.base.url=https://your-ngrok-url.ngrok.io

# Silence timeout
conversation.silence.timeout.ms=1500
```

## Next Steps

1. **Integrate STT**: Replace `transcribeAudio()` with actual speech-to-text
2. **Integrate AI**: Replace `generateAiResponse()` with actual AI service
3. **Monitor Logs**: Use the enhanced logging to track conversation flow
4. **Adjust Timing**: Fine-tune silence timeout and pause lengths as needed

