# Hellboy / MSN-GUARD source integration

MSN-GUARD (https://github.com/mbm110/MSN-GUARD), AGPL-3.0,
is included at source revision `5fc12b8e17dba95741507dcb8303456ec5b4c65f`
(tag `v3.0.0`). Its code, resources, service, settings screen and JNI
transports are packaged **inside the Hellboy application** as the
`:msnengine` Android library. Hellboy's original UI is kept, and its
Settings screen links to the internal MSN transport control screen.

The main app and integrated transport app share a package, and each
declares a VpnService. Only one tunnel can hold Android's VPN permission
at a time. Hellboy disconnects its own session before entering the
native transport screen.

Reproduce the integrated source tree and build with:

```sh
bash tools/stage_msn_guard.sh
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleRelease -PhellboyPreview=true
```

Staging verifies the pinned Git commit and SHA256 of the upstream release
APK. The JNI files are extracted from the corresponding upstream APK,
and original source is available in the exact pinned Git tree.
`app/libs/psiphon-msn.aar` is extracted from that same tag.
The generated `msnengine/src/main` and staged binaries are excluded
from Git; they are reproducible from this script and the pinned source.

**Licensing:** MSN-GUARD is AGPL-3.0-only; the combined distributed work
must meet AGPL source and notice conditions. See the bundled full license
and the upstream source. The PSIPHON AAR and native binary third-party
notices require independent audit before unrestricted redistribution.
This integration does not assert ownership of MSN-GUARD.
