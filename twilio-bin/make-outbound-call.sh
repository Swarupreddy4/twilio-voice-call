#!/bin/bash

# Make an outbound call using the Spring Boot API
# Usage: ./make-outbound-call.sh TO_PHONE_NUMBER [FROM_PHONE_NUMBER] [CUSTOM_MESSAGE] [API_BASE_URL]

TO_NUMBER=${1}
FROM_NUMBER=${2:-$TWILIO_PHONE_NUMBER}
CUSTOM_MESSAGE=${3:-""}
API_BASE_URL=${4:-"http://localhost:8080"}

if [ -z "$TO_NUMBER" ]; then
    echo "Usage: ./make-outbound-call.sh TO_PHONE_NUMBER [FROM_PHONE_NUMBER] [CUSTOM_MESSAGE] [API_BASE_URL]"
    echo "Example: ./make-outbound-call.sh +1234567890 +18026590229 \"Hello! This is a test call.\" http://localhost:8080"
    exit 1
fi

# Build JSON payload
if [ -z "$CUSTOM_MESSAGE" ]; then
    JSON_PAYLOAD="{\"toNumber\":\"$TO_NUMBER\",\"fromNumber\":\"$FROM_NUMBER\"}"
else
    JSON_PAYLOAD="{\"toNumber\":\"$TO_NUMBER\",\"fromNumber\":\"$FROM_NUMBER\",\"customMessage\":\"$CUSTOM_MESSAGE\"}"
fi

echo "Making outbound call..."
echo "To: $TO_NUMBER"
echo "From: $FROM_NUMBER"
if [ -n "$CUSTOM_MESSAGE" ]; then
    echo "Message: $CUSTOM_MESSAGE"
fi
echo ""

RESPONSE=$(curl -X POST "$API_BASE_URL/twilio/outbound/call" \
    -H "Content-Type: application/json" \
    -d "$JSON_PAYLOAD")

echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"

CALL_SID=$(echo "$RESPONSE" | grep -o '"callSid":"[^"]*"' | cut -d'"' -f4)

if [ -n "$CALL_SID" ]; then
    echo ""
    echo "✅ Outbound call initiated successfully!"
    echo "Call SID: $CALL_SID"
    echo "Monitor the call: https://console.twilio.com/us1/monitor/logs/calls/$CALL_SID"
    echo ""
    echo "Check call status: curl $API_BASE_URL/twilio/call/$CALL_SID/status"
else
    echo "❌ Failed to initiate outbound call"
    exit 1
fi

