# 🔥 Hellboy VPN v4.2.2 — تست‌های E2E و تگ‌های تست

## 🇮🇷 فارسی
- **تست‌های سرتاسری (E2E)** با [e2e](https://e2e.tester.army) از TesterArmy: در پوشه‌ی `tests/` — باز شدن اپ، دکمه‌ی اتصال و ناوبری پایین. بدون کلید AI.
- **CI اندروید:** `.github/workflows/e2e-android.yml` روی هر push و PR یک emulator اندروید بالا می‌آورد و تست‌ها را اجرا می‌کند.
- تگ‌های تست روی ناوبری، دکمه‌ی اتصال و صفحات (بدون تغییر ظاهر برای کاربر).
- ظاهر و قالب Hellboy حفظ شده است.

## 🇬🇧 English
- **End-to-end tests** with [e2e](https://e2e.tester.army) from TesterArmy, under `tests/`: app launch, connect button, bottom navigation. No AI key needed.
- **Android CI:** `.github/workflows/e2e-android.yml` runs the flows on an emulator on each push and PR.
- Test tags on navigation, the connect button and screens; no visible UI change.
- Hellboy skin and template are kept.

# 🔥 Hellboy VPN v4.2.1 — فاز ۱ مهاجرت از Narcic Gateway (Aether)

## 🇮🇷 فارسی
- **موتور Aether** (هسته‌ی `libaether.so`): پروتکل‌های WARP (WireGuard / MASQUE / gool …) مستقیم از گوشی، به‌صورت پردازه و با SOCKS روی لوپ‌بک؛ تونل سیستم مثل بقیه‌ی سرورها به آن وصل می‌شود.
- **لینک `aether://`** (همان فرمت PattNG و Narcic): وارد کردن از کلیپ‌بورد یا QR؛ تست واقعی تاخیر با هسته قبل از اتصال.
- ثبت خودکار کلید WARP وقتی لازم است.
- **محدودیت این نسخه:** حامل‌های داخلی Psiphon و Tor داخل Aether هنوز نیستند (پروفایل‌هایی که به آن‌ها نیاز دارند رد می‌شوند). SNI spoof، OpenConnect، IKEv2، SSH، DNS tunnels و بقیه‌ی فازها در نسخه‌های بعدی می‌آیند.

## 🇬🇧 English
- **Aether engine** (`libaether.so`): WARP protocols (WireGuard / MASQUE / gool …) run as a process with a loopback SOCKS; the system tunnel connects to it like any other server.
- **`aether://` links** (PattNG / Narcic format): import via clipboard or QR; real delay test with the core before connecting.
- Automatic WARP key registration when needed.
- **Limits:** Psiphon and Tor carriers inside Aether are not in this version (profiles needing them are rejected). SNI spoof, OpenConnect, IKEv2, SSH, DNS tunnels follow in later phases.

# 🔥 Hellboy VPN v4.1.0 — روش Netra + امکانات ZeroNet

## 🇮🇷 فارسی
- **روش Netra** (پنل netrair/netra-panel): پروفایل جدید «روش Netra · Fragment» — فرگمنت دومرحله‌ای داخل هسته (ClientHello در تکه‌های 0/104/1 و بسته‌ی بعدی 114/1) + cipherSuites نترا. اولین پروفایل جایگزین برای سرورهای کلادفلر. لینک‌های خود Netra (cs و fm) دست‌نخورده اجرا می‌شوند.
- **بدون سرور (Serverless)** مثل ZeroNet 0.3.2: نه VPS نه کانفیگ — اتصال مستقیم با ClientHello تکه‌تکه، DNS رمزنگاری‌شده (DoH) و بستن QUIC. (تلگرام که آدرسش بسته است باز نمی‌شود.)
- **Hysteria2** (hy2://) با **salamander** — قبلاً پشتیبانی نمی‌شد.
- **allowInsecure** حالا کار می‌کند: گواهی سرور یک بار گرفته و پین می‌شود (مثل v2rayNG) — قبلاً این کانفیگ‌ها کنار گذاشته می‌شدند.
- **سبک اتصال**: معمولی / سریع / گیمینگ / فقط رمزدار.
- **DNS دلخواه** و **قوانین مسیریابی** (مستقیم / مسدود / همیشه از تونل؛ دامنه، IP، CIDR، geosite، geoip).
- **لیست‌های تست‌شده** (لایه‌ی ۰ و ۱ ZeroNet) جلوتر از لیست‌های بزرگ.
- کلادفلر روی پورت‌های **2053 و 8443** هم امتحان می‌شود.
- رفع باگ‌ها: `pcs` در لینک گواهی پین‌شده است نه cipher suite؛ host/sni خالی؛ قانون‌های دامنه‌ای (ایران مستقیم) بدون sniff کار نمی‌کردند؛ کارت قدیمی سایفون در تنظیمات (که هنوز کانفیگ رسمی می‌خواست) حذف شد؛ ویرایشگر Hysteria2/WireGuard.

## 🇬🇧 English
- **Netra method** (netrair/netra-panel): new "Netra · Fragment" profile — two-stage in-core fragment (ClientHello split 0/104/1, next packet 114/1) + Netra cipher suites; first fallback profile for Cloudflare servers. Netra's own links (cs, fm) run untouched.
- **Serverless** (like ZeroNet 0.3.2): no VPS, no config — direct with fragmented ClientHello, DoH and QUIC blocked.
- **Hysteria2** (hy2://) with **salamander** obfs.
- **allowInsecure** now works via automatic certificate pinning (like v2rayNG).
- **Connection style**: normal / fast / gaming / encrypted-only. **Custom DNS** and **routing rules** (direct / block / proxy).
- **Tested feeds** (ZeroNet tier 0/1) prioritized; Cloudflare links also tried on **2053 / 8443**.
- Fixes: `pcs` is a pinned cert hash, not cipher suites; empty host/sni; domain rules needed sniffing; stale Psiphon card removed; Hysteria2/WireGuard editor fields.

# 🔥 Hellboy VPN v4.0.0 — سایفون رایگان + WARP رایگان

## 🇮🇷 فارسی
- **سایفون رایگان، بدون کانفیگ:** با لیست عمومی و امضاشده‌ی سرورهای خود سایفون (۴۲۷ سرور همراه اپ + به‌روزرسانی خودکار) — مثل PattNG.
- **موتور «شیر و خورشید»** (فورک CluvexStudio): پروتکل‌های **CDN fronting** و اسکن لبه‌های CDN برای اینترنت ایران. گزینه‌ی پروتکل جدید: «CDN (پیشنهادی ایران)».
- کانفیگ رسمی سایفون حالا **اختیاری** است.
- **WARP رایگان کلادفلر** (تب سرورها): «اتصال WARP» ← ثبت خودکار (کلید روی گوشی ساخته می‌شود) ← اسکن اندپوینت‌ها ← اتصال به سریع‌ترین. **نویز** ضد فیلتر WireGuard، «اسکن WARP» و «حساب تازه». لینک WARP قابل کپی/QR برای دیگران.

## 🇬🇧 English
- **Free Psiphon, no config needed:** uses Psiphon's own public, signature-verified server list (427 servers bundled + auto refresh) — like PattNG.
- **Shir-o-Khorshid engine** (CluvexStudio fork): **CDN fronting** protocols and CDN edge scanning for Iran. New protocol option: "CDN (recommended for Iran)".
- Official Psiphon config is now **optional**.
- **Free Cloudflare WARP** (Servers tab): "Connect WARP" → automatic registration (keys generated on the phone) → endpoint scan → fastest connect. Anti-filter **noise**, "Scan WARP", "New account". WARP link copy/QR for others.
