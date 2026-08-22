param(
    [string]$KeystorePath = "$env:USERPROFILE\PocketKeys\pocket-release.jks",
    [string]$CredentialPath = "$env:USERPROFILE\PocketKeys\pocket-release-credential.xml",
    [string]$KeyAlias = "pocket"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$gradleWrapper = Join-Path $repoRoot "gradlew.bat"
$variableNames = @(
    "JAVA_HOME",
    "ANDROID_HOME",
    "POCKET_RELEASE_STORE_FILE",
    "POCKET_RELEASE_STORE_PASSWORD",
    "POCKET_RELEASE_KEY_ALIAS",
    "POCKET_RELEASE_KEY_PASSWORD"
)
$previousValues = @{}
foreach ($name in $variableNames) {
    $previousValues[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

if (-not (Test-Path -LiteralPath $KeystorePath -PathType Leaf)) {
    throw "Release keystore not found: $KeystorePath"
}
if (-not (Test-Path -LiteralPath $CredentialPath -PathType Leaf)) {
    throw "DPAPI credential not found: $CredentialPath"
}

$credential = Import-Clixml -LiteralPath $CredentialPath
if ($credential -isnot [pscredential]) {
    throw "DPAPI credential file does not contain a PSCredential."
}
$password = $credential.GetNetworkCredential().Password

try {
    if (-not $env:JAVA_HOME) {
        $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
    }
    if (-not $env:ANDROID_HOME) {
        $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
    }
    $env:POCKET_RELEASE_STORE_FILE = $KeystorePath
    $env:POCKET_RELEASE_STORE_PASSWORD = $password
    $env:POCKET_RELEASE_KEY_ALIAS = $KeyAlias
    $env:POCKET_RELEASE_KEY_PASSWORD = $password

    & $gradleWrapper :app:assembleRelease
    if ($LASTEXITCODE -ne 0) {
        throw "Signed release build failed with exit code $LASTEXITCODE."
    }

    Write-Output (Join-Path $repoRoot "app\build\outputs\apk\release\app-release.apk")
}
finally {
    $password = $null
    $credential = $null
    foreach ($name in $variableNames) {
        [Environment]::SetEnvironmentVariable($name, $previousValues[$name], "Process")
    }
}
