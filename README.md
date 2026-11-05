# Skull Kid — Android

Unofficial Android compatibility port of **[the skullkid](https://www.newgrounds.com/portal/view/63747)** (2002), originally created by **korded (Chance Banning)**. Uses [Ruffle](https://ruffle.rs) and the shared [FrostFrontend](https://github.com/Frostie77/FrostFrontend) Android settings library.

**Status: initial Debug APK built and installed successfully on AYN Odin 2 Portal. Gameplay and virtual controls work; quality cycling and final boss ending still require device verification. No signed public release.**

The original Flash game **is not included** in the source or APK. Supply your own legally obtained `.swf` file at first launch.

## Build and test on Windows

1. Clone the repository with submodules: `git clone --recurse-submodules https://github.com/Frostie77/Skull-Kid.git`.
2. Open **PowerShell** in the checked-out directory.
3. Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\build-test.ps1`. Requires Android SDK and JDK 17; downloads the Ruffle runtime and Gradle if needed.
4. Launch the installed debug app on your Odin and tap **Select SWF File** to import the original game.
5. Verify gameplay and the five FrostFrontend tabs before planning a signed release.

Do not put the original SWF in the repository or under `app/src/main/assets/www`. The app stores an imported copy privately, and the build script refuses to package a game SWF.

## Game compatibility and controls

The supplied `Skull_Kid.swf` is a **Flash 6** movie with a **550 × 400** stage and **18 fps**.

- Left stick / D-pad: **arrow keys** to move, and duck/stand during the gunfight
- **A**: **Space** to chainsaw/shoot
- **Y**: cycle **Low → Medium → High** through the game's own Flash `_quality` setting, without restarting
- **Start**: FrostFrontend menu
- Touchscreen: joystick/D-pad and a single **USE** action

FrostFrontend contains **Graphics, Sound, Controls, Touch Settings, Updates**. Graphics offers original 550×400 proportions or stretch; Controls supports remapping the USE and quality-cycle buttons. Y changes the Flash movie's own `_quality` property live, rather than reloading the movie. A small AVM1 input listener is inserted **in memory while serving the user's imported SWF**; the SWF saved in app-private storage is never modified or published. Movement settings, touch layout and stereo speaker testing remain available.

The stable update-feed file is deliberately empty until a signed APK has been properly released. Debug builds do not offer verified public updates.

## Known gameplay issue

The final boss death sequence has been reported to stall before the victory ending. The supplied original SWF contains boss collision and timeline actions; the underlying cause is not yet confirmed. Do not call this fixed until the full final animation and victory screen have been tested on the Odin.

## Launcher icon

Custom Skull Kid artwork is still pending. No unrelated or temporary shield icon was copied into the project.

## Credits

- **Original game (2002):** [korded (Chance Banning)](https://korded.newgrounds.com/) — [Newgrounds original](https://www.newgrounds.com/portal/view/63747)
- **Android wrapper:** Frostie77
- **Flash compatibility:** [Ruffle](https://ruffle.rs)
- **Settings library:** [FrostFrontend](https://github.com/Frostie77/FrostFrontend)

Original game rights belong to their respective owners. This unofficial, unaffiliated project does not redistribute the original Flash file.
