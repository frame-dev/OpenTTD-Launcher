# Third-party notices

Runtime dependencies retain their original terms:

| Component | Version | License |
| --- | --- | --- |
| Google Gson | 2.11.0 | Apache-2.0 |
| Error Prone annotations | 2.27.0 | Apache-2.0 |
| Apache Commons Compress | 1.28.0 | Apache-2.0 |
| Apache Commons Codec | 1.19.0 | Apache-2.0 |
| Apache Commons IO | 2.20.0 | Apache-2.0 |
| Apache Commons Lang | 3.18.0 | Apache-2.0 |
| XZ for Java | 1.10 | 0BSD |

The binary ZIP includes dependency JARs with their license and notice files under `third-party`. The matching source ZIP includes dependency source JARs. Update this inventory when dependencies change. Build and test tools are not bundled.

Game binaries and original TTD assets are not included. The optional TTD download retrieves assets from tt-ms.de and invokes a separately downloaded 7-Zip 26.03 executable as an external program. No Junrar or UnRAR library is linked into the launcher. The downloaded tool retains its upstream License.txt and readme.txt; its SHA-256 is verified before extraction. Upstream binaries, source, and licensing information are available at https://github.com/ip7z/7zip/releases/tag/26.03 and https://www.7-zip.org/license.txt.
