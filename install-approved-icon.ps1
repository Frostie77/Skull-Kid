# Install the approved Skull Kid launcher icon into this local Android checkout.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$archive = Join-Path $HOME 'Downloads\Skull-Kid-Approved-Launcher-Icon-Resources.zip'
if (-not (Test-Path -LiteralPath $archive)) {
    throw 'Download Skull-Kid-Approved-Launcher-Icon-Resources.zip to Downloads first.'
}
$hash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
if ($hash -ne '8511db00922c2c65cb21ad0285690d8a6e3442f90d22760f674a8827b5da2736') {
    throw 'Launcher icon archive checksum mismatch.'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$expectedFiles = @(
    'app/src/main/res/drawable/ic_launcher_background.xml',
    'app/src/main/res/drawable-nodpi/ic_launcher_foreground.png',
    'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml',
    'app/src/main/res/mipmap-hdpi/ic_launcher.png',
    'app/src/main/res/mipmap-mdpi/ic_launcher.png',
    'app/src/main/res/mipmap-xhdpi/ic_launcher.png',
    'app/src/main/res/mipmap-xxhdpi/ic_launcher.png',
    'app/src/main/res/mipmap-xxxhdpi/ic_launcher.png'
)
$zip = [System.IO.Compression.ZipFile]::OpenRead($archive)
try {
    $actualFiles = @($zip.Entries | ForEach-Object { $_.FullName })
    if (@($actualFiles | Where-Object { $_ -notin $expectedFiles }).Count -gt 0 -or
        @($expectedFiles | Where-Object { $_ -notin $actualFiles }).Count -gt 0) {
        throw 'Launcher archive has unexpected or missing files.'
    }
} finally { $zip.Dispose() }
Expand-Archive -LiteralPath $archive -DestinationPath $PSScriptRoot -Force
$manifestFile = Join-Path $PSScriptRoot 'app\src\main\AndroidManifest.xml'
$manifest = [System.IO.File]::ReadAllText($manifestFile)
if ($manifest -notmatch 'android:label="Skull Kid"') {
    throw 'Manifest has an unexpected app label.'
}
if ($manifest -notmatch 'android:icon="@mipmap/ic_launcher"') {
    $nl = [Environment]::NewLine
    $manifest = $manifest.Replace(
        'android:label="Skull Kid"',
        'android:label="Skull Kid"' + $nl +
        '        android:icon="@mipmap/ic_launcher"' + $nl +
        '        android:roundIcon="@mipmap/ic_launcher"')
    [System.IO.File]::WriteAllText(
        $manifestFile, $manifest, [System.Text.UTF8Encoding]::new($false))
}
Write-Host 'PASS: approved Skull Kid icon resources and launcher manifest installed.'
