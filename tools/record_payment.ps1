param(
    [Parameter(Mandatory=$true, Position=0)]
    [string]$UTR,
    [Parameter(Position=1)]
    [double]$Amount = 49.0,
    [Parameter(Position=2)]
    [string]$Payer = "UPI Customer"
)

$WebhookUrl = "https://script.google.com/macros/s/AKfycbyHdJVvXx4o6Afvqrakm4GT9frKcnJjcIlRvAKKJ8Dhwjzi0mAzvi1GB4LbwJLiLHsK/exec"

$cleanUtr = $UTR.Trim().Replace(" ", "").Replace("-", "")
if ($cleanUtr.Length -ne 12 -or -not ($cleanUtr -match '^\d{12}$')) {
    Write-Host "ERROR: UTR must be exactly 12 numeric digits (got: '$UTR')." -ForegroundColor Red
    exit 1
}

$body = @{
    action = "record_incoming_payment"
    utr = $cleanUtr
    amount = $Amount
    payer = $Payer
} | ConvertTo-Json

Write-Host "Recording payment of ₹$Amount for UTR: $cleanUtr into Google Sheets..." -ForegroundColor Cyan
try {
    $resp = Invoke-RestMethod -Uri $WebhookUrl -Method Post -Body $body -ContentType "application/json"
    if ($resp.success) {
        Write-Host "SUCCESS: $($resp.message)" -ForegroundColor Green
        Write-Host "UTR $cleanUtr is now ACTIVE and ready for the customer to verify in Syntaxa Khata!" -ForegroundColor Green
    } else {
        Write-Host "RESPONSE: $($resp.message)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "ERROR: $_" -ForegroundColor Red
}
