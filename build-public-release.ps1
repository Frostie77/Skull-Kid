# Build a signed public APK and stage its exact update metadata.
# The permanent signing key stays outside this repository.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$package = 'io.github.frostie77.skullkid'
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
if (-not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $env:JAVA_HOME = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
}
if (-not (Test-Path (Join-Path $env:JAVA_HOME 'bin\keytool.exe'))) { throw 'JDK 17 or newer with keytool is required.' }
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:ANDROID_HOME = $sdk
if (-not (Test-Path $sdk)) { throw 'Android SDK is missing.' }
Set-Content -LiteralPath 'local.properties' -Value "sdk.dir=$($sdk.Replace('\','/'))"

& git submodule update --init --recursive
if ($LASTEXITCODE -ne 0) { throw 'Could not prepare the pinned FrostFrontend library.' }
& git diff --quiet HEAD --
if ($LASTEXITCODE -ne 0) { throw 'Tracked source has local changes. Commit or back them up before a public build.' }
$untrackedSource = @(& git ls-files --others --exclude-standard -- app/src/main)
if ($LASTEXITCODE -ne 0 -or $untrackedSource.Count -gt 0) { throw 'Untracked app source files must be reviewed before a public build.' }
$sourceSha = (& git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $sourceSha -notmatch '^[a-f0-9]{40}$') { throw 'Could not identify the release source commit.' }

$gradleSource = Get-Content -Raw -LiteralPath 'app\build.gradle'
$versionMatch = [regex]::Match($gradleSource, "versionName\s+'((?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*))'")
$codeMatch = [regex]::Match($gradleSource, 'versionCode\s+([0-9]+)')
if (-not $versionMatch.Success -or -not $codeMatch.Success) { throw 'Release version is invalid.' }
$releaseVersion = $versionMatch.Groups[1].Value
$versionCode = [long]$codeMatch.Groups[1].Value
if ($versionCode -lt 1 -or $versionCode -gt 2100000000) { throw 'Invalid version code.' }
$published = Get-Content -Raw -LiteralPath 'updates\android-stable.json' | ConvertFrom-Json
if ($published.schema_version -ne 1 -or $published.channel -ne 'stable' -or $published.package -ne $package) { throw 'Stable update feed identity is invalid.' }

$icons = Get-Content -Raw -LiteralPath 'approved-icon.sha256.json' | ConvertFrom-Json
foreach ($entry in $icons.PSObject.Properties) {
    $file = Join-Path $PSScriptRoot $entry.Name.Replace('/', '\')
    if (-not (Test-Path -LiteralPath $file) -or
        (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.Value) {
        throw "Approved launcher resource differs or is missing: $($entry.Name)"
    }
}
$manifest = [xml](Get-Content -Raw -LiteralPath 'app\src\main\AndroidManifest.xml')
$iconRef = $manifest.manifest.application.GetAttribute('icon', 'http://schemas.android.com/apk/res/android')
if ($iconRef -ne '@mipmap/ic_launcher') { throw 'Approved launcher icon is not selected.' }

$gameAssets = Join-Path $PSScriptRoot 'app\src\main\assets\www'
if (@(Get-ChildItem -LiteralPath $gameAssets -Recurse -File -Filter '*.swf').Count -gt 0) { throw 'Remove the personal game SWF from source assets before building.' }
$ruffle = Join-Path $gameAssets 'ruffle'
if (-not (Test-Path (Join-Path $ruffle 'ruffle.js')) -or -not (Test-Path (Join-Path $ruffle 'package.json'))) { throw 'Ruffle runtime is missing. Run build-test.ps1 to prepare the build tools first.' }
if ((Get-Content -Raw -LiteralPath (Join-Path $ruffle 'package.json') | ConvertFrom-Json).version -ne '0.7.1') { throw 'This release requires the tested Ruffle 0.7.1 runtime.' }
$gradle = Join-Path $PSScriptRoot '.bootstrap\gradle-8.11.1\bin\gradle.bat'
if (-not (Test-Path $gradle)) { throw 'Gradle bootstrap is missing. Run build-test.ps1 first.' }
$tools = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' -and
        (Test-Path (Join-Path $_.FullName 'apksigner.bat')) -and
        (Test-Path (Join-Path $_.FullName 'zipalign.exe')) -and
        (Test-Path (Join-Path $_.FullName 'aapt.exe')) } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $tools) { throw 'Android SDK signing and inspection tools are missing.' }
$zipalign = Join-Path $tools.FullName 'zipalign.exe'
$apksigner = Join-Path $tools.FullName 'apksigner.bat'
$aapt = Join-Path $tools.FullName 'aapt.exe'

$keyDirectory = Join-Path $env:USERPROFILE '.android'
$keystore = Join-Path $keyDirectory 'Skull-Kid-Release.keystore'
$keyAlias = 'skull-kid-release'
if (-not (Test-Path -LiteralPath $keystore)) {
    if ($null -ne $published.latest) { throw 'Permanent release keystore is missing. Refusing to replace the published signing identity.' }
    New-Item -ItemType Directory -Path $keyDirectory -Force | Out-Null
    Write-Host 'Create your permanent Skull Kid signing password when keytool asks. Keep it for future releases.'
    & (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -genkeypair -keystore $keystore `
        -storetype PKCS12 -alias $keyAlias -keyalg RSA -keysize 4096 -validity 10000 `
        -dname 'CN=Skull Kid Android Release'
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $keystore)) { throw 'Release key creation failed.' }
}

$backupDirectory = Join-Path $env:USERPROFILE 'Documents\Android-Signing-Backups'
New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
$keyBackup = Join-Path $backupDirectory 'Skull-Kid-Release.keystore'
if (Test-Path -LiteralPath $keyBackup) {
    if ((Get-FileHash $keyBackup -Algorithm SHA256).Hash -ne (Get-FileHash $keystore -Algorithm SHA256).Hash) { throw 'Existing signing-key backup differs. Preserve both files and review before signing.' }
} else { Copy-Item -LiteralPath $keystore -Destination $keyBackup }

& $gradle ':app:assembleRelease'
if ($LASTEXITCODE -ne 0) { throw 'Release compilation failed.' }
$unsigned = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release-unsigned.apk'
if (-not (Test-Path $unsigned)) { throw 'Unsigned release APK is missing.' }
$releaseDirectory = Join-Path $PSScriptRoot 'release'
New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null
$apkName = "Skull-Kid-v$releaseVersion-Android.apk"
$signed = Join-Path $releaseDirectory $apkName
$aligned = Join-Path $releaseDirectory "Skull-Kid-v$releaseVersion-aligned.apk"

function Confirm-Apk($file) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($file)
    try {
        $names = @($archive.Entries | ForEach-Object { $_.FullName })
        if (@($names | Where-Object { $_ -match '(?i)\.swf$' }).Count -gt 0) { throw 'Game SWF found inside APK.' }
        foreach ($needed in @('assets/www/index.html', 'assets/www/ruffle/ruffle.js')) {
            if ($needed -notin $names) { throw "APK is missing $needed" }
        }
        if (@($names | Where-Object { $_ -match '^assets/www/ruffle/.*\.wasm$' }).Count -lt 1) { throw 'Ruffle WASM is missing.' }
    } finally { $archive.Dispose() }
    $badging = @(& $aapt dump badging $file)
    if ($LASTEXITCODE -ne 0) { throw 'APK metadata inspection failed.' }
    $text = $badging -join "`n"
    if ($text -notmatch ("package: name='" + [regex]::Escape($package) + "' versionCode='" + $versionCode + "' versionName='" + [regex]::Escape($releaseVersion) + "'")) { throw 'Compiled package identity or version is incorrect.' }
    if ($text -match '(?m)^application-debuggable') { throw 'Public APK is debuggable.' }
    if ($text -notmatch "(?m)^sdkVersion:'26'" -or $text -notmatch "(?m)^targetSdkVersion:'35'") { throw 'Compiled Android SDK requirements are incorrect.' }
    if ($text -notmatch "(?m)^application.*icon='[^']*/ic_launcher\.(?:xml|png|webp)'") { throw 'Compiled approved launcher icon is missing.' }
}
Confirm-Apk $unsigned
& $zipalign -P 16 -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed.' }
Write-Host 'Enter the same permanent keystore password to sign this release.'
& $apksigner sign --ks $keystore --ks-key-alias $keyAlias `
    --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true `
    --v4-signing-enabled false --out $signed $aligned
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
$verification = @(& $apksigner verify --verbose --print-certs $signed)
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
$verificationText = $verification -join "`n"
if ($verificationText -notmatch 'Verified using v2 scheme[^\r\n]*: true' -or $verificationText -notmatch 'Verified using v3 scheme[^\r\n]*: true') { throw 'APK must have verified v2 and v3 signatures.' }
$certificate = [regex]::Match($verificationText, 'Signer #1 certificate SHA-256 digest:\s*([a-fA-F0-9]{64})')
if (-not $certificate.Success) { throw 'Could not read the verified signing certificate.' }
$certificateSha256 = $certificate.Groups[1].Value.ToLowerInvariant()
if ($null -ne $published.latest -and $published.latest.certificate_sha256 -ne $certificateSha256) { throw 'Signing certificate differs from the published release. Do not upload this APK.' }
& $zipalign -c -P 16 4 $signed
if ($LASTEXITCODE -ne 0) { throw 'Signed APK alignment verification failed.' }
Confirm-Apk $signed
Remove-Item -LiteralPath $aligned -Force
$apkSha256 = (Get-FileHash -LiteralPath $signed -Algorithm SHA256).Hash.ToLowerInvariant()
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText((Join-Path $releaseDirectory "Skull-Kid-v$releaseVersion-Android.sha256.txt"), "$apkSha256  $apkName`n", $utf8)

$feed = [ordered]@{
    schema_version = 1
    channel = 'stable'
    package = $package
    latest = [ordered]@{
        package = $package
        version_name = $releaseVersion
        version_code = $versionCode
        release_tag = "v$releaseVersion"
        source_commit = $sourceSha
        certificate_sha256 = $certificateSha256
        min_sdk = 26
        apk_asset = $apkName
        apk_size = (Get-Item -LiteralPath $signed).Length
        apk_sha256 = $apkSha256
    }
}
$feedFile = Join-Path $releaseDirectory 'android-stable.json'
[System.IO.File]::WriteAllText($feedFile, ($feed | ConvertTo-Json -Depth 5) + "`n", $utf8)
$notes = Join-Path $PSScriptRoot "RELEASE_NOTES-v$releaseVersion.md"
if (Test-Path -LiteralPath $notes) { Copy-Item -LiteralPath $notes -Destination $releaseDirectory -Force }
Write-Host 'PASS: signed, aligned release APK; correct package/version/icon; no game SWF.'
Write-Host "APK: $signed"
Write-Host "SHA256: $apkSha256"
Write-Host "CERTIFICATE SHA256: $certificateSha256"
Write-Host "SOURCE: $sourceSha"
Write-Host "KEY BACKUP: $keyBackup"
Write-Host "STAGED UPDATE FEED: $feedFile"
Write-Host 'No app installed. Publish the staged update feed only after the matching APK is live on GitHub.'
