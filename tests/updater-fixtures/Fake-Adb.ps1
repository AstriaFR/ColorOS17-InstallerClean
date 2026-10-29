param([Parameter(ValueFromRemainingArguments = $true)][string[]]$FakeArgs)
$ErrorActionPreference = 'Stop'
($FakeArgs | ConvertTo-Json -Compress) | Add-Content -LiteralPath $env:INSTALLERCLEAN_AUDIT_TRACE -Encoding utf8
$state = Get-Content -LiteralPath $env:INSTALLERCLEAN_AUDIT_STATE -Raw | ConvertFrom-Json
function Save-State { $state | ConvertTo-Json | Set-Content -LiteralPath $env:INSTALLERCLEAN_AUDIT_STATE -Encoding utf8 }
if ($FakeArgs[0] -eq 'get-state') { Write-Output 'device'; exit 0 }
if ($FakeArgs[0] -eq 'install') {
    $state.installedFile = $FakeArgs[-1]
    Save-State
    Write-Output 'Success'
    exit 0
}
if ($FakeArgs[0] -eq 'pull') { Copy-Item -LiteralPath $env:INSTALLERCLEAN_AUDIT_INSTALLED_APK -Destination $FakeArgs[-1] -Force; exit 0 }
$command = $FakeArgs -join ' '
if ($command -like '*modules ls') {
    @{success=$true;data=@(@{PACKAGE='cn.lzy.gt7p.installerclean';UID=10210;STATUS= 'enabled'})} | ConvertTo-Json -Depth 5 -Compress
    exit 0
}
if ($command -like '*scope ls*') {
    @{success=$true;data=@(@{APP_PACKAGE='com.oplus.appdetail';USER_ID=0},@{APP_PACKAGE='com.android.packageinstaller';USER_ID=0})} | ConvertTo-Json -Depth 5 -Compress
    exit 0
}
if ($command -like '*modules disable*') {
    $state.enabled = $false
    Save-State
    if ($env:INSTALLERCLEAN_AUDIT_MODE -eq 'malformed-disable') { Write-Output '{malformed'; exit 0 }
    Write-Output '{"success":true,"data":{}}'
    exit 0
}
if ($command -like '*modules enable*') {
    $state.enabled = $true
    Save-State
    Write-Output '{"success":true,"data":{}}'
    exit 0
}
if ($command -like 'shell am force-stop *') { exit 0 }
if ($command -like 'shell pm path *') { Write-Output 'package:/data/app/audit/base.apk'; exit 0 }
if ($command -like 'shell sha256sum *') { Write-Output "$($env:INSTALLERCLEAN_AUDIT_REMOTE_HASH)  /data/app/audit/base.apk"; exit 0 }
if ($command -like 'shell dumpsys package *') { Write-Output "versionCode=$($env:INSTALLERCLEAN_AUDIT_VERSION)"; exit 0 }
throw "Unsupported fake ADB command: $command"
