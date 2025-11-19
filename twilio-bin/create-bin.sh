#!/bin/bash

# Twilio Bin Creation Script
# This script creates a TwiML Bin in your Twilio account
# Usage: ./create-bin.sh YOUR_ACCOUNT_SID YOUR_AUTH_TOKEN YOUR_WEBSOCKET_URL

ACCOUNT_SID=${1:-$TWILIO_ACCOUNT_SID}
AUTH_TOKEN=${2:-$TWILIO_AUTH_TOKEN}
WEBSOCKET_URL=${3:-$WEBSOCKET_URL}

if [ -z "$ACCOUNT_SID" ] || [ -z "$AUTH_TOKEN" ] || [ -z "$WEBSOCKET_URL" ]; then
    echo "Usage: ./create-bin.sh ACCOUNT_SID AUTH_TOKEN WEBSOCKET_URL"
    echo "Or set environment variables: TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, WEBSOCKET_URL"
    exit 1
fi

# Create TwiML content with WebSocket URL
TwiML="<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Start><Stream url=\"$WEBSOCKET_URL\" /></Start><Say voice=\"alice\">Hello! I'm your AI assistant. How can I help you today?</Say><Pause length=\"30\" /></Response>"

# Create the TwiML Bin
echo "Creating TwiML Bin..."
RESPONSE=$(curl -X POST "https://twilio.bins.twilio.com/v1/Bins" \
    -u "$ACCOUNT_SID:$AUTH_TOKEN" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "FriendlyName=Voice WebSocket AI Agent" \
    -d "TwiML=$TwiML")

echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"

BIN_SID=$(echo "$RESPONSE" | grep -o '"sid":"[^"]*"' | cut -d'"' -f4)

if [ -n "$BIN_SID" ]; then
    echo ""
    echo "✅ TwiML Bin created successfully!"
    echo "Bin SID: $BIN_SID"
    echo "Bin URL: https://handler.twilio.com/$BIN_SID"
    echo ""
    echo "Use this URL in your Twilio phone number configuration:"
    echo "https://handler.twilio.com/$BIN_SID"
else
    echo "❌ Failed to create TwiML Bin"
    exit 1
fi

