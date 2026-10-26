#requires -Version 5.1
<#
  知花 / ZhiHua - portable APK build script

  Requirements:
    - JDK 17 (set JAVA_HOME, or put it at .tools/jdk17/jdk-17.0.2)
    - Android SDK with platform 34+ and build-tools 34+
      (set ANDROID_SDK_ROOT or ANDROID_HOME)

  Signing:
    By default this script creates a LOCAL DEBUG key at keystore/debug.jks.
    A debug-signed APK cannot upgrade a release-signed APK, so use your own
    release key for publishing:

      $env:ZHIHUA_KEYSTORE  = "C:\path\to\release.jks"
      $env:ZHIHUA_STORE_PASS= "..."
      $env:ZHIHUA_KEY_ALIAS = "your-alias"
      $env:ZHIHUA_KEY_PASS  = "..."   # optional, defaults to store pass

    Never commit a real keystore or its password.
#>
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

function Find-Jdk {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
        return $env:JAVA_HOME
    }
    $bundled = Join-Path $root '.tools\jdk17\jdk-17.0.2'
    if (Test-Path (Join-Path $bundled 'bin\javac.exe')) { return $bundled }
    throw 'JDK 17 not found. Set JAVA_HOME.'
}

function Find-Sdk {
    if ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) { return $env:ANDROID_SDK_ROOT }
    if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) { return $env:ANDROID_HOME }
    throw 'Android SDK not found. Set ANDROID_SDK_ROOT or ANDROID_HOME.'
}

function Newest-VersionDir([string]$base) {
    Get-ChildItem $base -Directory |
        Where-Object { $_.Name -match '^\d+(\.\d+)+$' } |
        Sort-Object { [version]$_.Name } -Descending |
        Select-Object -First 1
}

function Newest-PlatformDir([string]$base) {
    Get-ChildItem $base -Directory |
        Where-Object { $_.Name -match '^android-\d+(\.\d+)?$' } |
        Sort-Object { [double]($_.Name -replace '^android-', '') } -Descending |
        Select-Object -First 1
}

$jdk = Find-Jdk
$sdk = Find-Sdk
$btDir = if ($env:ANDROID_BUILD_TOOLS) {
    Join-Path $sdk "build-tools\$env:ANDROID_BUILD_TOOLS"
} else {
    $v = Newest-VersionDir (Join-Path $sdk 'build-tools')
    if (!$v) { throw 'No Android build-tools found.' }
    $v.FullName
}
$platformDir = if ($env:ANDROID_PLATFORM) {
    if (Test-Path $env:ANDROID_PLATFORM) { $env:ANDROID_PLATFORM }
    else { Join-Path $sdk "platforms\$env:ANDROID_PLATFORM" }
} else {
    $p = Newest-PlatformDir (Join-Path $sdk 'platforms')
    if (!$p) { throw 'No Android platform found.' }
    $p.FullName
}
$platform = Join-Path $platformDir 'android.jar'
$bt = $btDir

foreach ($tool in @('aapt2.exe', 'zipalign.exe', 'apksigner.bat')) {
    if (!(Test-Path (Join-Path $bt $tool))) { throw "Missing $tool in $bt" }
}
if (!(Test-Path $platform)) { throw "android.jar not found: $platform" }
if (!(Test-Path (Join-Path $jdk 'bin\javac.exe'))) { throw "javac not found: $jdk" }

$env:JAVA_HOME = $jdk
$env:PATH = (Join-Path $jdk 'bin') + ';' + $bt + ';' + $env:PATH
$build = Join-Path $root 'build\android'
$dist = Join-Path $root 'dist'
New-Item -ItemType Directory -Force -Path $build, $dist | Out-Null
Remove-Item (Join-Path $build '*') -Recurse -Force -ErrorAction SilentlyContinue

$resZip = Join-Path $build 'res.zip'
Write-Host '[1/6] aapt2 compile'
& (Join-Path $bt 'aapt2.exe') compile --dir (Join-Path $root 'app\src\main\res') -o $resZip
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

$gen = Join-Path $build 'gen'
New-Item -ItemType Directory -Force -Path $gen | Out-Null
$baseApk = Join-Path $build 'base.apk'
Write-Host '[2/6] aapt2 link'
& (Join-Path $bt 'aapt2.exe') link -o $baseApk -I $platform `
    --manifest (Join-Path $root 'app\src\main\AndroidManifest.xml') `
    -R $resZip --java $gen --auto-add-overlay `
    --min-sdk-version 26 --target-sdk-version 34 `
    --version-code 3 --version-name 1.2.0
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

$classes = Join-Path $build 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources = @(Get-ChildItem (Join-Path $root 'app\src\main\java') -Recurse -Filter *.java |
    ForEach-Object { $_.FullName })
$sources += @(Get-ChildItem $gen -Recurse -Filter *.java | ForEach-Object { $_.FullName })
Write-Host ("[3/6] javac ({0} files)" -f $sources.Count)
$javacArgs = @('-encoding', 'UTF-8', '-source', '8', '-target', '8',
    '-bootclasspath', $platform, '-classpath', $platform, '-d', $classes) + $sources
& (Join-Path $jdk 'bin\javac.exe') @javacArgs
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

$dexDir = Join-Path $build 'dex'
New-Item -ItemType Directory -Force -Path $dexDir | Out-Null
Write-Host '[4/6] jar + d8'
$classesJar = Join-Path $build 'classes.jar'
& (Join-Path $jdk 'bin\jar.exe') --create --file $classesJar -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'jar create failed' }
& (Join-Path $bt 'd8.bat') --lib $platform --min-api 26 --output $dexDir $classesJar
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host '[5/6] package + zipalign'
$unsigned = Join-Path $build 'app-unsigned.apk'
Copy-Item $baseApk $unsigned -Force
& (Join-Path $jdk 'bin\jar.exe') --update --file $unsigned -C $dexDir classes.dex
if ($LASTEXITCODE -ne 0) { throw 'jar update failed' }
$aligned = Join-Path $build 'app-aligned.apk'
& (Join-Path $bt 'zipalign.exe') -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

$ks = $env:ZHIHUA_KEYSTORE
$storePass = $env:ZHIHUA_STORE_PASS
$keyAlias = $env:ZHIHUA_KEY_ALIAS
$keyPass = $env:ZHIHUA_KEY_PASS
$debugSigning = $false
if (!$ks) {
    $ks = Join-Path $root 'keystore\debug.jks'
    $storePass = 'android'
    $keyAlias = 'androiddebugkey'
    $keyPass = 'android'
    $debugSigning = $true
}
if (!$keyAlias) { $keyAlias = 'zhihua' }
if (!$keyPass) { $keyPass = $storePass }
if (!$storePass) { throw 'ZHIHUA_STORE_PASS is required for a custom keystore.' }
if (!(Test-Path $ks)) {
    if (!$debugSigning) { throw "Keystore not found: $ks" }
    New-Item -ItemType Directory -Force -Path (Split-Path $ks) | Out-Null
    Write-Host '    generating local debug key (not for release)'
    & (Join-Path $jdk 'bin\keytool.exe') -genkeypair -v -keystore $ks `
        -storepass $storePass -keypass $keyPass -alias $keyAlias `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -dname 'CN=Android Debug,O=Android,C=US'
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}
if ($debugSigning) {
    Write-Warning 'Using a local DEBUG signing key. Do not publish this APK as a release build.'
}

$outApk = Join-Path $dist 'ZhiHua-1.2.0.apk'
Write-Host '[6/6] apksigner'
& (Join-Path $bt 'apksigner.bat') sign --ks $ks --ks-key-alias $keyAlias `
    --ks-pass "pass:$storePass" --key-pass "pass:$keyPass" `
    --out $outApk $aligned
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }
& (Join-Path $bt 'apksigner.bat') verify --print-certs $outApk
if ($LASTEXITCODE -ne 0) { throw 'apksigner verify failed' }

$hash = (Get-FileHash $outApk -Algorithm SHA256).Hash
Write-Host ''
Write-Host ("APK: {0}" -f $outApk)
Write-Host ("SHA-256: {0}" -f $hash)
& (Join-Path $bt 'aapt2.exe') dump badging $outApk | Select-Object -First 6
