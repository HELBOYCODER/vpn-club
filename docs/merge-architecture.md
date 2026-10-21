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
- The launcher is Narcic's `MainActivity`, because it already owns every Android launcher the protocol pages need (file picker, QR scanner, certificate install, IKEv2 consent, deep links, OpenConnect dialogs). It renders the Hellboy shell (`com.helboy.vpnclub.ui.screen.AppRoot`); the old Hellboy activity and Application were removed.
- The Hellboy shell has five tabs: Home, Servers, Protocols, IP scanner, Settings. Home, Servers, Scanner, Settings, onboarding and the navigation are Hellboy code.
- **Boundary, stated plainly:** the "Protocols" tab and the "Advanced tools and settings" page embed Narcic's own Compose screens (`ServersScreen`, `SettingsScreen`, the protocol sheets). They are themed with Hellboy's palette through the `hellboy` theme profile and sit on Hellboy's wall, but their layouts are Narcic's. Re-implementing roughly 30 000 lines of those screens natively was not done.

**Two engines, one tunnel (`bridge/EngineBridge.kt`):**
- Hellboy engine (`corex`): free Psiphon, free WARP, serverless, Cloudflare clean-IP scanner and injection, panel deploy, Hellboy server list. Service: `HellboyVpnService`.
- Narcic engine: every other protocol and the profile/subscription store. Service: `NrgVpnService`.
- Only one tunnel runs. `HellboyVpnService.beforeStart` and `AndroidVpn.beforeStart` stop the other engine first, in every start path (including the quick-settings tile, widget and boot receiver). Home shows whichever engine is running (`EngineBridge.overlay`); when nothing is running the big button starts the engine the user used last (`AppSettings.lastEngine`).
- Both engines call `Libv2ray.initCoreEnv` with their own asset directory; each start re-initialises it, which is safe only because the tunnels never overlap. Hellboy-side tests (scan/inject) run while a Narcic tunnel is up use whichever environment was set last.

**Data and upgrades:** `applicationId` stays `com.helboy.vpnclub`, and Hellboy's `SharedPreferences` (`vpnclub_settings`) and stores are untouched, so an upgrade over an older Hellboy keeps its servers, subscriptions, settings, Psiphon config and WARP account. Narcic's data lives in new stores. People coming from the separate Narcic package cannot be upgraded in place (different package); they use the backup/restore page under "Advanced tools and settings".
