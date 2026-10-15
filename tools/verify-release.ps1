param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [Parameter(Mandatory = $true)][string]$ExpectedVersion,
    [string]$ExpectedPackage = 'com.music.konnekt',
    [string]$ExpectedSignerSha256 = '54d56c9b9eef84ea188efc5853bf9306d0c2ea2a8765a0dcacaf559476c8eccf',
    [string]$PreviousApk,
    [string]$BuildToolsDir
)

$ErrorActionPreference = 'Stop'
$apk = (Resolve-Path -LiteralPath $ApkPath).Path
if (-not $BuildToolsDir) {
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
    if (-not $sdk) { throw 'Pass -BuildToolsDir or set ANDROID_HOME.' }
    $BuildToolsDir = (Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory |
        Sort-Object Name -Descending | Select-Object -First 1).FullName
}

$aapt = Join-Path $BuildToolsDir 'aapt.exe'
$signer = Join-Path $BuildToolsDir 'apksigner.bat'
$aligner = Join-Path $BuildToolsDir 'zipalign.exe'
foreach ($tool in @($aapt, $signer, $aligner)) {
    if (-not (Test-Path -LiteralPath $tool)) { throw "Missing Android build tool: $tool" }
}

$badging = & $aapt dump badging $apk
if ($LASTEXITCODE -ne 0) { throw "Android cannot read the APK manifest: $apk" }
$packageLine = $badging | Where-Object { $_ -like 'package:*' } | Select-Object -First 1
if ($packageLine -notmatch "name='([^']+)' versionCode='(\d+)' versionName='([^']+)'") {
    throw "Could not read package identity: $packageLine"
}
$package = $Matches[1]
$versionCode = [int]$Matches[2]
$version = $Matches[3]
if ($package -ne $ExpectedPackage) { throw "Unexpected package: $package" }
if ($version -ne $ExpectedVersion) { throw "Unexpected version: $version" }

$null = & $aligner -c -P 16 -v 4 $apk 2>&1
if ($LASTEXITCODE -ne 0) { throw 'APK ZIP alignment failed.' }
$signature = & $signer verify --print-certs $apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
$digest = ($signature | Select-String 'Signer #1 certificate SHA-256 digest: (\w+)' |
    Select-Object -First 1).Matches.Groups[1].Value
if (-not $digest) { throw 'No signing certificate found.' }
if ($digest -ne $ExpectedSignerSha256) {
    throw 'Unexpected signing certificate; this APK cannot update recent Konnekt releases.'
}

if ($PreviousApk) {
    $previous = (Resolve-Path -LiteralPath $PreviousApk).Path
    $previousBadging = & $aapt dump badging $previous
    if ($LASTEXITCODE -ne 0) { throw 'Android cannot read the previous APK manifest.' }
    $previousPackageLine = $previousBadging | Where-Object { $_ -like 'package:*' } | Select-Object -First 1
    if ($previousPackageLine -notmatch "name='([^']+)' versionCode='(\d+)' versionName='([^']+)'") {
        throw "Could not read previous package identity: $previousPackageLine"
    }
    if ($Matches[1] -ne $package) { throw 'The package ID changed; this APK cannot update the installed app.' }
    if ($versionCode -le [int]$Matches[2]) { throw 'The versionCode must increase for an in-place update.' }
    $previousSignature = & $signer verify --print-certs $previous
    if ($LASTEXITCODE -ne 0) { throw 'Previous APK signature verification failed.' }
    $previousDigest = ($previousSignature |
        Select-String 'Signer #1 certificate SHA-256 digest: (\w+)' |
        Select-Object -First 1).Matches.Groups[1].Value
    if ($digest -ne $previousDigest) {
        throw 'The signing certificate changed; this APK cannot update the installed app.'
    }
}

$file = Get-Item -LiteralPath $apk
$sha256 = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
Write-Output "Verified: $($file.Name)"
Write-Output "Package: $package  Version: $version ($versionCode)"
Write-Output "Size: $($file.Length) bytes  SHA-256: $sha256"
Write-Output "Signer SHA-256: $digest"
