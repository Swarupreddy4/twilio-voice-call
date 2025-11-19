# Twilio Bin Scripts

This directory contains scripts and files to create and manage Twilio Bins for the Voice WebSocket AI Agent.

## What is a Twilio Bin?

A Twilio Bin is a pre-configured TwiML script stored in Twilio that can be referenced by a URL. This is useful when you want to use a static TwiML configuration without hosting your own endpoint.

## Files

### TwiML Bins
- `voice-websocket-bin.xml` - The TwiML template for inbound calls with WebSocket connection
- `outbound-call-bin.xml` - The TwiML template for outbound calls with WebSocket connection

### Scripts
- `create-bin.sh` / `create-bin.ps1` - Create a TwiML Bin (Linux/Mac/Windows)
- `update-bin.sh` / `update-bin.ps1` - Update an existing TwiML Bin
- `make-call.sh` / `make-call.ps1` - Make a call using Twilio Bin (direct Twilio API)
- `make-outbound-call.sh` / `make-outbound-call.ps1` - Make an outbound call using Spring Boot API

### Examples
- `create-bin.java` - Java class example for programmatic bin creation

## Quick Start

### Option 1: Use the REST API Endpoint (Recommended)

Instead of using a Twilio Bin, you can use the Spring Boot endpoint directly:

1. Start your Spring Boot application
2. Use ngrok to expose it: `ngrok http 8080`
3. Configure your Twilio phone number webhook to:
   ```
   https://your-ngrok-url.ngrok.io/twilio/voice
   ```
   Method: POST

### Option 2: Create a Twilio Bin

#### Using Bash Script (Linux/Mac)

```bash
cd twilio-bin
chmod +x create-bin.sh

# Set your WebSocket URL (use ngrok or your production domain)
export WEBSOCKET_URL="wss://your-ngrok-url.ngrok.io/twilio/media-stream"
export TWILIO_ACCOUNT_SID="your_account_sid"
export TWILIO_AUTH_TOKEN="your_auth_token"

./create-bin.sh
```

Or with parameters:
```bash
./create-bin.sh ACCOUNT_SID AUTH_TOKEN wss://your-domain.com/twilio/media-stream
```

#### Using PowerShell Script (Windows)

```powershell
cd twilio-bin

# Set your WebSocket URL
$env:WEBSOCKET_URL = "wss://your-ngrok-url.ngrok.io/twilio/media-stream"
$env:TWILIO_ACCOUNT_SID = "your_account_sid"
$env:TWILIO_AUTH_TOKEN = "your_auth_token"

.\create-bin.ps1
```

Or with parameters:
```powershell
.\create-bin.ps1 -AccountSid "ACxxx" -AuthToken "xxx" -WebSocketUrl "wss://your-domain.com/twilio/media-stream"
```

#### Manual Creation via Twilio Console

1. Go to [Twilio Console → Runtime → TwiML Bins](https://console.twilio.com/us1/develop/runtime/twiml-bins)
2. Click "Create new TwiML Bin"
3. Set Friendly Name: "Voice WebSocket AI Agent"
4. Paste the TwiML from `voice-websocket-bin.xml`, replacing `YOUR_DOMAIN_HERE` with your actual WebSocket URL:
   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <Response>
       <Start>
           <Stream url="wss://your-ngrok-url.ngrok.io/twilio/media-stream" />
       </Start>
       <Say voice="alice">Hello! I'm your AI assistant. How can I help you today?</Say>
       <Pause length="30" />
   </Response>
   ```
5. Save the bin
6. Copy the Bin URL (format: `https://handler.twilio.com/BIN_SID`)

## Using the Twilio Bin

### Configure Phone Number

1. Go to [Twilio Console → Phone Numbers → Manage → Active Numbers](https://console.twilio.com/us1/develop/phone-numbers/manage/incoming)
2. Click on your phone number
3. Under "Voice & Fax", set:
   - **A CALL COMES IN**: Select "TwiML Bin"
   - Choose your bin from the dropdown
   - Or paste the bin URL: `https://handler.twilio.com/YOUR_BIN_SID`
4. Save

### Make a Test Call

Call your Twilio phone number. The call will:
1. Answer automatically
2. Connect to your WebSocket endpoint
3. Stream audio bidirectionally
4. Process through your AI agent

## WebSocket URL Format

The WebSocket URL must:
- Use `wss://` protocol (secure WebSocket)
- Be publicly accessible
- Point to your `/twilio/media-stream` endpoint

Examples:
- Local development with ngrok: `wss://abc123.ngrok.io/twilio/media-stream`
- Production: `wss://yourdomain.com/twilio/media-stream`

## Updating the Bin

If you need to update the WebSocket URL:

1. Edit the bin in Twilio Console, or
2. Delete and recreate using the scripts, or
3. Use the Twilio REST API to update the bin

## Troubleshooting

### WebSocket Connection Fails

- Ensure your WebSocket URL uses `wss://` (not `ws://`)
- Verify the endpoint is publicly accessible
- Check that your Spring Boot app is running
- Verify the WebSocket handler is properly configured

### Bin Not Working

- Check the bin URL is correct in phone number configuration
- Verify the TwiML syntax is valid
- Ensure the WebSocket URL in the bin is correct
- Check Twilio logs in the console for errors

## Making Outbound Calls

### Using Spring Boot API (Recommended)

The Spring Boot application provides a REST API endpoint to make outbound calls:

```bash
# Using curl
curl -X POST http://localhost:8080/twilio/outbound/call \
  -H "Content-Type: application/json" \
  -d '{
    "toNumber": "+1234567890",
    "fromNumber": "+18026590229",
    "customMessage": "Hello! This is a test call."
  }'
```

Or use the provided scripts:

**Bash (Linux/Mac):**
```bash
cd twilio-bin
chmod +x make-outbound-call.sh
./make-outbound-call.sh +1234567890 +18026590229 "Hello! This is a test call."
```

**PowerShell (Windows):**
```powershell
cd twilio-bin
.\make-outbound-call.ps1 -ToNumber "+1234567890" -FromNumber "+18026590229" -CustomMessage "Hello! This is a test call."
```

### Using Twilio Bin Directly

You can also make outbound calls directly using Twilio API with a bin:

```bash
./make-call.sh +1234567890 BIN_SID ACCOUNT_SID AUTH_TOKEN +18026590229
```

## Alternative: Use REST Endpoint

Instead of using a Twilio Bin, you can configure your phone number to call your Spring Boot REST endpoint directly:

```
https://your-domain.com/twilio/voice
```

This is more flexible as you can dynamically generate TwiML based on request parameters.

For outbound calls, use the Spring Boot API endpoint:
```
POST https://your-domain.com/twilio/outbound/call
```

