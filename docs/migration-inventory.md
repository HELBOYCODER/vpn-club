# Narcic → Hellboy: inventory of features (step 1)

Source of truth: code under `shared/src/commonMain` (UI and domain) and `app/src/main/java/dev/valid7996/narcicgeteway/core` (engines), in the Narcic tree that was merged into this branch at commit `256c338`.

Hellboy design reference (visual): commit `7c41864` (v4.2.2), the last Hellboy UI on this branch before the Narcic tree replaced it. A newer Hellboy line exists on `origin/hellboy-v4.3` (4.3.3, `f396c1d`), which diverged at `a2695df`. **Which of the two is the design reference is still open** (see the report).

Status legend: **H** = present in Hellboy 7c41864 (partly or fully), **M** = missing in Hellboy, **N** = integrated in the current merge with Narcic UI (needs Hellboy UI), **?** = not yet verified.

| # | Feature | Narcic code reference | Behaviour | Hellboy status | Acceptance test (to write) |
|---|---|---|---|---|---|
| 1 | Home: connect / disconnect | `ui/home/HomeScreen.kt`, `ui/home/ConnectControls.kt`, `ui/home/SlideToConnect.kt` | one control starts/stops; shows state | H (HellboyFace) / N (Narcic hero) | tap connects; state comes from service, not timer |
| 2 | Home: server card and route map | `ui/home/HomeServerSection.kt`, `ui/home/ConnectionRouteMap.kt` | active server, route/country | M | shows server name and country of the active profile |
| 3 | Home: failure banner / fault messages | `ui/connection/ConnectionViewModel.kt`, `ui/home/HomeViewModel.kt` | explains why connection failed | H (partly) | forced failure shows the reason |
| 4 | Servers list and grouping | `ui/servers/ServersScreen.kt`, `ServerCardItem.kt` | list, sort, group, ping | H | list shows latency and sorts by it |
| 5 | Import: deep link `narcicgeteway://`, clipboard, QR | `ui/servers/DeepLinkImportHost.kt`, `domain/config/DeepLinkParser.kt`, `NrgLink.kt`, `ui/components/QrCode.kt` | adds profiles | M (Hellboy has clipboard/QR for its own links) | each link type imports and appears in list |
| 6 | Manual config editor | `ui/servers/ManualConfigSheet.kt`, `domain/config/ConfigParser.kt` | edit fields of a profile | H (Hellboy editor, PattNG style) | edit a field, reconnect uses it |
| 7 | Subscriptions | `ui/servers/SubscriptionsSheet.kt`, `SubscriptionUpdateJob.kt`, `domain/config/Subscription*.kt` | fetch, refresh, headers | H (my subs) | refresh updates list |
| 8 | Auto-select (smart connect) | `ui/servers/AutoSelectUi.kt`, `domain/config/AutoSelectConfig.kt`, `ui/servers/AutoSelectUi.kt` | picks best server | H (fastest / auto) | disconnect + auto reconnect picks a live server |
| 9 | Cyber radar / background ping | `ui/components/CyberRadarIndicator.kt`, `shared/.../data/net/PingService.kt` | continuous latency | M | latency values refresh without blocking UI |
| 10 | Psiphon (sheet + engine) | `ui/servers/PsiphonSheet.kt`, `PsiphonCountryField.kt`, `core/PsiphonController.kt`, `domain/config/PsiphonProfile.kt` | run Psiphon with region/protocol | H (free Psiphon, Psiphon config) | connect with region X, exit country matches |
| 11 | Aether (WARP stacks) | `ui/servers/AetherSheet.kt`, `AetherActions.kt`, `core/AetherController.kt`, `shared/.../domain/config/Aether*.kt` | WARP over MASQUE/WG, gool | H (Phase 1: process runner, WG/MASQUE/gool basic) | aether:// link connects; carrier profiles refused with message |
| 12 | SNI spoof (no root) | `ui/servers/SniSpoofSheet.kt`, `ui/settings/RstaSpoofSheet.kt`, `core/RstaSpoofEngine.kt`, `RstaSpoofService.kt`, `RstaSpoofClient.kt`, `shared/.../domain/config/SniSpoof*.kt` | separate process, spoofs SNI | M | enable, test button reports result |
| 13 | IKEv2 | `ui/servers/Ikev2Sheet.kt`, `Ikev2CertBridge.kt`, `core/Ikev2Controller.kt`, `Ikev2Proposals.kt`, `Ikev2EventReceiver.kt`, `domain/config/Ikev2Profile.kt` | native VPN IPsec | M | profile connects, certificate import works |
| 14 | OpenConnect (Cisco AnyConnect) | `ui/servers/OpenConnectSheet.kt`, `core/OpenConnectController.kt`, `domain/config/OpenConnectProfile.kt`, `app/.../org/infradead/libopenconnect` | SSO / device code | M | connects to test endpoint, auth dialog works |
| 15 | SSH tunnel | `ui/servers/SshSheet.kt`, `domain/config/SshProfile.kt`, `SshLink.kt` | ssh dynamic socks | M | connects to test SSH host, traffic passes |
| 16 | DNS tunnels (DNSTT / VayDNS) | `ui/servers/DnsTunnelSheet.kt`, `core/DnsTunnelController.kt`, `domain/config/DnsTunnelProfile.kt` | tunnel over DNS | M | profile starts, DNS queries succeed |
| 17 | MasterDNS | `ui/servers/MasterDnsSheet.kt`, `core/MasterDnsController.kt`, `domain/config/MasterDnsProfile.kt` | MasterDNS tunnel | M | profile starts |
| 18 | Tor and bridges | `ui/settings/TorBridgesScreen.kt`, `core/tor/*`, `app/.../core/tor/*` | Tor with obfs4/snowflake/meek bridges | M | bootstrap reaches 100% with bridge set |
| 19 | Proxy chain / cross-chain | `ui/servers/ProxyChainSheet.kt`, `CrossChainSheet.kt`, `ChainPickerParts.kt` | multi-hop | M | two-hop profile connects |
| 20 | Certificate import (pinned / user CA) | `ui/servers/CertImport.kt`, `CertSourceRow.kt`, `core/CertImportAndroid.kt` | import certs | M | imported cert used by IKEv2 |
| 21 | Lock/unlock and sharing of profiles | `ui/servers/LockShareSheet.kt`, `UnlockConfigDialog.kt`, `RelayKeyDialog.kt`, `shared/.../crypto/ZsxModels.kt` (Narcic `.zsx`) | encrypted export/import | M | export then import on fresh install restores profile |
| 22 | Routing rules and rulesets | `ui/settings/RoutingScreen.kt`, `RuleEditSheet.kt`, `domain/config/RoutingMigration.kt`, `RulesetTransfer.kt`, `RuleValidation.kt` | direct / block / proxy by domain/IP/geo | H (Hellboy 4.x custom rules) | rule routes a domain direct, observed in logs |
| 23 | Per-app proxy | `ui/settings/PerAppProxyScreen.kt`, `app/.../core/RouteSplit.kt` | include/exclude apps | H (bypass apps) | excluded app bypasses tunnel |
| 24 | Live monitor | `ui/settings/LiveMonitorPage.kt` | speed, connections | M | values change with traffic |
| 25 | IP scanner | `ui/settings/IpScannerScreen.kt`, `shared/.../data/tools/IpScannerEngine.kt` | scans clean IPs | H (Cloudflare clean IP scanner) | scan produces list, selected IP applies |
| 26 | MTU optimizer | `ui/settings/MtuOptimizeRow.kt`, `shared/.../data/net/MtuOptimizer.kt`, `app/.../data/net/AndroidMtuProbe.kt` | probes MTU | M | probe result applied to tunnel |
| 27 | Telegram proxy | `ui/settings/TelegramProxyScreen.kt`, `shared/.../data/tools/TelegramProxyRepository.kt` | MTProto proxy list | M | list loads, proxy opens in Telegram |
| 28 | AI assistant (chat) | `ui/settings/AiAssistantPage.kt`, `AiChatScreen.kt`, `AiSection.kt`, `domain/ai/*`, `app/.../narcicgeteway/ai/AndroidAiBridge.kt` | chat that can operate app | M | sends message, tool call changes setting |
| 29 | Assets (geo files / rulesets) | `ui/settings/AssetsScreen.kt`, `app/.../data/assets/GeoAssetsRepository.kt`, `data/net/GeoLookup.kt` | update geoip/geosite | H (geodata) | update downloads and version shown |
| 30 | Backup and restore | `ui/settings/backup/*`, `data/backup/BackupManager.kt`, `BackupRestoreScreen.kt` | full settings backup | M | backup then restore returns same profiles |
| 31 | Appearance (themes, colours, transitions) | `ui/settings/AppearancePickers.kt`, `ColorPicker.kt`, `TransitionPicker.kt`, `ui/theme/*` | Narcic themes | H (Hellboy theme) | Hellboy theme is default and only UI skin |
| 32 | Onboarding | `ui/onboarding/OnboardingFlow.kt` | first-run flow | M | first run shows Hellboy onboarding |
| 33 | Easter egg | `ui/easteregg/*` | space game | M (optional) | — |
| 34 | Privacy / support / about / logs | `ui/settings/PrivacyPage.kt`, `SupportPage.kt`, `AboutSheet.kt`, `LogSheet.kt`, `core/AppLog.kt`, `LogcatTail.kt` | info and logs | H (log export) | log can be exported |
| 35 | Update checker / nudge | `ui/update/NudgeHost.kt`, `data/update/UpdateChecker.kt` | checks new release | H (update check) | points to this repo's releases |
| 36 | Quick settings tile, widget, boot receiver | `core/NrgTileService.kt`, `NrgWidgetProvider.kt`, `BootReceiver.kt` | tile toggles VPN | M | tile toggles tunnel |
| 37 | Relay / NrgRelay | `shared/.../data/config/NrgRelay.kt`, `shared/.../NrgRelay` | relay helper | ? | — |
| 38 | Psiphon / WARP free profiles | (Hellboy-specific) Hellboy 7c41864: `corex/PsiphonRunner.kt`, `corex/WarpManager.kt` | free Psiphon, WARP | H only (not Narcic) | keep working after merge |
| 39 | Serverless (no server) | (Hellboy-specific) Hellboy 7c41864: `corex/XrayCore.kt` | direct with fragment | H only | keep working |
| 40 | Panel deploy (BPB / Zeus) | (Hellboy-specific) Hellboy 7c41864: `corex/CloudflareDeployer.kt`, `ui/screen/PanelUi.kt` | install panel to Cloudflare | H only | keep working |

## Notes
- Engines in Narcic that Hellboy does not ship: sing-box, Tor, DNSTT, VayDNS, MasterDNS, OpenConnect, IKEv2, SSH, SNI spoof, AI. These need native libraries (`app/src/main/jniLibs`, `libs/nrgcore.aar`). `nrgcore.aar` (119 MB) is built locally with `tools/build-nrgcore.sh` and is not in git.
- Features present only in Hellboy (rows 38–40) must survive the merge; the current merged tree lost them from the navigation.
- Current merged tree: Narcic UI with Hellboy skin (theme, face hero, concrete wall). Rows marked N need Hellboy UI, not the Narcic one.


## Port status (final for this branch)

"Native" = Hellboy code. "Embedded" = Narcic's own screen shown inside the Hellboy shell under Hellboy's theme. "Not ported" = not reachable. Nothing below has been run on a device; see "Verified" at the end.

| # | Feature | Where it lives now |
|---|---|---|
| 1 | Connect / disconnect | Native: Home button (HellboyFace), one state for both engines |
| 2 | Server card, route map | Native card with name and duration. **Route map and country not ported** |
| 3 | Failure message | Native: Home shows the failure of either engine |
| 4 | Servers list | Native list for the Hellboy engine; Narcic list embedded in "Protocols" |
| 5 | Import (deep link, clipboard, QR, file) | Embedded (Protocols tab, `DeepLinkImportHost` always composed); Hellboy's own import dialog stays |
| 6 | Manual config editor | Native (Hellboy servers) and embedded (Narcic profiles) |
| 7 | Subscriptions | Native (Hellboy "my subscriptions") and embedded |
| 8 | Auto-select | Embedded |
| 9 | Cyber radar | **Not ported** (decoration); latency lives in the embedded list |
| 10 | Psiphon | Native free Psiphon (Hellboy engine) and embedded Narcic Psiphon sheet |
| 11-20 | Aether, SNI spoof, IKEv2, OpenConnect, SSH, DNS tunnels, MasterDNS, Tor, chains, certificates | Embedded (Protocols tab and Advanced page) |
| 21 | Lock, share, encrypted export | Embedded |
| 22 | Routing rules | Native rules dialog (Hellboy engine) and embedded RoutingScreen (Narcic engine) |
| 23 | Per-app proxy | Native bypass list (Hellboy engine) and embedded (Narcic engine) |
| 24 | Live monitor | Embedded (Advanced) |
| 25 | IP scanner | Native Cloudflare scanner; Narcic scanner embedded (Advanced) |
| 26 | MTU optimizer | Embedded (Advanced) |
| 27 | Telegram proxy | Embedded (Advanced) |
| 28 | AI assistant | Embedded (Advanced) |
| 29 | Geo assets | Embedded (Advanced) |
| 30 | Backup and restore | Embedded (Advanced) |
| 31 | Appearance | Native (theme, effects, button style) |
| 32 | Onboarding | Native, new (three cards) |
| 33 | Easter egg | **Not ported** (optional) |
| 34 | Logs | Native export and embedded Narcic log sheet |
| 35 | Update check | Embedded nudge pointing at this repo, plus Hellboy's own check |
| 36 | Tile, widget, boot receiver | Kept as system components; they control the Narcic engine and stop Hellboy's tunnel first |
| 37 | Relay | Embedded/internal |
| 38-40 | Free Psiphon/WARP, serverless, panel deploy | Native, unchanged |

### Verified
- JVM unit tests: Narcic suite, Hellboy `corex` suite (61), `EngineBridge` (7) and a Robolectric walk of the Hellboy shell (5): navigation, advanced page, import jump, onboarding, connect button.
- Release build for arm64-v8a, armeabi-v7a and x86_64; a local signing run with a throw-away key confirmed the signing path, v2 signature, package, versionCode and native libraries.
- **Not verified:** any behaviour on a device or emulator (VPN permission, tunnels, engine switching, the embedded pages' look), the GitHub Actions workflow (never run), the real release key.
