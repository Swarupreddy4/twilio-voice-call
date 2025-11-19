#!/bin/bash

# Make a test call using Twilio Bin
# Usage: ./make-call.sh TO_PHONE_NUMBER BIN_SID ACCOUNT_SID AUTH_TOKEN FROM_PHONE_NUMBER

TO_NUMBER=${1}
BIN_SID=${2:-$TWILIO_BIN_SID}
ACCOUNT_SID=${3:-$TWILIO_ACCOUNT_SID}
AUTH_TOKEN=${4:-$TWILIO_AUTH_TOKEN}
FROM_NUMBER=${5:-$TWILIO_PHONE_NUMBER}

if [ -z "$TO_NUMBER" ] || [ -z "$BIN_SID" ] || [ -z "$ACCOUNT_SID" ] || [ -z "$AUTH_TOKEN" ] || [ -z "$FROM_NUMBER" ]; then
    echo "Usage: ./make-call.sh TO_PHONE_NUMBER [BIN_SID] [ACCOUNT_SID] [AUTH_TOKEN] [FROM_PHONE_NUMBER]"
    echo "Or set environment variables: TWILIO_BIN_SID, TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_PHONE_NUMBER"
    exit 1
fi

echo "Making call from $FROM_NUMBER to $TO_NUMBER using Bin $BIN_SID..."

RESPONSE=$(curl -X POST "https://api.twilio.com/2010-04-01/Accounts/$ACCOUNT_SID/Calls.json" \
    -u "$ACCOUNT_SID:$AUTH_TOKEN" \
    -d "From=$FROM_NUMBER" \
    -d "To=$TO_NUMBER" \
    -d "Url=https://handler.twilio.com/$BIN_SID")

echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"

CALL_SID=$(echo "$RESPONSE" | grep -o '"sid":"[^"]*"' | cut -d'"' -f4)

if [ -n "$CALL_SID" ]; then
    echo ""
    echo "✅ Call initiated successfully!"
    echo "Call SID: $CALL_SID"
    echo "Monitor the call in Twilio Console: https://console.twilio.com/us1/monitor/logs/calls/$CALL_SID"
else
    echo "❌ Failed to initiate call"
    exit 1
fi

