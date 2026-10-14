# Third-party components / اجزای شخص ثالث

| Component | Use | License |
|---|---|---|
| [Psiphon tunnel-core](https://github.com/Psiphon-Labs/psiphon-tunnel-core) (ConsoleClient, built by `tools/build_psiphon.sh`) | Psiphon over server | **GPL-3.0** |
| Xray-core (PattNG fork, `libv2ray.aar`) | VPN core | MPL-2.0 |
| hev-socks5-tunnel (`hev-htproxy.aar`) | TUN → SOCKS | MIT |
| [Metal Mania](https://fonts.google.com/specimen/Metal+Mania) font (`res/font/hellboy_font.ttf`) | Hellboy-style titles | SIL OFL 1.1 (`tools/fonts/MetalMania-OFL.txt`) |
| [obra/superpowers](https://github.com/obra/superpowers) skills (`.claude/skills`) | dev tooling | MIT |

> Bundling GPL-3.0 Psiphon means distributed builds of this app should be released under GPL-3.0-compatible terms with source available.
> Psiphon network access requires an official configuration (PropagationChannelId/SponsorId…) from Psiphon Inc.; it is not included.

- Psiphon options (direct/over-server mode, protocol presets incl. Conduit in-proxy, egress region, aggressive establishment) modeled on MahsaNG by GFW-knocker (GPL-3.0): https://github.com/GFW-knocker/MahsaNG
- One-tap Cloudflare panel install modeled on BPB-Wizard by bia-pain-bache (GPL-3.0): https://github.com/bia-pain-bache/BPB-Wizard — BPB Panel code is downloaded at install time from https://github.com/bia-pain-bache/BPB-Worker-Panel (GPL-3.0), unmodified.
- Zeus Panel (proprietary, personal non-commercial use) is downloaded at install time from its official repository https://github.com/panel-zeus/Z-E-U-S onto the user's own Cloudflare account; it is not bundled or modified by this app.
- Psiphon engine built from the Shir-o-Khorshid fork (CluvexStudio/psiphon-tunnel-core, branch shirokhorshid, GPL-3.0) as pinned by Aether/PattNG; free mode uses Psiphon's public, signature-verified server list (assets/psiphon_servers.dat) and public channel, following patterniha/Aether (AGPL-3.0).
- Cloudflare WARP: device registration via the public WARP API; keys generated on-device (X25519, RFC 7748).
- ZeroNet by zeghostwriter (MIT): https://github.com/zeghostwriter/ZeroNet — features re-implemented on the Xray engine (serverless profile, connection styles, routing profiles, DNS choice, tested feed list incl. its crowd-data verified list, Cloudflare alt ports, empty-host fix). No ZeroNet code is bundled.
- Netra Panel by netrair (MIT): https://github.com/netrair/netra-panel — default cipher-suite list and two-stage finalmask fragment values reused for the "Netra" profile.

## Netra / Vazirmatn

- Netra Panel: https://github.com/netrair/netra-panel, MIT, pinned `9f2c7824fee248005bb0bb60b3da63914fd21b8c`. Worker is downloaded during user-authorized installation, not bundled or modified.
- Vazirmatn: SIL Open Font License 1.1. Font and notice sourced from ZeroNet at `e7f22ed7f40f267a91fd592677d237fdab066931`. Notice bundled in `app/src/main/assets/licenses/Vazirmatn-OFL.txt`.

The pinned Netra worker is hardened at installation: its `skipAuth` handler returns HTTP 403 before writing `authDisabled`. The upstream license is retained.
