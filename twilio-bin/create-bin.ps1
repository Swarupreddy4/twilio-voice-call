# Twilio Bin Creation Script for PowerShell
# This script creates a TwiML Bin in your Twilio account
# Usage: .\create-bin.ps1 -AccountSid YOUR_ACCOUNT_SID -AuthToken YOUR_AUTH_TOKEN -WebSocketUrl YOUR_WEBSOCKET_URL

param(
    [Parameter(Mandatory=$false)]
    [string]$AccountSid = $env:TWILIO_ACCOUNT_SID,
    
    [Parameter(Mandatory=$false)]
    [string]$AuthToken = $env:TWILIO_AUTH_TOKEN,
    
    [Parameter(Mandatory=$false)]
    [string]$WebSocketUrl = $env:WEBSOCKET_URL
)

if (-not $AccountSid -or -not $AuthToken -or -not $WebSocketUrl) {
    Write-Host "Usage: .\create-bin.ps1 -AccountSid YOUR_ACCOUNT_SID -AuthToken YOUR_AUTH_TOKEN -WebSocketUrl YOUR_WEBSOCKET_URL" -ForegroundColor Yellow
    Write-Host "Or set environment variables: TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, WEBSOCKET_URL" -ForegroundColor Yellow
    exit 1
}

# Create TwiML content with WebSocket URL
$TwiML = "<?xml version=`"1.0`" encoding=`"UTF-8`"?><Response><Start><Stream url=`"$WebSocketUrl`" /></Start><Say voice=`"alice`">Hello! I'm your AI assistant. How can I help you today?</Say><Pause length=`"30`" /></Response>"

# Create base64 encoded credentials
$base64Auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${AccountSid}:${AuthToken}"))

# Create the TwiML Bin
Write-Host "Creating TwiML Bin..." -ForegroundColor Cyan

$body = @{
    FriendlyName = "Voice WebSocket AI Agent"
    TwiML = $TwiML
}

$headers = @{
    Authorization = "Basic $base64Auth"
    "Content-Type" = "application/x-www-form-urlencoded"
}

try {
    $response = Invoke-RestMethod -Uri "https://twilio.bins.twilio.com/v1/Bins" `
        -Method Post `
        -Headers $headers `
        -Body $body

    Write-Host ""
    Write-Host "✅ TwiML Bin created successfully!" -ForegroundColor Green
    Write-Host "Bin SID: $($response.sid)" -ForegroundColor Green
    Write-Host "Bin URL: https://handler.twilio.com/$($response.sid)" -ForegroundColor Green
    Write-Host ""
    Write-Host "Use this URL in your Twilio phone number configuration:" -ForegroundColor Yellow
    Write-Host "https://handler.twilio.com/$($response.sid)" -ForegroundColor White
} catch {
    Write-Host "❌ Failed to create TwiML Bin" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}

