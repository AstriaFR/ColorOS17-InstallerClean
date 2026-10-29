param([string]$Jdk = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (!$Jdk) { throw 'Specify -Jdk or set JAVA_HOME.' }
$root = Split-Path $PSScriptRoot
$out = Join-Path $root 'build/finish-tests'
New-Item -ItemType Directory -Force $out | Out-Null
$fixtures = @((Get-ChildItem "$PSScriptRoot/finish-fixtures" -Recurse -Filter '*.java').FullName)
& "$Jdk/bin/javac.exe" -encoding UTF-8 --release 17 -d $out @fixtures "$root/src/java/cn/lzy/gt7p/installerclean/FinishButtons.java" "$PSScriptRoot/FinishButtonsTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Finish action fixture compilation failed.' }
& "$Jdk/bin/java.exe" -cp $out cn.lzy.gt7p.installerclean.FinishButtonsTest
if ($LASTEXITCODE -ne 0) { throw 'Finish action regression failed.' }
