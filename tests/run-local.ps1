param([string]$Jdk = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (!$Jdk) { throw 'Specify -Jdk or set JAVA_HOME.' }
$root = Split-Path $PSScriptRoot
$manifest = [xml](Get-Content -LiteralPath "$root/src/AndroidManifest.xml" -Raw)
$buildInfo = Get-Content -LiteralPath "$root/src/java/cn/lzy/gt7p/installerclean/BuildInfo.java" -Raw
if ($buildInfo -notmatch 'VERSION_CODE\s*=\s*(\d+)L') { throw 'Missing compiled build identity.' }
if ([long]$manifest.manifest.GetAttribute('versionCode','http://schemas.android.com/apk/res/android') -ne [long]$Matches[1]) {
    throw 'Manifest version differs from the compiled module identity.'
}
$properties = Get-Content -LiteralPath "$root/src/resources/META-INF/xposed/module.prop" -Raw
if ($properties -notmatch '(?m)^autoHotReload=true\s*$') { throw 'Automatic hot reload must remain enabled.' }
$entrypoints = @(Get-Content -LiteralPath "$root/src/resources/META-INF/xposed/java_init.list" | Where-Object { $_.Trim() -and !$_.Trim().StartsWith('#') })
if ($entrypoints.Count -ne 1 -or $entrypoints[0].Trim() -ne 'cn.lzy.gt7p.installerclean.InstallerHook') {
    throw 'Hot reload requires exactly one Java entrypoint.'
}
Write-Output 'BUILD_CONFIGURATION_PASS'
$out = Join-Path $root 'build/local-tests'
New-Item -ItemType Directory -Force $out | Out-Null
& "$Jdk/bin/javac.exe" -encoding UTF-8 --release 17 -d $out `
    "$root/src/java/cn/lzy/gt7p/installerclean/PermissionCatalog.java" `
    "$root/src/java/cn/lzy/gt7p/installerclean/ParsingPolicy.java" `
    "$root/src/java/cn/lzy/gt7p/installerclean/ReloadState.java" `
    "$root/src/java/cn/lzy/gt7p/installerclean/Feature.java" `
    "$root/src/java/cn/lzy/gt7p/installerclean/Profile.java" `
    "$root/src/java/cn/lzy/gt7p/installerclean/StructuralProbe.java" `
    "$root/src/java/cn/lzy/gt7p/installerclean/RoutingPolicy.java" `
    "$PSScriptRoot/fixtures/com/oplus/appdetail/modelv2/parsing/b.java" "$PSScriptRoot/RegressionTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Local test compilation failed.' }
& "$Jdk/bin/java.exe" -cp $out cn.lzy.gt7p.installerclean.RegressionTest
if ($LASTEXITCODE -ne 0) { throw 'Local regression failed.' }
$androidOut = Join-Path $root 'build/android-local-tests'
$privateOut = Join-Path $root 'build/private-permission-fixtures'
New-Item -ItemType Directory -Force $androidOut,$privateOut | Out-Null
$androidFixtures = @((Get-ChildItem "$PSScriptRoot/android-fixtures" -Recurse -Filter '*.java').FullName)
& "$Jdk/bin/javac.exe" -encoding UTF-8 --release 17 -d $androidOut @androidFixtures `
    "$root/src/java/cn/lzy/gt7p/installerclean/Profile.java" `
    "$root/src/java/cn/lzy/gt7p/installerclean/Feature.java" `
    "$root/src/java/cn/lzy/gt7p/installerclean/PermissionCatalog.java" `
    "$root/src/java/cn/lzy/gt7p/installerclean/PermissionDetails.java" `
    "$PSScriptRoot/AndroidRegressionTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Android fixture compilation failed.' }
& "$Jdk/bin/javac.exe" -encoding UTF-8 --release 17 -d $privateOut "$PSScriptRoot/private-fixtures/le/b.java"
if ($LASTEXITCODE -ne 0) { throw 'Private permission fixture compilation failed.' }
& "$Jdk/bin/java.exe" -cp $androidOut cn.lzy.gt7p.installerclean.AndroidRegressionTest $privateOut
if ($LASTEXITCODE -ne 0) { throw 'Android fixture regression failed.' }
