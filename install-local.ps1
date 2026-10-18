param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [string]$Adb = 'adb',
    [string]$Serial,
    [long]$ExpectedVersionCode = 0,
    [string]$BuildTools,
    [string]$Jdk = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$Apk = (Resolve-Path -LiteralPath $Apk).Path
$deviceArgs = @()
function Invoke-Adb([string[]]$Arguments) {
    $result = & $Adb @deviceArgs @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($Arguments -join ' ')`n$($result -join "`n")" }
    return ($result -join "`n")
}
$module = 'cn.lzy.gt7p.installerclean'
# Check local identity before any device mutation.
if (!$BuildTools) {
    $sdkRoots = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT,
        (Join-Path (Split-Path (Split-Path $PSScriptRoot)) '.android-toolchain-locationspoofer/sdk'))
    if ($env:LOCALAPPDATA) { $sdkRoots += Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    foreach ($sdkRoot in $sdkRoots) {
        if (!$sdkRoot -or !(Test-Path -LiteralPath "$sdkRoot/build-tools")) { continue }
        $candidate = Get-ChildItem -LiteralPath "$sdkRoot/build-tools" -Directory |
            Sort-Object Name -Descending | Where-Object { Test-Path -LiteralPath "$($_.FullName)/aapt2.exe" } | Select-Object -First 1
        if ($candidate) { $BuildTools = $candidate.FullName; break }
    }
}
if (!$BuildTools -or !(Test-Path -LiteralPath "$BuildTools/aapt2.exe") -or !(Test-Path -LiteralPath "$BuildTools/lib/apksigner.jar")) {
    throw 'Specify -BuildTools with Android SDK Build Tools (aapt2.exe and lib/apksigner.jar).'
}
$java = if ($Jdk) { Join-Path $Jdk 'bin/java.exe' } else { (Get-Command java -ErrorAction SilentlyContinue).Source }
if (!$java -or !(Test-Path -LiteralPath $java)) { throw 'Specify -Jdk or configure JAVA_HOME/java on PATH.' }
$badging = & "$BuildTools/aapt2.exe" dump badging $Apk 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Cannot parse the requested APK.' }
if (($badging -join "`n") -notmatch "package: name='([^']+)' versionCode='(\d+)'") { throw 'Cannot read APK identity.' }
if ($Matches[1] -ne $module) { throw "Wrong APK package: $($Matches[1]); expected $module." }
$apkVersion = [long]$Matches[2]
if ($ExpectedVersionCode -gt 0 -and $apkVersion -ne $ExpectedVersionCode) { throw 'Requested APK version code mismatch.' }
function Read-Signers([string]$Path) {
    $certs = & $java -jar "$BuildTools/lib/apksigner.jar" verify --print-certs $Path 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $digests = @([regex]::Matches(($certs -join "`n"), '(?m)^(?:Signer #\d+|V\d+(?:\.\d+)? Signer):?\s+certificate SHA-256 digest:\s*([0-9a-fA-F]{64})') |
        ForEach-Object { $_.Groups[1].Value.ToLowerInvariant() } | Sort-Object -Unique)
    if (!$digests.Count) { throw 'Cannot read APK signer identity.' }
    return $digests
}
$localSigners = @(Read-Signers $Apk)
$localHash = (Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($Serial) { $deviceArgs = @('-s', $Serial) }
if ((Invoke-Adb @('get-state')).Trim() -ne 'device') { throw 'Connect one authorized Android device.' }
$cli = '/data/adb/lspd/cli'
$before = (Invoke-Adb @('shell','su','-c',"$cli --json modules ls")) | ConvertFrom-Json
$scopeBefore = (Invoke-Adb @('shell','su','-c',"$cli --json scope ls $module")) | ConvertFrom-Json
if (!$before.success -or !$scopeBefore.success) { throw 'Cannot read Vector configuration.' }
if (!($before.data | Where-Object { $_.PACKAGE -eq $module -and $_.STATUS -eq 'enabled' })) {
    throw 'Enable this module in Vector and choose its scope before using this update script.'
}
$previousPackage = Invoke-Adb @('shell','dumpsys','package',$module)
if ($previousPackage -notmatch 'versionCode=(\d+)') { throw 'Cannot read installed version code before update.' }
if ($apkVersion -lt [long]$Matches[1]) { throw 'Requested APK would downgrade the installed module.' }
$previousPath = (Invoke-Adb @('shell','pm','path',$module)).Trim()
if ($previousPath -notmatch '^package:(/data/app/[A-Za-z0-9_/=~+.\-]+/base\.apk)$') { throw 'Unexpected installed APK path.' }
$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$tempApk = Join-Path $tempRoot ("installerclean-preflight-$([guid]::NewGuid()).apk")
try {
    Invoke-Adb @('pull',$Matches[1],$tempApk) | Out-Null
    $installedSigners = @(Read-Signers $tempApk)
    if (Compare-Object $localSigners $installedSigners) { throw 'Requested APK signer differs from the installed module.' }
} finally {
    # This is one task-owned file, never a recursive cleanup.
    if ([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($tempApk)).TrimEnd('\','/') -ne $tempRoot.TrimEnd('\','/')) {
        throw 'Invalid temporary APK cleanup path.'
    }
    if (Test-Path -LiteralPath $tempApk) { Remove-Item -LiteralPath $tempApk -Force }
}
Write-Output (Invoke-Adb @('install','--no-incremental','-r',$Apk))
# Normal automatic reload is preferred; this scoped refresh also covers old opt-out builds
# and missed package-change broadcasts. Always re-enable if the disable call succeeded.
$restoreNeeded = $false
try {
    $restoreNeeded = $true
    $result = (Invoke-Adb @('shell','su','-c',"$cli --json modules disable $module")) | ConvertFrom-Json
    if (!$result.success) { throw 'Vector refused to disable this module for refresh.' }
} finally {
    if ($restoreNeeded) {
        $result = (Invoke-Adb @('shell','su','-c',"$cli --json modules enable $module")) | ConvertFrom-Json
        if (!$result.success) { throw 'Vector did not re-enable this module; enable it manually.' }
    }
}
Invoke-Adb @('shell','am','force-stop','com.oplus.appdetail') | Out-Null
Invoke-Adb @('shell','am','force-stop','com.android.packageinstaller') | Out-Null
$installedPath = (Invoke-Adb @('shell','pm','path',$module)).Trim()
if ($installedPath -notmatch '^package:(/data/app/[^\r\n]+/base\.apk)$') { throw 'Unexpected installed APK path.' }
$remoteHash = (Invoke-Adb @('shell','sha256sum',$Matches[1])).Split(' ', [StringSplitOptions]::RemoveEmptyEntries)[0].ToLowerInvariant()
if ($remoteHash -ne $localHash) { throw 'Installed APK hash differs from the requested file.' }
$package = Invoke-Adb @('shell','dumpsys','package',$module)
if ($package -notmatch 'versionCode=(\d+)') { throw 'Cannot read installed version code.' }
$installedVersion = [long]$Matches[1]
if ($installedVersion -ne $apkVersion) { throw 'Installed version code mismatch.' }
$after = (Invoke-Adb @('shell','su','-c',"$cli --json modules ls")) | ConvertFrom-Json
$scopeAfter = (Invoke-Adb @('shell','su','-c',"$cli --json scope ls $module")) | ConvertFrom-Json
if (!$after.success -or !$scopeAfter.success) { throw 'Cannot verify Vector after update.' }
$beforeStates = @($before.data | Sort-Object PACKAGE | ForEach-Object { "$($_.PACKAGE):$($_.UID):$($_.STATUS)" })
$afterStates = @($after.data | Sort-Object PACKAGE | ForEach-Object { "$($_.PACKAGE):$($_.UID):$($_.STATUS)" })
if (Compare-Object $beforeStates $afterStates) { throw 'Vector module states changed unexpectedly.' }
$beforeScope = @($scopeBefore.data | Sort-Object APP_PACKAGE,USER_ID | ForEach-Object { "$($_.APP_PACKAGE):$($_.USER_ID)" })
$afterScope = @($scopeAfter.data | Sort-Object APP_PACKAGE,USER_ID | ForEach-Object { "$($_.APP_PACKAGE):$($_.USER_ID)" })
if (Compare-Object $beforeScope $afterScope) { throw 'Vector scope changed unexpectedly.' }
[ordered]@{
    installed = $true
    versionCode = $installedVersion
    apkSha256 = $localHash
    apkIdentityPrevalidated = $true
    signerMatchesInstalled = $true
    vectorCacheRefreshed = $true
    moduleStatesUnchanged = $true
    scopeUnchanged = $true
    installerProcessesStopped = $true
    onDeviceInstallFlowTested = $false
} | ConvertTo-Json
