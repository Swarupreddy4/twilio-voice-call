# AI Response Playback Fix

## Problem
AI-generated responses were not being played back to the caller after the user spoke.

## Root Causes
1. **No Speech-to-Text Integration**: The `transcribeAudio()` method returned `null`, so no text was being transcribed from the audio
2. **Incorrect Response Method**: The `sendMediaResponse()` method was trying to send text as audio data through the WebSocket, which doesn't work for playback
3. **Missing Call SID Tracking**: The Call SID wasn't being stored when the stream started, so responses couldn't be injected into the call

## Solution Implemented

### 1. Created TwilioTwiMLInjectionService
- New service that uses Twilio's REST API to inject TwiML into active calls
- Uses `Call.updater()` to update the call with new TwiML containing `<Say>` verb
- Supports two modes:
  - `injectSayIntoCall()`: Just speaks the message
  - `injectSayAndContinueStream()`: Speaks the message and continues the WebSocket stream

### 2. Updated TwilioMediaStreamHandler
- **Stores Call SID**: Now captures and stores the Call SID from the `start` event
- **Stores Stream URL**: Captures the stream URL to reconnect after speaking
- **New Response Method**: `sendAiResponse()` uses TwiML injection instead of trying to send audio through WebSocket
- **Debouncing**: Added 3-second minimum interval between responses to prevent spam

### 3. Enhanced AiAgentService
- **Test Mode**: Added `ai.agent.test.mode` configuration option
- When test mode is enabled, generates a test response even without STT
- Allows testing the response playback mechanism before integrating actual STT

## Configuration

In `application.properties`:
```properties
# Enable test mode to test response playback without STT
ai.agent.test.mode=true
```

## How It Works Now

1. **Call Starts**: WebSocket connection established, Call SID is stored
2. **User Speaks**: Audio data received through WebSocket
3. **Audio Processing**: 
   - If test mode is ON: Generates test response
   - If STT is integrated: Transcribes audio, generates AI response
4. **Response Playback**: 
   - Retrieves Call SID from session
   - Uses Twilio REST API to inject TwiML with `<Say>` verb
   - Message is spoken to the caller
   - Stream continues after speaking (if configured)

## Testing

1. **Enable Test Mode**: Set `ai.agent.test.mode=true` in `application.properties`
2. **Make a Call**: Call your Twilio number
3. **Speak**: Say anything into the phone
4. **Expected Result**: You should hear "I received your audio. This is a test response..."

## Next Steps

To enable real AI responses:

1. **Integrate Speech-to-Text**:
   - Update `transcribeAudio()` in `AiAgentService.java`
   - Integrate with OpenAI Whisper, Google Speech-to-Text, AWS Transcribe, or Azure Speech Services

2. **Integrate AI Service**:
   - Update `generateAiResponse()` in `AiAgentService.java`
   - Integrate with OpenAI GPT, Anthropic Claude, Google Gemini, etc.

3. **Disable Test Mode**:
   - Set `ai.agent.test.mode=false` once STT is integrated

## Files Modified

- `src/main/java/com/example/twilio/service/TwilioTwiMLInjectionService.java` (NEW)
- `src/main/java/com/example/twilio/websocket/TwilioMediaStreamHandler.java` (UPDATED)
- `src/main/java/com/example/twilio/service/AiAgentService.java` (UPDATED)
- `src/main/resources/application.properties` (UPDATED)

## Troubleshooting

### Responses Still Not Playing

1. **Check Logs**: Look for "Sending AI response to call" and "Successfully injected TwiML"
2. **Verify Call SID**: Ensure Call SID is being captured (check "Stored Call SID" in logs)
3. **Check Twilio Credentials**: Verify `twilio.account.sid` and `twilio.auth.token` are correct
4. **Test Mode**: Enable test mode to verify the mechanism works

### Too Many Responses

- The debouncing mechanism prevents responses more frequent than every 3 seconds
- Adjust `MIN_RESPONSE_INTERVAL_MS` in `TwilioMediaStreamHandler.java` if needed

### Call SID Not Found

- Ensure the `start` event is being received
- Check that the WebSocket connection is properly established
- Verify the TwiML includes the `<Stream>` verb

