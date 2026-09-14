# OpenTTD Launcher

A small Java 21/Swing launcher for the official OpenTTD releases on Windows, macOS, and Linux. It checks the official OpenTTD download pages, installs native releases into versioned managed folders, and launches the selected game without touching user saves or configuration.

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
- The native archive for the current platform is downloaded from the official CDN and extracted into `<install directory>/versions/<channel>-<version>`.
- User saves/configuration are not placed inside managed version folders. OpenTTD continues to use its normal user data location.
- Launcher settings are saved to `.openttd-launcher.properties` inside the user's home directory.
- Repair downloads and validates the replacement in a temporary folder before replacing the installed copy. Download or extraction failures leave the working installation intact; a failed replacement move attempts to restore the previous copy.
- Installed versions are selected using numeric version ordering, with final releases preferred over their beta/RC builds. Other channels and temporary installation folders are excluded.

The launcher uses native ZIP or tar.xz archives so it can manage files without registry changes or an uninstall workflow.

## Tests

Run `mvn test` to check version selection, channel isolation, repair success and failure, and rejection of unsafe archive paths. Installation tests use a local HTTP server and temporary folders; they do not download OpenTTD or modify your installed game.
