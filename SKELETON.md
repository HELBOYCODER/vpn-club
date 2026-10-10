# VPN Club — Project Skeleton (Single Source of Truth)

>این فایل وضعیت دقیق پروژه است. هر تغییر: اول اینجا آپدیت، بعد کامیت.
>هرگز از حافظه/خلاصه‌ی چت حدس نزن — اینجا را بخوان.

## 1. معماری (ثابت)

```
UI (OneButtonScreen — یک دکمه)
 └ MainViewModel.toggle()
    └ VpnClubEngine.autoConnect()
       ├ Phase.FETCHING    SubscriptionFetcher.fetchAll(21 منبع، 6 thread)
       ├ Phase.SCANNING_CF CloudflareScanner.scan (SSLContext+SNI speed.cloudflare.com، UA لازم)
       ├ Phase.TESTING     غربال TCP (1.5s) → استخر 30 → تست تونل واقعی
       │    برای هر کاندید (رندوم): برای هر IranVariants (mci-ech → mci-ipv6-ff → irancell-ff):
       │      FragmentProxy محلی + XrayCore.setVariant(v) + xray.start() → HealthTester
       │      (tcpPing + دانلود 2MB + آپلود 400KB از speed.cloudflare.com با UA "VPNClub/1.0")
       │      اولین isUsable → lastVariantId ثبت → خروج
       ├ Phase.CONNECTING  CurrentConfigHolder.save(config, variantId) + VpnClubService.start()
       └ Phase.CONNECTED / FAILED(با lastErr در message)
VpnClubService (foreground): CurrentConfigHolder.load + loadVariant → xray.start → Tun2Socks(hev) تونل سراسری
```

## 2. پروفایل‌های ایران (IranVariants.kt — طبق دستور کارفرما)

| id | فایروال | آدرس | fp | echConfigList | cipherSuites | finalMask | alpn |
|---|---|---|---|---|---|---|---|
| mci-ech | همراه‌اول | IP تمیز CF | chrome | `cloudflare-ech.com+udp://1.1.1.1` | خالی | خالی | h2,http/1.1 |
| mci-ipv6-ff | همراه‌اول | IPv6 رندوم `2a06:98c1:312x::` | chrome | خالی | خالی | tlshello-0-len | h2,http/1.1 |
| irancell-ff | ایرانسل | IP تمیز CF | **unsafe** | خالی | **semi-python** | tlshello-0-len | **http/1.1** (ws) |

finalmask JSON (فرمت فورک patterniha — تاییدشده با باینری): هر fragment با **`length`/`delay`/`maxSplit` به‌صورت رشته‌ی تکی**، نه آرایه `lengths`. مثلا: fragment1: packets `tlshello`, length `104`; fragment2: packets `1-1`, length `114`. آرایه `lengths` → `LengthMin can't be 0` → parse fail.

## 3. نکات بحرانی (هرگز خراب نشود)

1. **Libv2ray.initCoreEnv(assetDir, deviceId) + کپی geoip.dat/geosite.dat از assets به getDir("assets") قبل از اولین startLoop** — بدون این هسته همیشه fail می‌شود (باگ ریشه‌ای v1.4.1).
2. بعد از startLoop تا ۵ ثانیه منتظر `isRunning` بمان، بعد تست بزن.
3. CurrentConfigHolder لینک **تزریق‌شده** را ذخیره کند (toShareLink) نه raw — وگرنه IP تمیز گم می‌شود.
4. تزریق IP تمیز CF فقط برای دامنه‌هایی که resolve‌شان واقعاً پشت CF است (isBehindCf).
5. health: MIN_DOWNLOAD_BPS=20KB/s — کانفیگِ فقط-پینگ رد می‌شود.
6. variant برنده باید هم در تست و هم در VpnClubService اعمال شود (current_variant.id).
7. هسته `.so` = patterniha/Xray-core 2026-10-08 — finalmask/ECH ساپورت می‌شود.
8. GH release: اول tag ref بساز، بعد release، بعد asset؛ بدنه‌ی فارسی/ایموجی بلند نده (silent fail).
9. **deviceId برای initCoreEnv** = base64 URL_SAFE/NO_PADDING/NO_WRAP یک آرایه‌ی ۳۲ بیتی (ANDROID_ID) — مثل v2rayNG. UUID hex → خطای «BaseKey must be...» در هسته (باگ v1.5.2).
10. **فرمت outbound فورک FLAT است** (address/port/id در سطح settings، نه servers[]/vnext).
11. **`keyOf` و هر string template — مراقب escape خورده‌ی `\${...}` باشد**: template literal escape‌شده همه را یکسان می‌کند → distinctBy فقط ۱ نگه می‌دارد → pool=1 (باگ v1.5.2).
12. **Executors.submit با lambda نوع مختلط → Runnable انتخاب می‌شود و نتیجه دور ریخته می‌شود** → همیشه `submit(java.util.concurrent.Callable { ... })` صریح (باگ v1.5.2 — aliveCnt=152 ولی pinged=0).
13. غربال TCP باید موازی (thread pool 24) باشد — sequential روی 200 کانفیگ عملاً هیچ‌وقت کامل نمی‌شود.
14. **VpnClubService حتما در AndroidManifest ثبت شود** (BIND_VPN_SERVICE + VpnService intent-filter) — نبودِ آن → «Unable to start service ... not found» و TUN هرگز بالا نمی‌آید (باگ v1.5.2).
15. redroid: `Cannot create interface` در Vpn.establish محدودیت redroid است (TUN نمی‌سازد) — باگ اپ نیست؛ engine تا سطح سرویس سبز است.

## 4. فایل‌ها

- `corex/`: XrayCore (پل هسته+variant+lastError/lastConfigJson)، FragmentProxy (تکه‌کننده ClientHello)، CloudflareScanner، HealthTester، SubscriptionFetcher (21 منبع)، LinkParser/ShareLinkBuilder/ProxyConfig، ProfileStore (JSON)، Tun2Socks (hev)، VpnClubService، CurrentConfigHolder (+variant)، VpnClubEngine (چرخه)، **IranVariants**، **DiagLogger** (`filesDir/diag.log` — یک خط به‌ازای هر تست؛ برای عیب‌یابی روی گوشی).
- `app/src/main/assets/`: geoip.dat، geosite.dat (الزامی).
- تست: `app/src/test/.../CorexLiveTest.kt` (شبکه واقعی؛ run: `gradle :app:testReleaseUnitTest --tests ...CorexLiveTest`).

## 5. نسخه‌ها (جدول رسمی — اینجا را آپدیت کن)

| نسخه | کامیت | خلاصه | وضعیت روی گوشی کارفرما |
|---|---|---|---|
| v1.4.0 | cf7fb7a | موتور PattNG کامل + UI تک‌دکمه | «کانفیگ سالم پیدا نشد» |
| v1.4.1 | 81a46fc | منابع wbnet + رندوم‌گیری + FragmentProxy | همان مشکل |
| v1.4.2 | 0da5bd5 | **FIX ریشه‌ای: initCoreEnv + geoip assets + ذخیره لینک تزریقی** | «کار نکرد» (جزئیات نامشخص) |
| v1.5.0 | fef88da | پروفایل‌های ایران (ECH/IPv6/F&F) | «کار نکرد» — نیاز به diag |
| v1.5.1 | 13335b5 | DiagLogger + نمایش خطای دقیق در UI | منتظر تست کارفرما |
| v1.5.2 | 0d663d7 | سرور ساب خودمان روی CF Worker (vpnclub-sub، 17 منبع، 5000 کانفیگ، کش 30د) به‌عنوان منبع اول | باگ‌های 9–14 (پایین) |
| v1.5.3 | ae3f552 | **FIX ریشه‌ای redroid: finalmask رشته‌ای + BaseKey درست + keyOf de-escape + submit Callable + پینگ موازی + VpnClubService در manifest** | redroid: هسته OK + تونل تست واقعی OK (دانلود 158KB/s)؛ TUN سراسری فقط روی گوشی واقعی (redroid TUN نمی‌سازد) |
| v1.5.4 | (در حال ریلیز) | **FIX بحرانی: ایران-variant هرگز اعمال نمی‌شد** — variantCache["" → variant] با c.tag رندوم (US-1) هیچ‌وقت match نمی‌کرد → ECH/fragment/cipherSuites روی ایران صفر بود؛ فیکس: effectiveVariant=activeVariant مستقیم | منتظر تست |

## 7. زیرساخت سرور (Cloudflare — اکانت کارفرما)

- **Worker:** `vpnclub-sub` → https://vpnclub-sub.quilt-refract.workers.dev (کد: `/home/agentuser/projects/vpnclub-worker/`)
  - `/sub` متن خام، `/sub?base64=1`، `/health`
  - ۱۷ منبع را سمت سرور می‌کشد، base64 را دیکود می‌کند، دی‌داپ، سقف ۵۰۰۰، کش KV ۳۰ دقیقه
  - KV: `vpnclub-subs` (id c2667955269745908b42ccc90f61b354)
- توکن CF و اکانت‌آیدی در `~/.config/vpnclub/cf.env` (chmod 600، هرگز در چت/ریپو)
- دیپلوی مجدد: `cd ~/projects/vpnclub-worker && source ~/.config/vpnclub/cf.env && npx wrangler deploy`

## 6. پروتکل عیب‌یابی (وقتی «کار نکرد»)

1. از کارفرما فقط یک چیز بخواه: **متن پیام UI** (از v1.5.1 خطای دقیق دارد) یا فایل `Android/data/com.helboy.vpnclub/files/diag.log`.
2. diag.log هر تست را با نام کانفیگ + variant + خطا ثبت می‌کند → پین‌پوینت دقیق.
3. حدس نزن. با diag برو جلو.
