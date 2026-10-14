# ZeroNet comparison / تطبیق با ZeroNet

Reviewed upstream: `zeghostwriter/ZeroNet` at `e7f22ed7f40f267a91fd592677d237fdab066931`.
Hellboy baseline: `a2695df50df924556862fcfc0cb8fe56425b0e3f` (4.1.0).
Netra worker: `9f2c7824fee248005bb0bb60b3da63914fd21b8c`, verified by SHA-256 before deployment.

This is a source comparison, not a claim of complete parity or measured performance.
«داشتن قابلیت» با «تأیید اتصال روی اینترنت ایران» متفاوت است. برای اتصال واقعی باید روی اپراتور کاربر تست شود.

| Capability | Hellboy 4.2 | Evidence / limitation |
|---|---|---|
| VLESS, REALITY, VMess, Trojan, Shadowsocks, XHTTP | Existing | PattNG Xray core; imported links and protocol editor |
| Hysteria2 + salamander | Existing | 4.1 parser/config tests |
| Free WARP + WireGuard noise | Existing | WarpManager / XrayCore; WireGuard only |
| Serverless + fragmented ClientHello | Existing, DNS hardened | Direct connection; cannot bypass IP blocks |
| Normal / fast / gaming / encrypted connection styles | Existing, gaming fixed | Gaming no longer performs watchdog server switching |
| Prioritize tested public feeds / local history | Existing | HellboyEngine / stored tested servers |
| Clean Cloudflare scanner and selected IP injection | Existing | CloudflareScanner / IranVariants |
| Per-app exclusions and Iranian direct routing | Existing | Tun2Socks / routing rules |
| Editable configurations, copy, QR display, share | Existing | ConfigDialogs / ShareLinkBuilder |
| Quick personal panel | Added Netra replacing BPB install | Dedicated KV, random UUID/password/path, preset panel password, upstream Skip Login bypass disabled; existing BPB subscriptions preserved |
| Remote encrypted DNS | Added IP-addressed HTTPS DoH | Custom DNS validation; no system DNS fallback after DoH failure |
| Authenticated LAN HTTP / SOCKS sharing | Added | Xray service only, 10819/10818; reconnect after changes; not Psiphon-only mode |
| Quick Settings tile / home shortcut widget | Added | Activity obtains VPN consent; widget is a shortcut, not a live status display |
| Server search, protocol filters, favourites | Added | Applies to selected servers; favourite identity stored as SHA-256 |
| Import text/base64 from files and Android Share | Added | Files limited to 1 MiB |
| App update detection | Fixed | Stable semantic version comparison; uses tunnel-aware HTTP |
| UI customization / Persian text | Preserved and refined | Original Hellboy, orb, concrete, fire/blood artwork and palette; Vazirmatn text font |
| WARP MASQUE (HTTP/2 and HTTP/3) | Missing | Requires a native core implementation, not a UI switch |
| WARP hybrid / reverse hybrid / automatic transport race | Missing | Current WireGuard engine has no equivalent verified orchestration |
| Zray leastPing hot reload / native Rust engine | Missing | Hellboy uses Xray/Psiphon/hev, not ZeroNet JNI |
| Crowd result uploads / relay | Missing | Reads verified public feeds; does not transmit user's private configs |
| Trusted-network rules / preferred countries / named routing profiles | Missing | Current country search and routing editor do not replace these |
| FakeDNS, advanced decoys, anti-sanction DNS, DoT | Missing | Requires DNS/routing/core work and end-to-end tests |
| Kill switch during discovery and failed reconnection | Missing | Normal VpnService is not a verified equivalent |
| Camera QR scan / languages beyond Persian | Missing | QR display and text sharing exist; no camera scan/localization parity |
| UI/VPN process isolation | Missing | Engine still runs in the application's process |
| Full in-app APK verification/installation | Missing | Opens release download page; does not silently install APKs |

## Validation

CI must pass unit tests, Android lint, APK builds and emulator launch before publishing.
External-network tests remain opt-in with `HELLBOY_LIVE_TESTS=1`; their result on a GitHub runner does not prove connectivity on an Iranian mobile network.
Netra installation requires the user's own Cloudflare token; a real deployment is not performed by CI.

If the original signing key is unavailable, CI creates a **prerelease preview**, package `com.helboy.vpnclub.preview`, installed alongside the original app. It must not be presented as an in-place upgrade.
