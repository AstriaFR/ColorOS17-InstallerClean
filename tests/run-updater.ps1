param(
    [Parameter(Mandatory=$true)][string]$Apk,
    [Parameter(Mandatory=$true)][string]$BuildTools,
    [Parameter(Mandatory=$true)][string]$Jdk
)
$ErrorActionPreference='Stop'
$Apk=(Resolve-Path -LiteralPath $Apk).Path
$root=Split-Path $PSScriptRoot
$out=Join-Path $root 'build/updater-tests'
New-Item -ItemType Directory -Path $out -Force | Out-Null
$badging=& "$BuildTools/aapt2.exe" dump badging $Apk
if (($badging -join "`n") -notmatch "versionCode='(\d+)'") {throw 'Cannot read test APK version'}
$version=[long]$Matches[1]
$platform=Join-Path $BuildTools '../../platforms/android-37.0/android.jar'
if (!(Test-Path $platform)) {$platform=Join-Path $BuildTools '../../platforms/android-37/android.jar'}
$wrong=Join-Path $out 'wrong-package.apk'
& "$BuildTools/aapt2.exe" link --manifest "$PSScriptRoot/FlowFixtureManifest.xml" -I $platform -o $wrong
if ($LASTEXITCODE -ne 0) {throw 'Cannot build wrong-package fixture'}
$unsigned=Join-Path $out 'unsigned.apk'
if (Test-Path -LiteralPath $unsigned) {Remove-Item -LiteralPath $unsigned -Force}
$inputZip=[IO.Compression.ZipFile]::OpenRead($Apk)
$outputZip=[IO.Compression.ZipFile]::Open($unsigned,[IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($entry in $inputZip.Entries) {
        $copy=$outputZip.CreateEntry($entry.FullName)
        $inputStream=$entry.Open();$outputStream=$copy.Open()
        try {$inputStream.CopyTo($outputStream)} finally {$inputStream.Dispose();$outputStream.Dispose()}
    }
} finally {$inputZip.Dispose();$outputZip.Dispose()}
$env:INSTALLERCLEAN_AUDIT_INSTALLED_APK=$Apk
$env:INSTALLERCLEAN_AUDIT_REMOTE_HASH=(Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash.ToLowerInvariant()
$env:INSTALLERCLEAN_AUDIT_VERSION="$version"
$results=@()
foreach ($mode in @('wrong-package','wrong-version','unsigned-apk','malformed-disable','success')) {
    $env:INSTALLERCLEAN_AUDIT_MODE=$mode
    $env:INSTALLERCLEAN_AUDIT_STATE=Join-Path $out "$mode-state.json"
    $env:INSTALLERCLEAN_AUDIT_TRACE=Join-Path $out "$mode-trace.jsonl"
    @{enabled=$true;installedFile=''}|ConvertTo-Json|Set-Content $env:INSTALLERCLEAN_AUDIT_STATE -Encoding utf8
    Set-Content $env:INSTALLERCLEAN_AUDIT_TRACE '' -Encoding utf8
    $candidate=if ($mode -eq 'wrong-package') {$wrong} elseif ($mode -eq 'unsigned-apk') {$unsigned} else {$Apk}
    $expected=if ($mode -eq 'wrong-version') {$version+100} else {$version}
    $failure=''
    try {& "$root/install-local.ps1" -Apk $candidate -Adb "$PSScriptRoot/updater-fixtures/Fake-Adb.ps1" -ExpectedVersionCode $expected -BuildTools $BuildTools -Jdk $Jdk|Out-Null}
    catch {$failure=$_.Exception.Message}
    $state=Get-Content $env:INSTALLERCLEAN_AUDIT_STATE -Raw|ConvertFrom-Json
    $trace=Get-Content $env:INSTALLERCLEAN_AUDIT_TRACE -Raw
    if ($mode -in @('wrong-package','wrong-version','unsigned-apk')) {
        if (!$failure -or $state.installedFile -or $trace.Contains('modules disable')) {throw "Input preflight failed: $mode $failure"}
        if ($mode -eq 'wrong-package' -and !$failure.Contains('Wrong APK package')) {throw $failure}
        if ($mode -eq 'wrong-version' -and !$failure.Contains('Requested APK version')) {throw $failure}
        if ($mode -eq 'unsigned-apk' -and !$failure.Contains('signature verification failed')) {throw $failure}
    } elseif ($mode -eq 'malformed-disable') {
        if (!$failure -or !$state.enabled -or !$trace.Contains('modules enable')) {throw "Enablement recovery failed: $failure"}
    } elseif ($failure -or !$state.enabled -or $state.installedFile -ne $Apk) {throw "Normal update failed: $failure"}
    Write-Output "UPDATER_CASE_PASS $mode"
    $results+=@{mode=$mode;passed=$true;phoneTouched=$false}
}
$results|ConvertTo-Json|Set-Content (Join-Path $out 'results.json') -Encoding utf8
