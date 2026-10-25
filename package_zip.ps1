$stageDir = "$env:TEMP\syntaxa_khata_stage"
if (Test-Path $stageDir) { Remove-Item -Recurse -Force $stageDir }
New-Item -ItemType Directory -Path $stageDir | Out-Null

# 1. Copy source code and app module files
$appTarget = "$stageDir\app"
New-Item -ItemType Directory -Path $appTarget | Out-Null
Copy-Item -Recurse "app\src" "$appTarget\"
Copy-Item "app\build.gradle.kts" "$appTarget\"
Copy-Item "app\proguard-rules.pro" "$appTarget\"

# 2. Copy root build files and gradle wrapper
Copy-Item "build.gradle.kts" "$stageDir\"
Copy-Item "settings.gradle.kts" "$stageDir\"
Copy-Item "gradle.properties" "$stageDir\"
Copy-Item "gradlew" "$stageDir\"
Copy-Item "gradlew.bat" "$stageDir\"
Copy-Item ".gitignore" "$stageDir\"
Copy-Item -Recurse "gradle" "$stageDir\"

# 3. Copy compiled debug APK into apk/ folder
$apkTarget = "$stageDir\apk"
New-Item -ItemType Directory -Path $apkTarget | Out-Null
Copy-Item "app\build\outputs\apk\debug\app-debug.apk" "$apkTarget\SyntaxaKhata-v1.0-debug.apk"

# 4. Create README.md
$readmeLines = @(
    "# Syntaxa Khata (v1.0)",
    "Offline-First Daily Business Assistant for Small Indian Shops",
    "",
    "## Included in this Package:",
    "1. **Ready-to-install APK:** `apk/SyntaxaKhata-v1.0-debug.apk` (19.2 MB)",
    "2. **Complete Native Android Source Code:**",
    "   - Kotlin + Jetpack Compose + Material 3",
    "   - Room SQLite local database with Clean Architecture (MVVM + Repository)",
    "   - Exact integer paise math model (100 paise = INR 1.00)",
    "   - 100% offline PDF receipt generator",
    "   - WhatsApp sharing integration",
    "   - Bluetooth ESC/POS thermal printing engine (58mm / 80mm)",
    "   - Security PIN lock & Biometrics",
    "   - Encrypted backup & restore with SHA-256 integrity validation",
    "   - Bilingual English & Hindi resources",
    "",
    "## How to Install APK on Android Device:",
    "1. Copy `apk/SyntaxaKhata-v1.0-debug.apk` to your Android smartphone (via USB, WhatsApp, or Google Drive).",
    "2. Tap the APK file to install (allow 'Install unknown apps' if prompted).",
    "3. Open Syntaxa Khata and start managing your shop!",
    "",
    "## How to Build from Source:",
    "- Prerequisites: JDK 17 or 21, Android SDK (API 26 to 35).",
    "- Build debug APK: `./gradlew.bat assembleDebug`",
    "- Run automated tests: `./gradlew.bat test`"
)
$readmeLines | Set-Content -Path "$stageDir\README.md" -Encoding UTF8

# 5. Compress to zip
$zipDest = "c:\Users\sahil\OneDrive\Desktop\smallshopos\Syntaxa_Khata_v1.0.zip"
if (Test-Path $zipDest) { Remove-Item -Force $zipDest }
Compress-Archive -Path "$stageDir\*" -DestinationPath $zipDest -CompressionLevel Optimal

# Also copy to Desktop
Copy-Item $zipDest "c:\Users\sahil\OneDrive\Desktop\Syntaxa_Khata_v1.0.zip" -Force

# Clean up staging
Remove-Item -Recurse -Force $stageDir

# Report size
Get-Item $zipDest | Select-Object FullName, @{Name="SizeMB"; Expression={"{0:N2} MB" -f ($_.Length / 1MB)}}
