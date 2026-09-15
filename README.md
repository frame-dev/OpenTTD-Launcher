# OpenTTD Launcher

A small Java 21/Swing launcher for official OpenTTD releases and JGR's Patch Pack. It checks release sources, installs native releases into versioned managed folders, and launches the selected game without touching user saves or configuration.

## Requirements

- Windows 10/11, macOS, or Linux
- JDK 21 or newer
- Maven 3.9+
- Internet access to `www.openttd.org` and `cdn.openttd.org`

## Build and run

```powershell
mvn clean package
java -jar target/openttd-launcher-1.0.0.jar
```

On Windows, `scripts\\build.bat` builds the JAR and `scripts\\run.bat` starts it. On macOS/Linux, run `sh scripts/build.sh` and `sh scripts/run.sh`.

## Behavior

- Stable, Testing, and Nightly metadata is read from the official OpenTTD pages.
- JGR Patch Pack reads the latest release from [JGRennison/OpenTTD-patches](https://github.com/JGRennison/OpenTTD-patches/releases). Select it in the channel menu to install, update, repair, or launch it independently of official releases. Its selection is remembered across restarts.
- JGR automatic installation supports published Windows x64/ARM64 ZIPs, Linux x64 generic tar.xz archives, and macOS universal DMGs. Platforms without a matching archive require manual installation. GitHub metadata and downloads require access to `api.github.com`, `github.com`, and GitHub's release asset hosts; API rate limits can temporarily prevent update checks.
- The native archive for the current platform is downloaded from the selected release source and extracted into `<install directory>/versions/<channel>-<version>`.
- User saves/configuration are not placed inside managed version folders. OpenTTD continues to use its normal user data location.
- Launcher settings are saved to `.openttd-launcher.properties` inside the user's home directory.
- Repair downloads and validates the replacement in a temporary folder before replacing the installed copy. Download or extraction failures leave the working installation intact; a failed replacement move attempts to restore the previous copy.
- Installed versions are selected using numeric version ordering, with final releases preferred over their beta/RC builds. Other channels and temporary installation folders are excluded.

The launcher uses native ZIP, tar.xz, or macOS DMG downloads so it can manage files without registry changes or an uninstall workflow.

## Older versions

Choose a channel, click **Older versions**, choose a release in **Version**, then click **Install**. Each version stays in its own managed folder. Select an installed version and click **Launch OpenTTD** to play that exact version; **Repair** reinstalls the selected version and **Update** selects and installs the latest release.

Stable lists final releases, Testing lists beta/RC releases, and Nightly loads one archive year at a time. JGR loads 30 release records at a time and lists those with compatible archives. Click **Load more** to go further back. Official archive entries are checked for a compatible download when you install; some older releases do not have an archive for your platform. Installed versions remain selectable without internet access.

## macOS disk images

On a Mac, select JGR Patch Pack (or an official release), choose a version, and click **Install**. DMGs are mounted read-only with `hdiutil`; `ditto` copies the app bundle into the managed version folder, preserving permissions and links. The image is detached after copying, including when copying fails. **Launch OpenTTD** opens the copied application through macOS. Cached TTD files stay outside the app bundle. macOS security checks remain in effect.

DMGs require macOS; Windows and Linux continue to use their own native downloads.

## Original TTD graphics and sound

For an older release that asks for original Transport Tycoon Deluxe files, install and select that version, then click **Set up TTD files**. The launcher downloads the [TTD archive from tt-ms.de](https://www.tt-ms.de/downloads/ttd302011.rar) and imports only `trg1r.grf`, `trgcr.grf`, `trghr.grf`, `trgir.grf`, `trgtr.grf`, and `sample.cat`. It does not run the original installer or import music.

Launch also detects the legacy `data/openttd.grf` layout without base-set descriptors (such as OpenTTD 0.5.3). If required files are missing, it prepares them automatically and verifies them before starting the game. Empty required files are replaced from the cache. Setup errors prevent launch and appear in the launcher; starting a process is reported as “Launch requested,” with an exit status when it closes.

These files are cached under `<install directory>/ttd-data` and copied beside the selected OpenTTD executable into both `data` (older releases) and `baseset` (newer releases). Existing files are preserved. Once set up, the cache is reused for future installs, repairs, and launches without another download. The original game data is downloaded on demand and is not bundled with the launcher or committed to this repository.

## Tests

Run `mvn test` to check version selection, channel isolation, repair success and failure, and rejection of unsafe archive paths. Most installation tests use a local HTTP server and temporary folders. On macOS, integration tests also create and mount a test DMG and download/install the published JGR 0.73.2 DMG into a temporary folder. No tests modify your installed game. GitHub Actions runs the suite on Windows, Linux, and macOS.

## Platform compatibility

The launcher recognizes current `macos-universal` and historical `macosx-universal` / `macosx` packages. On macOS, ZIP extraction uses `ditto` to preserve application permissions and links; DMGs use the mounted-bundle workflow. Nested app bundles are detected. Native Mac validation rejects PowerPC/32-bit-only binaries with an explanation, since modern macOS cannot run them. Older Intel-only 64-bit releases on Apple Silicon may require Rosetta and may have other operating-system limitations.

Windows x64 prefers 64-bit ZIPs and falls back to 32-bit ZIPs for early releases. Windows x86 and ARM64 select their own architecture; ARM64 does not silently install a different architecture.

A September 2026 snapshot of 241 official Stable/Testing release directories is covered by the archive-selection tests: 234 publish recognized Windows ZIPs and 214 publish recognized Mac archives. This validates download selection, not gameplay compatibility for every historical binary. Some early Windows releases publish only installers, some releases have no Mac build, and old operating-system dependencies cannot be supplied by the launcher. Native CI installs official 15.3 and 1.5.3 on Windows/macOS, plus Windows 0.3.6 and the JGR Mac DMG. The current Mac binary is also checked using its help command.
