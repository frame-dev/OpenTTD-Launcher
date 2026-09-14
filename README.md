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
- JGR automatic installation supports published Windows x64/ARM64 ZIPs and Linux x64 generic tar.xz archives. macOS DMG files and platforms without a matching archive require manual installation. GitHub metadata and downloads require access to `api.github.com`, `github.com`, and GitHub's release asset hosts; API rate limits can temporarily prevent update checks.
- The native archive for the current platform is downloaded from the selected release source and extracted into `<install directory>/versions/<channel>-<version>`.
- User saves/configuration are not placed inside managed version folders. OpenTTD continues to use its normal user data location.
- Launcher settings are saved to `.openttd-launcher.properties` inside the user's home directory.
- Repair downloads and validates the replacement in a temporary folder before replacing the installed copy. Download or extraction failures leave the working installation intact; a failed replacement move attempts to restore the previous copy.
- Installed versions are selected using numeric version ordering, with final releases preferred over their beta/RC builds. Other channels and temporary installation folders are excluded.

The launcher uses native ZIP or tar.xz archives so it can manage files without registry changes or an uninstall workflow.

## Older versions

Choose a channel, click **Older versions**, choose a release in **Version**, then click **Install**. Each version stays in its own managed folder. Select an installed version and click **Launch OpenTTD** to play that exact version; **Repair** reinstalls the selected version and **Update** selects and installs the latest release.

Stable lists final releases, Testing lists beta/RC releases, and Nightly loads one archive year at a time. JGR loads 30 release records at a time and lists those with compatible archives. Click **Load more** to go further back. Official archive entries are checked for a compatible download when you install; some older releases do not have an archive for your platform. Installed versions remain selectable without internet access.

## Tests

Run `mvn test` to check version selection, channel isolation, repair success and failure, and rejection of unsafe archive paths. Installation tests use a local HTTP server and temporary folders; they do not download OpenTTD or modify your installed game.
