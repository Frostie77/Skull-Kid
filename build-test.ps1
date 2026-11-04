$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
# Never package the user's game inside the source/debug APK.
$gameAssets = Join-Path $PSScriptRoot 'app\src\main\assets\www'
$looseSwfs = @(Get-ChildItem -LiteralPath $gameAssets -Filter '*.swf' -File -ErrorAction SilentlyContinue)
if ($looseSwfs.Count -gt 0) {
    throw 'SWF must not be under source assets. Import the game via the app file picker.'
}
$libGradle = Join-Path $PSScriptRoot 'third_party\FrostFrontend\frostfrontend\build.gradle'
if (-not (Test-Path $libGradle)) {
    & git submodule update --init --recursive
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $libGradle)) { throw 'FrostFrontend submodule missing.' }
}
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
if (!(Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $env:JAVA_HOME = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
}
if (!(Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { throw 'JDK 17 not found.' }
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
if (!(Test-Path $sdk)) { throw "Android SDK not found: $sdk" }
$env:ANDROID_HOME = $sdk
Set-Content -Path 'local.properties' -Value "sdk.dir=$($sdk.Replace('\','/'))"

$ruffleDest = Join-Path $PSScriptRoot 'app\src\main\assets\www\ruffle'
if (!(Test-Path (Join-Path $ruffleDest 'ruffle.js'))) {
    $version = '0.7.1' # same self-hosted Ruffle build initially used by Arcane
    $tempDir = Join-Path $env:TEMP 'skullkid-ruffle-extract'
    $archive = Join-Path $env:TEMP "skullkid-ruffle-$version.tgz"
    Write-Host "Downloading official Ruffle $version..."
    Invoke-WebRequest "https://registry.npmjs.org/@ruffle-rs/ruffle/-/ruffle-$version.tgz" -OutFile $archive
    if (Test-Path $tempDir) { Remove-Item $tempDir -Recurse -Force }
    New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
    tar -xzf $archive -C $tempDir
    $js = Get-ChildItem $tempDir -Recurse -File -Filter 'ruffle.js' | Select-Object -First 1
    if (!$js) { throw 'Ruffle download missing ruffle.js' }
    New-Item -ItemType Directory -Path $ruffleDest -Force | Out-Null
    Copy-Item (Join-Path $js.DirectoryName '*') $ruffleDest -Recurse -Force
}

$gradle = Join-Path $PSScriptRoot '.bootstrap\gradle-8.11.1\bin\gradle.bat'
if (!(Test-Path $gradle)) {
    $gradle = (Get-Command gradle.bat -ErrorAction SilentlyContinue).Source
}
if (!$gradle -or !(Test-Path $gradle)) {
    $boot = Join-Path $PSScriptRoot '.bootstrap'
    $zip = Join-Path $boot 'gradle-8.11.1-bin.zip'
    New-Item -ItemType Directory -Path $boot -Force | Out-Null
    if (!(Test-Path $zip)) {
        Write-Host 'Downloading Gradle 8.11.1...'
        Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' -OutFile $zip
    }
    Expand-Archive $zip -DestinationPath $boot -Force
    $gradle = Join-Path $boot 'gradle-8.11.1\bin\gradle.bat'
}
& $gradle ':app:assembleDebug'
if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed.' }
$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (!(Test-Path $apk)) { throw 'Debug APK missing.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
try {
    $entries = @($zip.Entries | ForEach-Object { $_.FullName })
    if (@($entries | Where-Object { $_ -match '(?i)\.swf$' }).Count -gt 0) {
        throw 'Game SWF was unexpectedly bundled inside the APK.'
    }
    if (!($entries -contains 'assets/www/ruffle/ruffle.js') -or
        !($entries -contains 'assets/www/index.html')) {
        throw 'Ruffle or HTML wrapper missing from APK.'
    }
} finally { $zip.Dispose() }
Write-Host 'PASS: game-free APK contains Ruffle and the HTML wrapper.'
Write-Host "Built: $apk"
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (Test-Path $adb) {
    $devices = & $adb devices
    if (($devices -join "`n") -match "(?m)^\S+\s+device$") {
        & $adb install -r $apk
        if ($LASTEXITCODE -ne 0) { throw 'ADB installation failed.' }
        Write-Host 'Installed Skull Kid test APK. Open it and import your SWF.'
    } else { Write-Host 'No authorized Odin connected; install the APK manually.' }
}
