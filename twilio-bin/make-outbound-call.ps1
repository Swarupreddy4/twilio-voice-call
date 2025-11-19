# Make an outbound call using the Spring Boot API (PowerShell)
# Usage: .\make-outbound-call.ps1 -ToNumber "+1234567890" [-FromNumber "+1234567890"] [-CustomMessage "Hello!"] [-ApiBaseUrl "http://localhost:8080"]

param(
    [Parameter(Mandatory=$true)]
    [string]$ToNumber,
    
    [Parameter(Mandatory=$false)]
    [string]$FromNumber = $env:TWILIO_PHONE_NUMBER,
    
    [Parameter(Mandatory=$false)]
    [string]$CustomMessage = "",
    
    [Parameter(Mandatory=$false)]
    [string]$ApiBaseUrl = "http://localhost:8080"
)

if (-not $FromNumber) {
    Write-Host "Error: FromNumber is required. Set TWILIO_PHONE_NUMBER environment variable or provide -FromNumber parameter." -ForegroundColor Red
    exit 1
}

# Build JSON payload
$body = @{
    toNumber = $ToNumber
    fromNumber = $FromNumber
}

if ($CustomMessage) {
    $body.customMessage = $CustomMessage
}

$jsonBody = $body | ConvertTo-Json

Write-Host "Making outbound call..." -ForegroundColor Cyan
Write-Host "To: $ToNumber" -ForegroundColor White
Write-Host "From: $FromNumber" -ForegroundColor White
if ($CustomMessage) {
    Write-Host "Message: $CustomMessage" -ForegroundColor White
}
Write-Host ""

try {
    $response = Invoke-RestMethod -Uri "$ApiBaseUrl/twilio/outbound/call" `
        -Method Post `
        -ContentType "application/json" `
        -Body $jsonBody

    Write-Host ""
    Write-Host "✅ Outbound call initiated successfully!" -ForegroundColor Green
    Write-Host "Call SID: $($response.callSid)" -ForegroundColor Green
    Write-Host "Status: $($response.status)" -ForegroundColor Green
    Write-Host "Monitor the call: https://console.twilio.com/us1/monitor/logs/calls/$($response.callSid)" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Check call status: Invoke-RestMethod -Uri `"$ApiBaseUrl/twilio/call/$($response.callSid)/status`"" -ForegroundColor Cyan
} catch {
    Write-Host "❌ Failed to initiate outbound call" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    if ($_.ErrorDetails) {
        Write-Host $_.ErrorDetails.Message -ForegroundColor Red
    }
    exit 1
}

