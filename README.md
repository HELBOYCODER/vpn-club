# 🔥 Hellboy VPN

کلاینت VPN اندروید با **موتور Narcic Gateway** (Xray, sing-box, Psiphon, Aether, Tor, DNS tunnels، WireGuard/AWG، IKEv2، SSH، OpenConnect) و **پوسته‌ی Hellboy** (دیوار بتنی، خون و آتش، چهره‌ی هلبوی).

- نسخه‌ی فعلی: **5.0.0** — فایل نصبی در `dist/HellboyVPN-5.0.0-arm64.apk`
- موتور و منطق اپ: [Narcic Gateway](https://github.com/valid7996/Narcic-Getway) (GPL-3.0)
- پوسته و قالب: Hellboy VPN

## ساخت
۱. هسته‌ی `nrgcore` را بسازید (فایل ۱۱۹ مگابایتی، در ریپو نیست):
   ```bash
   bash tools/fetch-cores.sh          # کدهای vendor را در ریشه می‌گیرد
   bash tools/build-nrgcore.sh        # خروجی: app/libs/nrgcore.aar
   ```
   نیازمندی‌ها: Go 1.26.3، `gomobile`، Android NDK 29 و Android SDK با platform 37.0.
۲. اپ را بسازید:
   ```bash
   ./gradlew :app:assembleRelease
   ```
۳. با کلید انتشار امضا کنید (کلید در ریپو نیست).

## یادداشت
- تست‌های E2E (`tests/`) برای رابط قبلی نوشته شده بودند و با پوسته‌ی جدید هماهنگ نشده‌اند.
- CI (GitHub Actions) فعلاً حذف شده، چون ساخت `nrgcore` در CI بسیار سنگین است و فایل بزرگ‌تر از حد GitHub است.

## مجوز
GPL-3.0 (به‌خاطر Narcic Gateway و هسته‌های همراه). نکات اسناد شخص ثالث در `THIRD_PARTY.md` و `NOTICE`.
