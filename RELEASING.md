# Publishing a release

1. Confirm the POM version and release notes. Run `pwsh -File scripts/package-release.ps1` with PowerShell 7, Java 21+, and Maven 3.9+. It runs tests and creates Windows/macOS/Linux binary ZIPs, a source ZIP and SHA-256 checksums under `target/release`.
2. Extract the binary ZIP into a clean folder. Launch with Java 21 and check release refresh, install, launch, repair, and settings persistence. Confirm native CI passes on all supported platforms.
3. Commit the intended changes and tag that exact commit `v1.0.1`, matching the POM version. Upload all four ZIPs and `SHA256SUMS.txt` together to a draft GitHub release. Use `RELEASE_NOTES.md` as its description.
4. Verify downloaded checksums and publish the draft after review.

Keep the matching source ZIP available alongside each binary release. It includes launcher source, build scripts, and runtime dependency sources. Never bundle installed games, original TTD data, settings, or credentials. Packages are unsigned and require Java separately.

CI uploads packages for review; it does not automatically publish releases. Windows validation alone does not establish native Mac/Linux behavior.
