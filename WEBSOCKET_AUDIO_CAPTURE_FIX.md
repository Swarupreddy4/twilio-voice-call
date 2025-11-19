# WebSocket Audio Capture Fix

## Problem
User responses (audio) are not being captured by the WebSocket connection.

## Enhanced Logging

I've added comprehensive logging to help diagnose the issue:

### 1. Media Event Logging
- **INFO level** logging when media events are received
- Logs session ID, track type, and payload size
- Logs when audio chunks are buffered with statistics

### 2. WebSocket Message Logging
- Logs all incoming WebSocket messages
- Logs event types being processed
- Logs errors with message payloads

### 3. Audio Buffer Statistics
- Tracks number of chunks buffered
- Tracks total bytes buffered
- Helps identify if audio is being received but not processed

## What to Check in Logs

When you make a call, you should see:

1. **Connection Events:**
   ```
   >>> Connected event received for session: {id}
   >>> WebSocket protocol: {protocol}
   ```

2. **Start Event:**
   ```
   Start event received for session: {id}
   >>> Conversation started - Call SID: {callSid}, Session: {id}
   ```

3. **Media Events (when user speaks):**
   ```
   >>> Media event received - Session: {id}, Track: inbound, Payload size: {bytes}
   >>> Audio chunk buffered - Session: {id}, Size: {bytes}, Chunks: {count}, Total: {total} bytes
   ```

4. **Processing:**
   ```
   >>> Processing buffered audio for session {id} ({bytes} bytes)
   ```

## Common Issues and Solutions

### Issue 1: No Media Events Received

**Symptoms:**
- You see "Connected" and "Start" events
- But no "Media event received" messages when user speaks

**Possible Causes:**
1. **TwiML Stream Configuration**: The Stream might not be configured to send audio
2. **WebSocket URL**: The WebSocket URL might be incorrect
3. **Network/Firewall**: ngrok or network might be blocking the connection

**Solutions:**
- Verify the WebSocket URL in TwiML is correct (must use `wss://`)
- Check ngrok is running and accessible
- Verify Twilio can reach your WebSocket endpoint

### Issue 2: Media Events with Empty Payload

**Symptoms:**
- You see "Media event received" but payload is empty
- Or "Media event received with empty payload"

**Possible Causes:**
1. Audio not being sent from Twilio
2. Stream not properly initialized

**Solutions:**
- Ensure the Stream is started before the user speaks
- Check that the call is in the correct state
- Verify Twilio account has Media Streams enabled

### Issue 3: Audio Chunks Not Being Buffered

**Symptoms:**
- Media events received with payload
- But "No audio buffer found" warning

**Possible Causes:**
- Session cleanup happened too early
- Buffer not initialized properly

**Solutions:**
- Check that `afterConnectionEstablished` is being called
- Verify session ID is consistent

## Testing Steps

1. **Start the application** and check logs for WebSocket initialization

2. **Make a call** to your Twilio number

3. **Watch for these log messages:**
   - `>>> Connected event received`
   - `Start event received`
   - `>>> Conversation started`

4. **Speak into the phone** and watch for:
   - `>>> Media event received`
   - `>>> Audio chunk buffered`

5. **Wait 1.5 seconds** after speaking and watch for:
   - `>>> Processing buffered audio`
   - `USER SPEECH` (if STT is integrated)
   - `AI RESPONSE`

## Debugging Commands

### Check if WebSocket is receiving messages:
Look for any log entries containing:
- `Received WebSocket message`
- `Media event received`
- `Audio chunk buffered`

### Check if audio is being processed:
Look for:
- `Processing buffered audio`
- `USER SPEECH`
- `AI RESPONSE`

### Check for errors:
Look for:
- `Error handling WebSocket message`
- `Failed to decode base64 audio payload`
- `No audio buffer found`

## Configuration Check

Verify in `application.properties`:
```properties
# WebSocket should be enabled
websocket.allowed.origins=*

# Silence timeout (adjust if needed)
conversation.silence.timeout.ms=1500

# Test mode (to see responses even without STT)
ai.agent.test.mode=true
```

## Next Steps

1. **Check the logs** when making a call
2. **Identify which step is failing**:
   - No WebSocket connection?
   - No media events?
   - Media events but empty payload?
   - Audio buffered but not processed?

3. **Share the log output** to identify the specific issue

## Expected Log Flow

```
[INFO] WebSocket connection established: {sessionId}
[INFO] >>> Connected event received for session: {sessionId}
[INFO] Start event received for session: {sessionId}
[INFO] >>> Conversation started - Call SID: {callSid}, Session: {sessionId}
[INFO] >>> Media event received - Session: {sessionId}, Track: inbound, Payload size: 160
[INFO] >>> Audio chunk buffered - Session: {sessionId}, Size: 160 bytes, Chunks: 1, Total: 160 bytes
[INFO] >>> Media event received - Session: {sessionId}, Track: inbound, Payload size: 160
[INFO] >>> Audio chunk buffered - Session: {sessionId}, Size: 160 bytes, Chunks: 2, Total: 320 bytes
... (more chunks as user speaks) ...
[INFO] >>> Processing buffered audio for session {sessionId} (3200 bytes)
[INFO] ================================================================================
[INFO] USER SPEECH [Session: {sessionId}]: {transcribed text}
[INFO] ================================================================================
[INFO] --------------------------------------------------------------------------------
[INFO] AI RESPONSE [Session: {sessionId}]: {ai response}
[INFO] --------------------------------------------------------------------------------
```

