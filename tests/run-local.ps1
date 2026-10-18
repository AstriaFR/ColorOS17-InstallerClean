param([string]$Jdk = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (!$Jdk) { throw 'Specify -Jdk or set JAVA_HOME.' }
$root = Split-Path $PSScriptRoot
$out = Join-Path $root 'build/local-tests'
New-Item -ItemType Directory -Force $out | Out-Null
& "$Jdk/bin/javac.exe" -encoding UTF-8 --release 17 -d $out `
    "$root/src/java/cn/lzy/gt7p/installerclean/PermissionCatalog.java" `
    "$root/src/java/cn/lzy/gt7p/installerclean/ParsingPolicy.java" `
    "$PSScriptRoot/fixtures/com/oplus/appdetail/modelv2/parsing/b.java" "$PSScriptRoot/RegressionTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Local test compilation failed.' }
& "$Jdk/bin/java.exe" -cp $out cn.lzy.gt7p.installerclean.RegressionTest
if ($LASTEXITCODE -ne 0) { throw 'Local regression failed.' }
