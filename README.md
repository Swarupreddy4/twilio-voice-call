# Twilio Voice AI Agent with WebSocket

A Spring Boot application that implements a Twilio Voice AI Agent using WebSocket connections for real-time audio streaming and AI-powered voice interactions, with optional Salesforce integration for contact-driven outbound calls and automatic Task logging.

## Features

- **Twilio Voice Integration**: Handles incoming voice calls via Twilio
- **WebSocket Media Streams**: Real-time bidirectional audio streaming
- **AI Agent Service**: Extensible service for processing audio and generating AI responses
- **Spring Boot**: Modern Java framework with WebSocket support

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- Twilio Account with:
  - Account SID
  - Auth Token
  - Phone Number (with Voice capabilities)
- Public URL for WebSocket (use ngrok for local development)

## Setup

### 1. Clone and Build

```bash
mvn clean install
```

### 2. Configure Twilio Credentials

Edit `src/main/resources/application.properties` or set environment variables:

```properties
twilio.account.sid=your_account_sid_here
twilio.auth.token=your_auth_token_here
twilio.phone.number=+1234567890
```

Or set environment variables:
```bash
export TWILIO_ACCOUNT_SID=your_account_sid
export TWILIO_AUTH_TOKEN=your_auth_token
export TWILIO_PHONE_NUMBER=+1234567890
```

### 3. Expose Your Application (for Local Development)

Since Twilio needs to connect to your WebSocket endpoint, you need a public URL. Use ngrok:

```bash
# Install ngrok: https://ngrok.com/download
ngrok http 8080
```

Note the HTTPS URL provided by ngrok (e.g., `https://abc123.ngrok.io`)

### 4. Update TwiML WebSocket URL

Update the `generateVoiceTwiML` method in `TwilioVoiceService.java` to use your ngrok URL or production domain:

```java
// For production, use your actual domain
String wsUrl = "wss://yourdomain.com/twilio/media-stream";
```

### 5. Configure Twilio Phone Number

You have two options:

#### Option A: Use REST Endpoint (Recommended for Development)

In your Twilio Console:
1. Go to Phone Numbers → Manage → Active Numbers
2. Select your phone number
3. Under "Voice & Fax", set the webhook URL to:
   ```
   https://your-ngrok-url.ngrok.io/twilio/voice
   ```
4. Set HTTP method to `POST`
5. Save the configuration

#### Option B: Use Twilio Bin (Alternative)

See the `twilio-bin/` directory for scripts to create and manage Twilio Bins:
- `create-bin.sh` / `create-bin.ps1` - Create a TwiML Bin
- `update-bin.sh` / `update-bin.ps1` - Update an existing bin
- `make-call.sh` / `make-call.ps1` - Make test calls

Quick start with Twilio Bin:
```bash
cd twilio-bin
export WEBSOCKET_URL="wss://your-ngrok-url.ngrok.io/twilio/media-stream"
export TWILIO_ACCOUNT_SID="your_account_sid"
export TWILIO_AUTH_TOKEN="your_auth_token"
./create-bin.sh
```

Then configure your phone number to use the bin URL: `https://handler.twilio.com/YOUR_BIN_SID`

### 6. Run the Application

```bash
mvn spring-boot:run
```

Or run the main class:
```bash
java -jar target/twilio-voice-ai-agent-1.0.0.jar
```

## Architecture

### Components

1. **TwilioVoiceController**: REST endpoints for Twilio callbacks
   - `/twilio/voice` - Handles incoming calls, returns TwiML
   - `/twilio/status` - Receives call status updates and (optionally) creates Salesforce Tasks when calls complete

2. **TwilioMediaStreamHandler**: WebSocket handler for media streams
   - Handles WebSocket connections from Twilio
   - Processes audio events
   - Manages session lifecycle

3. **AiAgentService**: AI processing service
   - Converts mu-law audio to PCM
   - Placeholder for speech-to-text integration
   - Placeholder for AI response generation

4. **WebSocket Configuration**: Configures WebSocket endpoints
   - `/twilio/media-stream` - WebSocket endpoint for Twilio

## Extending the AI Agent

The `AiAgentService` class contains placeholder methods that you can integrate with:

### Speech-to-Text Integration

Update the `transcribeAudio` method to integrate with:
- OpenAI Whisper API
- Google Cloud Speech-to-Text
- AWS Transcribe
- Azure Speech Services

Example with OpenAI Whisper:
```java
private String transcribeAudio(byte[] pcmAudio) {
    // Use OpenAI Whisper API or similar service
    // Return transcribed text
}
```

### AI Response Generation

Update the `generateAiResponse` method to integrate with:
- OpenAI GPT-4
- Anthropic Claude
- Google Gemini
- Custom LLM

Example:
```java
private String generateAiResponse(String userInput, String sessionId) {
    // Call your AI service
    // Return text response
}
```

### Text-to-Speech

To send audio responses back to the caller, you'll need to:
1. Convert text response to audio (using TTS service)
2. Encode audio to mu-law format
3. Send via WebSocket in Twilio Media Stream format

## API Endpoints

### POST /twilio/voice
Twilio webhook endpoint that returns TwiML to start the media stream (for both inbound and outbound calls).

**Response**: TwiML XML

**Query Parameters** (optional):
- `message`: Custom greeting message

### POST /twilio/outbound/call
Make an outbound call with AI voice agent.

#### A. Direct phone‑driven call

**Request Body**:
```json
{
  "toNumber": "+1234567890",
  "fromNumber": "+18026590229",
  "customMessage": "Hello! This is a test call."
}
```

#### B. Salesforce contact‑driven call

If you pass a Salesforce `contactId` (and optional `accountId`), the application will:

- Fetch the Contact from Salesforce
- Use `MobilePhone` (or `Phone` as fallback) as the destination number
- Build a greeting from the Contact name and `Description` as the `customMessage`

**Request Body**:
```json
{
  "contactId": "003XXXXXXXXXXXXXXX",
  "accountId": "001XXXXXXXXXXXXXXX",
  "fromNumber": "+18026590229"
}
```

**Response** (both variants):
```json
{
  "callSid": "CAxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "status": "queued",
  "message": "Call initiated successfully",
  "toNumber": "+1234567890",
  "fromNumber": "+18026590229"
}
```

### GET /twilio/call/{callSid}/status
Get the status of a call by Call SID.

**Response**: Call object with status information

### POST /twilio/status
Receives call status updates from Twilio.

**Parameters**:
- `CallSid`: Unique call identifier
- `CallStatus`: Current call status

When a call that was initiated with a `contactId` completes (`CallStatus=completed`), the application:

- Retrieves the full conversation log (user speech + AI responses) from the in‑memory `ConversationLogger`
- Creates a Salesforce `Task` via `SalesforceService.createCallTaskForContactAndAccount`:
  - `WhoId` = Contact
  - `WhatId` = Account (if provided)
  - `Subject` = `AI Voice Call - <CallStatus>`
  - `Status` = `Completed`
  - `TaskSubtype` = `Call`, `CallType` = `Outbound`
  - `CallObject` = `CallSid`, `Phone` = dialed number
  - `Description` = formatted conversation log (user + AI messages)

### WebSocket /twilio/media-stream
WebSocket endpoint for Twilio Media Streams.

**Events**:
- `connected`: Connection established
- `start`: Stream started
- `media`: Audio data received
- `stop`: Stream stopped

## Making Outbound Calls

### Using the REST API

```bash
curl -X POST http://localhost:8080/twilio/outbound/call \
  -H "Content-Type: application/json" \
  -d '{
    "toNumber": "+1234567890",
    "fromNumber": "+18026590229",
    "customMessage": "Hello! This is a test call from our AI agent."
  }'
```

### Using the Scripts

**Windows (PowerShell):**
```powershell
cd twilio-bin
.\make-outbound-call.ps1 -ToNumber "+1234567890" -FromNumber "+18026590229" -CustomMessage "Hello!"
```

**Linux/Mac (Bash):**
```bash
cd twilio-bin
chmod +x make-outbound-call.sh
./make-outbound-call.sh +1234567890 +18026590229 "Hello!"
```

### Using Java Code

```java
@Autowired
private OutboundCallService outboundCallService;

public void makeCall() {
    OutboundCallRequest request = new OutboundCallRequest(
        "+1234567890",
        "+18026590229",
        "Hello! This is a test call."
    );
    
    OutboundCallResponse response = outboundCallService.makeOutboundCall(request);
    System.out.println("Call SID: " + response.getCallSid());
}
```

### Important Configuration

For outbound calls to work, you need to set the callback base URL in `application.properties`:

```properties
# For local development with ngrok
twilio.callback.base.url=https://your-ngrok-url.ngrok.io

# For production
twilio.callback.base.url=https://yourdomain.com
```

This URL is used by Twilio to fetch the TwiML when the outbound call is answered.

## Testing

### Test with Twilio CLI

```bash
# Install Twilio CLI: https://www.twilio.com/docs/twilio-cli
twilio phone-numbers:update +1234567890 \
  --voice-url https://your-ngrok-url.ngrok.io/twilio/voice \
  --voice-method POST
```

### Make a Test Call

Call your Twilio phone number and the application will:
1. Answer the call
2. Establish WebSocket connection
3. Stream audio bidirectionally
4. Process audio through AI agent (when implemented)

## Troubleshooting

### WebSocket Connection Issues

- Ensure your ngrok URL is HTTPS (required for wss://)
- Check that the WebSocket endpoint is accessible publicly
- Verify CORS settings if needed

### Audio Processing Issues

- Twilio sends audio in mu-law format (8-bit, 8000 Hz)
- Ensure proper audio format conversion in `AiAgentService`
- Check logs for audio processing errors

### TwiML Issues

- Verify the WebSocket URL in TwiML is correct
- Ensure the URL uses `wss://` protocol
- Check that the endpoint path matches your configuration

## Production Deployment

1. Deploy to a cloud provider (AWS, GCP, Azure, etc.)
2. Use a proper domain with SSL certificate
3. Update WebSocket URL in `TwilioVoiceService`
4. Configure environment variables securely
5. Set up monitoring and logging
6. Implement proper error handling and retries

## License

This project is provided as-is for educational and development purposes.

## Resources

- [Twilio Media Streams Documentation](https://www.twilio.com/docs/voice/twiml/stream)
- [Spring WebSocket Documentation](https://docs.spring.io/spring-framework/reference/web/websocket.html)
- [Twilio Java SDK](https://www.twilio.com/docs/libraries/java)


