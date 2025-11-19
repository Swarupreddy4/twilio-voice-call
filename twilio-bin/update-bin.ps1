# Twilio Bin Update Script for PowerShell
# This script updates an existing TwiML Bin with a new WebSocket URL
# Usage: .\update-bin.ps1 -BinSid BIN_SID -AccountSid ACCOUNT_SID -AuthToken AUTH_TOKEN -WebSocketUrl WEBSOCKET_URL

param(
    [Parameter(Mandatory=$false)]
    [string]$BinSid = $env:TWILIO_BIN_SID,
    
    [Parameter(Mandatory=$false)]
    [string]$AccountSid = $env:TWILIO_ACCOUNT_SID,
    
    [Parameter(Mandatory=$false)]
    [string]$AuthToken = $env:TWILIO_AUTH_TOKEN,
    
    [Parameter(Mandatory=$false)]
    [string]$WebSocketUrl = $env:WEBSOCKET_URL
)

if (-not $BinSid -or -not $AccountSid -or -not $AuthToken -or -not $WebSocketUrl) {
    Write-Host "Usage: .\update-bin.ps1 -BinSid BIN_SID -AccountSid ACCOUNT_SID -AuthToken AUTH_TOKEN -WebSocketUrl WEBSOCKET_URL" -ForegroundColor Yellow
    Write-Host "Or set environment variables: TWILIO_BIN_SID, TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, WEBSOCKET_URL" -ForegroundColor Yellow
    exit 1
}

# Create TwiML content with WebSocket URL
$TwiML = "<?xml version=`"1.0`" encoding=`"UTF-8`"?><Response><Start><Stream url=`"$WebSocketUrl`" /></Start><Say voice=`"alice`">Hello! I'm your AI assistant. How can I help you today?</Say><Pause length=`"30`" /></Response>"

# Create base64 encoded credentials
$base64Auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${AccountSid}:${AuthToken}"))

# Update the TwiML Bin
Write-Host "Updating TwiML Bin $BinSid..." -ForegroundColor Cyan

$body = @{
    TwiML = $TwiML
}

$headers = @{
    Authorization = "Basic $base64Auth"
    "Content-Type" = "application/x-www-form-urlencoded"
}

try {
    $response = Invoke-RestMethod -Uri "https://twilio.bins.twilio.com/v1/Bins/$BinSid" `
        -Method Post `
        -Headers $headers `
        -Body $body

    Write-Host ""
    Write-Host "✅ TwiML Bin updated successfully!" -ForegroundColor Green
    Write-Host "Bin URL: https://handler.twilio.com/$BinSid" -ForegroundColor Green
} catch {
    Write-Host "❌ Failed to update TwiML Bin" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}

