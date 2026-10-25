param(
    [Parameter(Mandatory=$true, Position=0)]
    [string]$DeviceId
)

$WebhookUrl = "https://script.google.com/macros/s/AKfycbyHdJVvXx4o6Afvqrakm4GT9frKcnJjcIlRvAKKJ8Dhwjzi0mAzvi1GB4LbwJLiLHsK/exec"

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
