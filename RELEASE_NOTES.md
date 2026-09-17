# OpenTTD Launcher 1.0.1

Now available for **Windows, macOS, and Linux**.

## Changes

- Launcher settings: dark/light themes, accent colors, text size, startup checks, window behavior, activity history, and custom game arguments.
- Restored automatic download and installation of original TTD graphics and sound, alongside local-folder import. Required files are cached for reuse.
- Windows x86, x64, and ARM64 game selection and compatibility checks, with supported emulation fallbacks.
- macOS application bundles, Intel/Apple Silicon selection, and Rosetta compatibility checks.
- Current and historical versions of official OpenTTD and JGR Patch Pack, with independent installs and repair.

## Download and run

| System | Download | Start |
| --- | --- | --- |
| Windows | `openttd-launcher-1.0.1-windows.zip` | Extract and run `launch.bat` |
| macOS | `openttd-launcher-1.0.1-macos.zip` | Extract and run `launch.command`, or `sh launch.sh` in Terminal |
| Linux | `openttd-launcher-1.0.1-linux.zip` | Extract and run `sh launch.sh` |

All packages use the same portable Java application and require **Java 21+ with desktop support** installed and available as `java`. Java and game data are not bundled. Packages are unsigned. Existing launcher preferences and installed games are retained.

Validation covers Windows x64 and Windows 11 ARM, macOS on Intel and Apple Silicon, and Linux x64. Tests include downloading and installing original TTD files. Historical game compatibility depends on the operating system; Rosetta does not support old 32-bit/PowerPC Mac games. Managing x86 games does not imply support for every 32-bit Windows/Java installation.

`SHA256SUMS.txt` contains checksums for all ZIPs. The source archive includes launcher and runtime dependency sources. Licensed under GPL-3.0-only; separately downloaded archive tools retain their upstream licenses.
