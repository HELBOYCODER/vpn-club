# Automatic connection behavior

The user's requested workflow is owned by one `SmartConnectionLoop`, rather than separate racing connection/watchdog/pool jobs.

1. On every Connect: fresh feed fetch with one 24-second global deadline and bounded per-feed requests.
2. Randomized tunnel tests keep up to five candidates, ordered by measured latency. Discovery has a 90-second testing budget.
3. Activate candidates in order. A real HTTPS response and bounded binary download must satisfy latency ≤2 seconds and download ≥64 KiB/s. Ping-only candidates are rejected.
4. While connected, test quality every 30 seconds. Two dead results or three weak results trigger recovery. A healthy sample resets failure counters. Idle user bandwidth is irrelevant.
5. Try remaining backups; exhaustion fetches feeds again. Rejected identities are excluded for five minutes to avoid immediate loops. Empty scans back off 5–30 seconds.
6. When the physical network disappears, wait instead of blaming every server. On return, start fresh discovery.
7. UI, widget/tile and notification Stop cancel the session. Stale service commands cannot activate a server after a newer session/stop.

Removed capabilities: WARP registration/endpoint scan, WireGuard runtime, Serverless outbound, Psiphon native build/assets/runtime, Cloudflare panel deployment and clean-IP scanning/injection UI. The original drawing code and skin assets remain unchanged.

Validation covers deterministic recovery/cancellation, retained protocol/routing tests and emulator navigation/search/stop. Actual successful tunneling through public third-party servers on the user's carrier cannot be guaranteed by CI.
