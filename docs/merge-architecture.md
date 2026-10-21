# Merge architecture (decided)

**Runtime:** one Xray runtime — Narcic's `nrgcore.aar` (gomobile of AndroidLibXrayLite, with sing-box, Psiphon, DNSTT, VayDNS, MasterDNS). Hellboy's `libv2ray.aar` is removed.
- Hellboy calls only six `libv2ray` members (`newCoreController`, `initCoreEnv`, `measureOutboundDelay`, `fetchTlsCertSha256`, `fetchQuicCertSha256`, `CoreController`/`CoreCallbackHandler`); all are present in `nrgcore`.
- `nrgcore` contains the Xray features Hellboy relies on (`finalmask`, `noises`, `mlkem`, `pinnedPeerCertSha256`, `xhttp`, `ech`).
- Two copies of `libv2ray` cannot coexist (same class name, both ship `libgojni.so`), so this is the only option.

**Tunnel:** Hellboy's `hev-htproxy.aar` stays (package `hev.htproxy`, no conflict with `nrgcore`).

**Code layout (Android module `app`):**
- Narcic base: `dev.valid7996.narcicgeteway` (engine, domain, core, services). Its module builds today.
- Hellboy engine and UI: `com.helboy.vpnclub.corex` and `com.helboy.vpnclub.ui` (copied from `7c41864`), with Hellboy resources mapped into the module's R.
- Shared domain: Narcic `ConfigRepository` stores every profile; Hellboy's engine reads Xray profiles from it through their share links.
- The launcher activity and all user screens are Hellboy's. Narcic's Compose UI (`shared/.../ui`, `MainActivity`) is not on any user path.
