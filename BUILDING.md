# Building Skull Kid on Windows

Requires Android SDK 35 and JDK 17 or newer.

## Debug build

```powershell
git clone --recurse-submodules https://github.com/Frostie77/Skull-Kid.git
cd Skull-Kid
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-test.ps1
```

The helper prepares Gradle and Ruffle, builds a Debug APK, and installs it when an authorized device is connected.

Do not put a personal SWF under source assets. Import it through the app's file picker.

## Public release

After preparing the build tools, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-public-release.ps1
```

The helper requires clean tracked source, verifies the approved launcher resources, and creates a signed release APK, checksum, release notes and staged update feed under `release`.

The permanent keystore is stored outside the checkout at `%USERPROFILE%\.android\Skull-Kid-Release.keystore`, with a backup under `%USERPROFILE%\Documents\Android-Signing-Backups`. Retain the same key and its password for future public releases.

Publish the signed APK and checksum first. Activate `updates/android-stable.json` only after its APK size and SHA-256 match the public GitHub release asset. Future releases must increase the Android version code and preserve the package and signing identity.

Private Debug APKs use a different certificate and cannot be upgraded directly to a public release.

## Update verifier tests

With JDK 17 and Python 3 available:

```powershell
python tests/test_updates.py
```

The tests exercise the production checker with controlled release metadata and HTTPS responses. CI also builds both APK variants, checks their packaging and verifies the Windows signing helper.
