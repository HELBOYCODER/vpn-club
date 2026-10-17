# Native MSN transport engines inside Hellboy VPN

Upstream source: [MSN-GUARD](https://github.com/mbm110/MSN-GUARD), AGPL-3.0, pinned at commit `5fc12b8e17dba95741507dcb8303456ec5b4c65f` (`v3.0.0`).

Hellboy remains the only user-facing application and UI. Upstream native VPN services, tunnel managers and JNI binaries are embedded in the `:msnengine` Android library. The native transport's standalone `MainActivity`, shade-collapse activity, and QS tile are removed from the merged manifest; Hellboy's own Compose Settings and home dial control the real native connection through `NativeTransportBridge`, status broadcasts and traffic counters. Native notifications return to Hellboy's launcher.

The bridge transfers Hellboy DNS, bypassed apps and Iran bypass settings into the native service's preferences before starting it. Only one Android VpnService should own the active tunnel; switching modes stops the previous session.

Xray is process-isolated via upstream `libxray.so`. This removes the conflicting PattNG GoMobile bridge while preserving the native Psiphon GoMobile bridge. Its VLESS/VMess/Trojan/Shadowsocks outbound configs are translated to standard Xray JSON. Core availability is verified by Gradle and Android emulator smoke tests.

Rebuild:

```bash
bash tools/stage_msn_guard.sh
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleRelease -PhellboyPreview=true
```

The stage script clones and verifies the pinned Git commit and verifies the upstream release APK against upstream SHA256SUMS before extracting native libraries. The corresponding Psiphon AAR is included from the same revision. The generated source/JNI files are not committed, but reproducible.

**Licensing and security:** The combined distributed application must comply with MSN-GUARD's AGPL-3.0 source and attribution requirements and all third-party native/Go dependency license notices. Build and emulator tests do **not** prove successful connectivity on every carrier, or verify trustworthiness of public relay endpoints. Source changes remain visible in this repository.
