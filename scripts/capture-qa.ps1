param(
    [string]$DeviceId = 'emulator-5554',
    [string[]]$Profiles = @('phone', 'dark', 'large-text', 'small-phone', 'tablet-portrait', 'tablet-landscape', 'phone-landscape'),
    [switch]$SkipBuild,
    [switch]$SkipFunctionalTests
)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$sdkPath = $env:ANDROID_HOME
if (-not $sdkPath) { $sdkPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adbPath = Join-Path $sdkPath 'platform-tools\adb.exe'
function Invoke-Adb {
    $result = & $adbPath -s $DeviceId @args 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    return ($result -join "`n")
}
if ((Invoke-Adb shell getprop ro.kernel.qemu).Trim() -ne '1') {
    throw 'QA display overrides are intended for an emulator only.'
}
$originalSize = Invoke-Adb shell wm size
$originalDensity = Invoke-Adb shell wm density
$originalFont = (Invoke-Adb shell settings get system font_scale).Trim()
$originalNight = (Invoke-Adb shell cmd uimode night).Split(':')[-1].Trim()
$originalRotation = (Invoke-Adb shell settings get system user_rotation).Trim()
$originalAutoRotation = (Invoke-Adb shell settings get system accelerometer_rotation).Trim()
$animationKeys = @('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale')
$originalAnimations = @{}
foreach ($key in $animationKeys) { $originalAnimations[$key] = (Invoke-Adb shell settings get global $key).Trim() }
Push-Location $projectPath
try {
    New-Item -ItemType Directory -Force -Path 'artifacts\screenshots','artifacts\profiles' | Out-Null
    if (-not $SkipBuild) {
        if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
        & .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console plain
        if ($LASTEXITCODE -ne 0) { throw 'Could not build app and instrumentation APKs.' }
    }
    Invoke-Adb install -r 'app\build\outputs\apk\debug\app-debug.apk' | Write-Host
    Invoke-Adb install -r 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk' | Write-Host
    Invoke-Adb shell settings put system accelerometer_rotation 0 | Out-Null
    Invoke-Adb shell settings put system user_rotation 0 | Out-Null
    Invoke-Adb shell wm size reset | Out-Null
    Invoke-Adb shell wm density reset | Out-Null
    Invoke-Adb shell settings put system font_scale 1.0 | Out-Null
    Invoke-Adb shell cmd uimode night no | Out-Null
    # Match Android Gradle Plugin's animationsDisabled test setting, including rotation.
    foreach ($key in $animationKeys) { Invoke-Adb shell settings put global $key 0 | Out-Null }
    if (-not $SkipFunctionalTests) {
        $result = Invoke-Adb shell am instrument -w -e class 'vn.nghia.pos.PosFlowTest,vn.nghia.pos.RepositoryIntegrationTest' 'vn.nghia.pos.test/androidx.test.runner.AndroidJUnitRunner'
        Set-Content -LiteralPath 'artifacts\functional-device-tests.log' -Value $result -Encoding utf8
        if ($result -notmatch 'OK \(12 tests\)') { throw 'Functional device tests failed; see artifacts/functional-device-tests.log.' }
        Write-Host 'Functional tests: 12 passed.'
    }
    foreach ($profile in $Profiles) {
        Invoke-Adb shell wm size reset | Out-Null
        Invoke-Adb shell wm density reset | Out-Null
        Invoke-Adb shell settings put system user_rotation 0 | Out-Null
        Invoke-Adb shell settings put system font_scale 1.0 | Out-Null
        Invoke-Adb shell cmd uimode night no | Out-Null
        switch ($profile) {
            'phone' { }
            'dark' { Invoke-Adb shell cmd uimode night yes | Out-Null }
            'large-text' { Invoke-Adb shell settings put system font_scale 2.0 | Out-Null }
            'small-phone' { Invoke-Adb shell wm size 720x1280 | Out-Null; Invoke-Adb shell wm density 320 | Out-Null }
            'tablet-portrait' { Invoke-Adb shell wm size 1000x1400 | Out-Null; Invoke-Adb shell wm density 160 | Out-Null }
            'tablet-landscape' { Invoke-Adb shell wm size 1400x900 | Out-Null; Invoke-Adb shell wm density 160 | Out-Null }
            'phone-landscape' { Invoke-Adb shell wm size 1280x720 | Out-Null; Invoke-Adb shell wm density 320 | Out-Null }
            default { throw "Unknown profile: $profile" }
        }
        $result = Invoke-Adb shell am instrument -w -e class 'vn.nghia.pos.VisualSmokeTest' -e profile $profile 'vn.nghia.pos.test/androidx.test.runner.AndroidJUnitRunner'
        Set-Content -LiteralPath "artifacts\profiles\$profile.log" -Value $result -Encoding utf8
        if ($result -notmatch 'OK \(1 test\)') { throw "Visual smoke failed for $profile; see artifacts/profiles/$profile.log." }
        Write-Host "Visual smoke passed: $profile"
    }
} finally {
    Invoke-Adb shell am force-stop vn.nghia.pos | Out-Null
    Invoke-Adb pull '/sdcard/Android/data/vn.nghia.pos/files/qa/.' 'artifacts\screenshots' | Write-Host
    Invoke-Adb shell wm size reset | Out-Null
    Invoke-Adb shell wm density reset | Out-Null
    if ($originalSize -match 'Override size:\s*(\d+x\d+)') { Invoke-Adb shell wm size $Matches[1] | Out-Null }
    if ($originalDensity -match 'Override density:\s*(\d+)') { Invoke-Adb shell wm density $Matches[1] | Out-Null }
    if ($originalFont -eq 'null') { Invoke-Adb shell settings delete system font_scale | Out-Null }
    else { Invoke-Adb shell settings put system font_scale $originalFont | Out-Null }
    if ($originalNight -in @('yes','no','auto','custom')) { Invoke-Adb shell cmd uimode night $originalNight | Out-Null }
    Invoke-Adb shell settings put system user_rotation $originalRotation | Out-Null
    Invoke-Adb shell settings put system accelerometer_rotation $originalAutoRotation | Out-Null
    foreach ($key in $animationKeys) {
        if ($originalAnimations[$key] -eq 'null') { Invoke-Adb shell settings delete global $key | Out-Null }
        else { Invoke-Adb shell settings put global $key $originalAnimations[$key] | Out-Null }
    }
    Pop-Location
}
