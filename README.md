# Skull Kid — Android

Unofficial Android compatibility port of **[the skullkid](https://www.newgrounds.com/portal/view/63747)** (2002), originally created by **korded (Chance Banning)**. Uses [Ruffle](https://ruffle.rs) and the shared [FrostFrontend](https://github.com/Frostie77/FrostFrontend) Android settings library.

**Status: Full gameplay and the boss ending are verified on AYN Odin 2 Portal. The v1.0.0 signed public release is being prepared.**

The original Flash game **is not included** in the source or APK. Supply your own legally obtained `.swf` file at first launch.

## Installation and original game download

1. Download the signed Android APK from [Releases](https://github.com/Frostie77/Skull-Kid/releases).
2. Download the [original Skull Kid SWF](https://uploads.ungrounded.net/63000/63747_skullkid.swf), or use your own copy.
3. Save the SWF in your device's Downloads folder.
4. Open Skull Kid, choose **Select SWF File**, and import it. The app remembers the file for later launches.

If the SWF opens in your browser, use **Save link as**.

- [Direct SWF download](https://uploads.ungrounded.net/63000/63747_skullkid.swf)
- [Original Newgrounds page and creator credits](https://www.newgrounds.com/portal/view/63747)

Requires **Android 8.0 or newer**. The game file is supplied separately and is not bundled in the APK.

## Build and test on Windows

1. Clone the repository with submodules: `git clone --recurse-submodules https://github.com/Frostie77/Skull-Kid.git`.
2. Open **PowerShell** in the checked-out directory.
3. Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\build-test.ps1`. Requires Android SDK and JDK 17; downloads the Ruffle runtime and Gradle if needed.
4. Launch the installed debug app on your Odin and tap **Select SWF File** to import the original game.
5. Verify gameplay and the five FrostFrontend tabs before planning a signed release.

To prepare a signed public APK after the build tools are ready:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-public-release.ps1
```

The helper keeps the permanent release keystore outside the repository, makes a backup, verifies the approved icon and APK signature, and stages the APK, checksum, release notes and exact update feed under `release`. It does not install or publish the app. Publish the staged feed after the matching APK is live on GitHub.

Do not put the original SWF in the repository or under `app/src/main/assets/www`. The app stores an imported copy privately, and the build script refuses to package a game SWF.

## Game compatibility and controls

The supplied `Skull_Kid.swf` is a **Flash 6** movie with a **550 × 400** stage and **18 fps**.

- Left stick / D-pad: **arrow keys** to move, and duck/stand during the gunfight
- **A**: **Space** to chainsaw/shoot
- **Start**: FrostFrontend menu
- Touchscreen: joystick/D-pad and a single **USE** action

FrostFrontend contains **Graphics, Sound, Controls, Touch Settings, Updates**. Graphics offers original 550×400 proportions or stretch; Controls supports remapping the USE action. Rendering quality uses Ruffle's default High setting; there is no quality shortcut. Movement settings, touch layout and stereo speaker testing remain available. Release builds serve the imported SWF unchanged. Debug builds add temporary timeline traces in memory.

The stable update-feed file is deliberately empty until a signed APK has been properly released. Debug builds do not offer verified public updates.

## Boss ending verification

The full boss death animation and victory screen have passed local Ruffle 0.7.1 testing and on-device testing on AYN Odin 2 Portal. Held controller and touch keys generate keyboard repeat events to match the original Flash keyDown controls.

At the boss, release **USE** after the first cut, approach again, then hold **USE** through the second cut.

## Launcher icon

The approved Skull Kid launcher artwork is included in source and verified by `approved-icon.sha256.json` before public builds.

## Credits

- **Original game (2002):** [korded (Chance Banning)](https://korded.newgrounds.com/) — [Newgrounds original](https://www.newgrounds.com/portal/view/63747)
- **Android wrapper:** Frostie77
- **Flash compatibility:** [Ruffle](https://ruffle.rs)
- **Settings library:** [FrostFrontend](https://github.com/Frostie77/FrostFrontend)

Original game rights belong to their respective owners. This unofficial, unaffiliated project does not redistribute the original Flash file.
