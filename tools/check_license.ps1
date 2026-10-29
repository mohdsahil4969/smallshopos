param(
    [Parameter(Mandatory=$true, Position=0)]
    [string]$DeviceId
)

$WebhookUrl = "https://script.google.com/macros/s/AKfycbyn2XGiekYBTKgODflwhl5OuCxD_6dNLkoO7qeAJrpN1s4A-C4lIJjIPPkm0g5WE654/exec"

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
