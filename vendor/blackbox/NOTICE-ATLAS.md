# Source provenance and local changes

Upstream: https://github.com/Black00Z/Blacks-BlackBox

Pinned revision: `40282a7bf4500948cfd598fc67e6e63114b26dd9`.

Copied modules: `Bcore`, `black-reflection`, `compiler`. The upstream `LICENSE` (Apache-2.0) is retained alongside these files. The bundled Dobby archives and other native sources originate from this pinned upstream tree; retain upstream copyright and license notices. This integration is a development build, not a statement that all transitive distribution obligations have been audited.

Atlas changes (2026-10-17):

- ARM64-only build, minimum API 26, Java source/target 17. NDK remains upstream's 29.0.13846066.
- Remove external log upload transport; clear default destination; disable automatic background logcat collection at host attach.
- Reduce manifest permissions to networking, foreground-service support, notifications, Wi-Fi state, and location. Remove duplicates. Camera, microphone, contacts, SMS, storage and vendor-specific grants are not included by this integration.
- Sanitize the generated AIDL command-line comment on Windows because backslash-u path text is an illegal Java Unicode escape. Interface definitions are unchanged.
- Correct `BLocation(Parcel)` float read order to match `writeToParcel` (speed, bearing, accuracy).
- Remove the duplicate lowercase `getComponentEnabledSetting` hook class, retaining the null-safe uppercase implementation; the two generated class files collided on Windows.
- Build the required missing `assets/empty.jar` from Atlas's inert `Empty` class with the SDK D8 tool. The optional JUnit guest asset is not included.

Host startup and adapter code live in the Atlas app, not in any imported guest APK. Guest APKs are not patched or re-signed. Android 16 and third-party guest compatibility require device testing.

2026-10-18: IPermissionManagerProxy now translates the current guest UID/package to the host identity for fine/coarse location permission queries on the modern permission service. Other identities and permissions remain unchanged; the original system service decides the result. Version 0.3.1 is an experimental compatibility fix pending device confirmation.

2026-11-08 open-source candidate changes: application-specific payload loading and compatibility hooks were removed or disabled; unprotected manifest proxy activities, services, providers and the stub receiver were made non-exported; identified account logs were redacted. Per-file modification markers were added to files differing from the pinned local upstream checkout. Runtime behavior after these changes requires device validation.

2026-11-11 open-source compliance hardening: removed unused AntiDetection file-hooks and neutralized hideXposed class-loader masking to eliminate stealth evasion and anti-detection mechanisms.
