# Make a test call using Twilio Bin (PowerShell)
# Usage: .\make-call.ps1 -ToNumber "+1234567890" -BinSid "BIN_SID" -AccountSid "ACCOUNT_SID" -AuthToken "AUTH_TOKEN" -FromNumber "+1234567890"

param(
    [Parameter(Mandatory=$true)]
    [string]$ToNumber,
    
    [Parameter(Mandatory=$false)]
    [string]$BinSid = $env:TWILIO_BIN_SID,
    
    [Parameter(Mandatory=$false)]
    [string]$AccountSid = $env:TWILIO_ACCOUNT_SID,
    
    [Parameter(Mandatory=$false)]
    [string]$AuthToken = $env:TWILIO_AUTH_TOKEN,
    
    [Parameter(Mandatory=$false)]
    [string]$FromNumber = $env:TWILIO_PHONE_NUMBER
)

if (-not $BinSid -or -not $AccountSid -or -not $AuthToken -or -not $FromNumber) {
    Write-Host "Usage: .\make-call.ps1 -ToNumber `"+1234567890`" -BinSid BIN_SID -AccountSid ACCOUNT_SID -AuthToken AUTH_TOKEN -FromNumber `"+1234567890`"" -ForegroundColor Yellow
    Write-Host "Or set environment variables: TWILIO_BIN_SID, TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_PHONE_NUMBER" -ForegroundColor Yellow
    exit 1
}

# Create base64 encoded credentials
$base64Auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${AccountSid}:${AuthToken}"))

Write-Host "Making call from $FromNumber to $ToNumber using Bin $BinSid..." -ForegroundColor Cyan

$body = @{
    From = $FromNumber
    To = $ToNumber
    Url = "https://handler.twilio.com/$BinSid"
}

$headers = @{
    Authorization = "Basic $base64Auth"
}

try {
    $response = Invoke-RestMethod -Uri "https://api.twilio.com/2010-04-01/Accounts/$AccountSid/Calls.json" `
        -Method Post `
        -Headers $headers `
        -Body $body

    Write-Host ""
    Write-Host "✅ Call initiated successfully!" -ForegroundColor Green
    Write-Host "Call SID: $($response.sid)" -ForegroundColor Green
    Write-Host "Monitor the call: https://console.twilio.com/us1/monitor/logs/calls/$($response.sid)" -ForegroundColor Yellow
} catch {
    Write-Host "❌ Failed to initiate call" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}

