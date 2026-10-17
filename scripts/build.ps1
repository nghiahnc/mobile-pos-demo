param(
    [switch]$Install,
    [switch]$DeviceTests,
    [string]$DeviceId
)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$bundledJavaPath = 'C:\Program Files\Android\Android Studio\jbr'
if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath "$bundledJavaPath\bin\java.exe")) {
    $env:JAVA_HOME = $bundledJavaPath
}
$sdkPath = $env:ANDROID_HOME
if (-not $sdkPath) { $sdkPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not (Test-Path -LiteralPath (Join-Path $projectPath 'local.properties'))) {
    if (-not (Test-Path -LiteralPath $sdkPath)) { throw 'Không tìm thấy Android SDK. Đặt ANDROID_HOME hoặc mở dự án bằng Android Studio.' }
    Set-Content -LiteralPath (Join-Path $projectPath 'local.properties') -Value ('sdk.dir=' + $sdkPath.Replace('\','/')) -Encoding utf8
}
Push-Location $projectPath
try {
    $tasks = @(':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug')
    if ($DeviceTests) { $tasks += ':app:connectedDebugAndroidTest' }
    & .\gradlew.bat @tasks --console plain
    if ($LASTEXITCODE -ne 0) { throw 'Gradle verification/build failed. Xem lỗi phía trên.' }
    New-Item -ItemType Directory -Force -Path 'artifacts' | Out-Null
    Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination 'artifacts\NghiaPOS-debug.apk' -Force
    Write-Host "APK: $projectPath\artifacts\NghiaPOS-debug.apk"
    if ($Install) {
        $adbPath = Join-Path $sdkPath 'platform-tools\adb.exe'
        if (-not (Test-Path -LiteralPath $adbPath)) { throw 'Không tìm thấy adb. Đặt ANDROID_HOME về SDK đang dùng.' }
        $adbArgs = @()
        if ($DeviceId) { $adbArgs += @('-s', $DeviceId) }
        & $adbPath @adbArgs install -r 'artifacts\NghiaPOS-debug.apk'
        if ($LASTEXITCODE -ne 0) { throw 'Cài APK thất bại. Kiểm tra thiết bị trong adb devices.' }
        & $adbPath @adbArgs shell am start -W -n 'vn.nghia.pos/.ui.MainActivity'
        if ($LASTEXITCODE -ne 0) { throw 'Không mở được ứng dụng.' }
    }
} finally { Pop-Location }
