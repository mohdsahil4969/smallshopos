param(
    [Parameter(Mandatory=$true, Position=0)]
    [string]$DeviceId
)

$WebhookUrl = "https://script.google.com/macros/s/AKfycbxhzYbn2KLc9QbNLrWsTgEW8pxl-efG300ivTZ0vCXSNn_ketAJHvqbBwVs-fgZ5qwCDA/exec"

$body = @{
    action = "check_status"
    deviceId = $DeviceId.Trim()
} | ConvertTo-Json

Write-Host "Querying Google Sheets for Device ID: $DeviceId..." -ForegroundColor Cyan
try {
    $resp = Invoke-RestMethod -Uri $WebhookUrl -Method Post -Body $body -ContentType "application/json"
    $resp | Format-List
} catch {
    Write-Host "ERROR: $_" -ForegroundColor Red
}
