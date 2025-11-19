#!/bin/bash

# Twilio Bin Update Script
# This script updates an existing TwiML Bin with a new WebSocket URL
# Usage: ./update-bin.sh BIN_SID ACCOUNT_SID AUTH_TOKEN WEBSOCKET_URL

BIN_SID=${1:-$TWILIO_BIN_SID}
ACCOUNT_SID=${2:-$TWILIO_ACCOUNT_SID}
AUTH_TOKEN=${3:-$TWILIO_AUTH_TOKEN}
WEBSOCKET_URL=${4:-$WEBSOCKET_URL}

if [ -z "$BIN_SID" ] || [ -z "$ACCOUNT_SID" ] || [ -z "$AUTH_TOKEN" ] || [ -z "$WEBSOCKET_URL" ]; then
    echo "Usage: ./update-bin.sh BIN_SID ACCOUNT_SID AUTH_TOKEN WEBSOCKET_URL"
    echo "Or set environment variables: TWILIO_BIN_SID, TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, WEBSOCKET_URL"
    exit 1
fi

# Create TwiML content with WebSocket URL
TwiML="<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Start><Stream url=\"$WEBSOCKET_URL\" /></Start><Say voice=\"alice\">Hello! I'm your AI assistant. How can I help you today?</Say><Pause length=\"30\" /></Response>"

# Update the TwiML Bin
echo "Updating TwiML Bin $BIN_SID..."
RESPONSE=$(curl -X POST "https://twilio.bins.twilio.com/v1/Bins/$BIN_SID" \
    -u "$ACCOUNT_SID:$AUTH_TOKEN" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "TwiML=$TwiML")

echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"

echo ""
echo "✅ TwiML Bin updated successfully!"
echo "Bin URL: https://handler.twilio.com/$BIN_SID"

