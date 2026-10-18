param(
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$Jdk = $env:JAVA_HOME,
    [Parameter(Mandatory = $true)][string]$LibXposedAar,
    [string]$KeyStore,
    [string]$KeyAlias = 'installer-clean'
)
$ErrorActionPreference = 'Stop'
function Run([string]$exe, [string[]]$arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed: $exe" }
}
if (!$Sdk) { $Sdk = $env:ANDROID_SDK_ROOT }
if (!$Sdk -or !$Jdk) { throw 'Specify -Sdk and -Jdk, or set ANDROID_HOME and JAVA_HOME.' }
$LibXposedAar = (Resolve-Path -LiteralPath $LibXposedAar).Path
$bt = Join-Path $Sdk 'build-tools/37.0.0'
$platform = Join-Path $Sdk 'platforms/android-37/android.jar'
# Some SDK distributions use the android-37.0 directory name.
if (!(Test-Path -LiteralPath $platform)) { $platform = Join-Path $Sdk 'platforms/android-37.0/android.jar' }
if (!(Test-Path -LiteralPath $platform)) { throw 'Android SDK Platform 37 is missing.' }
$out = Join-Path $PSScriptRoot 'build/apk'
$src = Join-Path $PSScriptRoot 'src'
if (Test-Path -LiteralPath $out) {
    $resolved = (Resolve-Path -LiteralPath $out).Path
    $expected = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'build/apk'))
    if (!$resolved.Equals($expected, [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid generated output path' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
New-Item -ItemType Directory -Force "$out/classes","$out/dex","$out/api" | Out-Null
Push-Location "$out/api"
try { Run "$Jdk/bin/jar.exe" @('xf',$LibXposedAar,'classes.jar') } finally { Pop-Location }
Run "$bt/aapt2.exe" @('compile','--dir',"$src/res",'-o',"$out/resources.zip")
Run "$bt/aapt2.exe" @('link','-o',"$out/unsigned.apk",'--manifest',"$src/AndroidManifest.xml",'-I',$platform,"$out/resources.zip")
$sources = @((Get-ChildItem "$src/java" -Recurse -Filter '*.java').FullName)
Run "$Jdk/bin/javac.exe" (@('-encoding','UTF-8','-source','17','-target','17','-classpath',"$platform;$out/api/classes.jar",'-d',"$out/classes") + $sources)
$classes = @((Get-ChildItem "$out/classes" -Recurse -Filter '*.class').FullName)
Run "$Jdk/bin/java.exe" (@('-cp',"$bt/lib/d8.jar",'com.android.tools.r8.D8','--lib',$platform,'--classpath',"$out/api/classes.jar",'--min-api','26','--output',"$out/dex") + $classes)
Run "$Jdk/bin/jar.exe" @('uf',"$out/unsigned.apk",'-C',"$out/dex",'classes.dex','-C',"$src/resources",'META-INF')
$apk = Join-Path $out 'ColorOS17-InstallerClean-v1.1-unsigned.apk'
Run "$bt/zipalign.exe" @('-f','-p','4',"$out/unsigned.apk",$apk)
if ($KeyStore) {
    # Passwords are supplied through environment variables and never stored in source.
    if (!$env:INSTALLERCLEAN_KEYSTORE_PASSWORD) { throw 'Set INSTALLERCLEAN_KEYSTORE_PASSWORD for signing.' }
    $signed = Join-Path $out 'ColorOS17-InstallerClean-v1.1.apk'
    $signArgs = @('-jar',"$bt/lib/apksigner.jar",'sign','--ks',$KeyStore,'--ks-key-alias',$KeyAlias,'--ks-pass','env:INSTALLERCLEAN_KEYSTORE_PASSWORD')
    if ($env:INSTALLERCLEAN_KEY_PASSWORD) { $signArgs += @('--key-pass','env:INSTALLERCLEAN_KEY_PASSWORD') }
    Run "$Jdk/bin/java.exe" ($signArgs + @('--out',$signed,$apk))
    Run "$Jdk/bin/java.exe" @('-jar',"$bt/lib/apksigner.jar",'verify','--verbose',$signed)
    $apk = $signed
}
Run "$bt/aapt2.exe" @('dump','badging',$apk)
Write-Output "Built $apk"
