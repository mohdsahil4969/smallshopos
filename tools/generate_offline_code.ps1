param(
    [Parameter(Mandatory=$true, Position=0)]
    [string]$DeviceId
)

$saltMonthly = "SYNTAXA_OFFLINE_PRO_SALT_2026"
$saltYearly = "SYNTAXA_OFFLINE_PRO_YEAR_2026"
$cleanDevId = $DeviceId.Trim()

# Calculate Monthly (30 Days)
$sha = [System.Security.Cryptography.SHA256]::Create()
$bytesM = [System.Text.Encoding]::UTF8.GetBytes($cleanDevId + ":" + $saltMonthly)
$codeMonthly = (($sha.ComputeHash($bytesM) | ForEach-Object { $_.ToString("X2") }) -join "").Substring(0, 6).ToUpper()

# Calculate 1-Year (365 Days)
$bytesY = [System.Text.Encoding]::UTF8.GetBytes($cleanDevId + ":" + $saltYearly)
$codeYearly = (($sha.ComputeHash($bytesY) | ForEach-Object { $_.ToString("X2") }) -join "").Substring(0, 6).ToUpper()

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " SMARTKHATA (SYNTAXA) - OFFLINE PRO CODE GENERATOR" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Target Device ID : $cleanDevId" -ForegroundColor Yellow
Write-Host "------------------------------------------" -ForegroundColor DarkGray
Write-Host "🌟 1-YEAR (365 DAYS) PRO CODE : $codeYearly" -ForegroundColor Green
Write-Host "   (Universal Master Code     : VIP365 or SYNTAXA365)" -ForegroundColor White
Write-Host "------------------------------------------" -ForegroundColor DarkGray
Write-Host "📦 1-MONTH (30 DAYS) PRO CODE  : $codeMonthly" -ForegroundColor Magenta
Write-Host "==========================================" -ForegroundColor Cyan
