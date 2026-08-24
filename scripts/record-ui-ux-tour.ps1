[CmdletBinding()]
param(
    [string]$Serial,
    [string]$OutputDirectory = (Join-Path ([Environment]::GetFolderPath('UserProfile')) 'Downloads')
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$gradleRunner = Join-Path $repoRoot '.agents\skills\gradle-run\scripts\gradle_run.py'
$testClass = 'com.aif31.pocket.PocketUiUxReviewTourTest'
$runner = 'com.aif31.pocket.test/androidx.test.runner.AndroidJUnitRunner'
$remoteVideo = '/sdcard/pocket-ui-ux-review.mp4'
$workflow = $null
$recording = $null
$tourPassed = $false

if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}
if (-not $env:JAVA_HOME) {
    $jdkRoot = Join-Path $env:USERPROFILE '.gradle\jdks'
    $jdk = Get-ChildItem -LiteralPath $jdkRoot -Directory -Filter 'eclipse_adoptium-17*' |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $jdk) { throw "Java 17 not found under $jdkRoot" }
    $env:JAVA_HOME = $jdk.FullName
}

if (-not (Test-Path -LiteralPath $adb)) { throw "ADB not found at $adb" }
if (-not (Test-Path -LiteralPath $gradleRunner)) { throw "Gradle wrapper helper not found at $gradleRunner" }

if (-not $Serial) {
    $devices = @(& $adb devices -l | Select-String '\sdevice\s')
    if ($devices.Count -ne 1) { throw 'Pass -Serial when zero or multiple Android devices are connected.' }
    $Serial = ($devices[0].Line -split '\s+')[0]
}

$deviceState = (& $adb -s $Serial get-state).Trim()
if ($deviceState -ne 'device') { throw "Device $Serial is not ready: $deviceState" }

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$localVideo = Join-Path $OutputDirectory "pocket-ui-ux-review-$stamp.mp4"

Push-Location $repoRoot
try {
    $created = py -3 $gradleRunner create | ConvertFrom-Json
    $workflow = $created.workflow
    $question = 'Do the app and UI-tour test APKs compile and package for recording?'
    $build = py -3 $gradleRunner run --workflow $workflow --scope targeted --question $question -- .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest | ConvertFrom-Json
    if ($build.exit_status -ne 0) { throw "APK build failed: $($build.excerpt -join [Environment]::NewLine)" }

    & $adb -s $Serial install -r -t 'app\build\outputs\apk\debug\app-debug.apk' | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'App APK installation failed.' }
    & $adb -s $Serial install -r -t 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk' | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Test APK installation failed.' }

    & $adb -s $Serial shell rm -f $remoteVideo | Out-Null
    $recording = Start-Process -FilePath $adb -ArgumentList @(
        '-s', $Serial, 'shell', 'screenrecord',
        '--size', '1080x2280', '--bit-rate', '8000000', '--time-limit', '180', $remoteVideo
    ) -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 2

    $instrumentation = @(& $adb -s $Serial shell am instrument -w -r -e class $testClass $runner 2>&1)
    $failures = @($instrumentation | Where-Object { $_ -match '^INSTRUMENTATION_STATUS_CODE: -[12]\s*$' })
    $finalCodes = @($instrumentation | Where-Object { $_ -match '^INSTRUMENTATION_CODE:' })
    if ($failures.Count -gt 0 -or $finalCodes.Count -ne 1 -or $instrumentation -match 'FAILURES!!!') {
        $instrumentation | Select-Object -Last 120 | Out-Host
        throw 'The UI tour test failed; the recording may be incomplete.'
    }
    $tourPassed = $true
}
finally {
    if ($recording -and -not $recording.HasExited) {
        & $adb -s $Serial shell pkill -INT screenrecord | Out-Null
        $null = $recording.WaitForExit(10000)
    }
    if ($tourPassed -and (& $adb -s $Serial shell ls $remoteVideo 2>$null)) {
        & $adb -s $Serial pull $remoteVideo $localVideo | Out-Host
    }
    & $adb -s $Serial shell rm -f $remoteVideo | Out-Null
    if (-not $tourPassed -and (Test-Path -LiteralPath $localVideo)) {
        Remove-Item -LiteralPath $localVideo -Force
    }
    if ($workflow) { py -3 $gradleRunner finish --workflow $workflow | Out-Host }
    Pop-Location
}

if (-not (Test-Path -LiteralPath $localVideo)) { throw 'No recording was produced.' }
Write-Host "UI/UX review video: $localVideo"
