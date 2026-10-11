# 🔥 Hellboy VPN

![Hellboy VPN](app/src/main/res/mipmap-xxxhdpi/ic_launcher.png)

[![Build Hellboy VPN APK](https://github.com/HELBOYCODER/hellboy-vpn/actions/workflows/build-apk.yml/badge.svg)](https://github.com/HELBOYCODER/hellboy-vpn/actions/workflows/build-apk.yml)
[![Latest Release](https://img.shields.io/github/v/release/HELBOYCODER/hellboy-vpn?color=E8742A&label=Release)](https://github.com/HELBOYCODER/hellboy-vpn/releases/latest)

کلاینت VPN اندروید با موتور **PattNG / Xray** و پروفایل‌های عبور از فایروال ایران.

## امکانات
- **یک دکمه**: چهره‌ی هلبوی از ذرات — بعد از اتصال لبخند می‌زند، اگر وصل نشود ناراحت می‌شود.
- **سرورها**: ۱۰ سرور سالم تصادفی از ساب‌های عمومی، تست واقعی تاخیر از داخل تونل، دسته‌بندی (کلادفلر / CDN / Reality / مستقیم).
- **اسکنر IP تمیز کلادفلر**: از هر اسکن یک IP منتخب (سریع‌ترین)، قابل ویرایش دستی.
- **اینجکت**: IP منتخب روی همه‌ی سرورهای کلادفلر یا روی یک سرور دلخواه؛ اتصال زنده هم منتقل می‌شود.
- **سریع‌ترین اتصال**، تعویض خودکار سرور هنگام قطعی، تازه‌سازی خودکار لیست ضعیف.
- **تونل کامل** پیش‌فرض (همه‌ی اپ‌ها)، تونل تفکیکی اختیاری.
- پوسته‌ی **دیوار بتنی** با قطره‌های خون و آتش، تم روز و شب.

## دانلود
آخرین نسخه: **[Releases](https://github.com/HELBOYCODER/hellboy-vpn/releases/latest)** — فایل `HellboyVPN-x.y.z.apk`

## ساخت
```bash
gradle assembleRelease
```
- `tools/make_face_particles.py` — ساخت ذرات چهره از سه عکس (خنثی/لبخند/ناراحت)
- `tools/make_concrete.py` — ساخت بافت دیوار بتنی

## معماری
`VpnController` (منطق و دکمه‌ها) ← `HellboyEngine` (ساب‌ها، اسکن، تست با measureOutboundDelay) ← `HellboyVpnService` (Xray + hev-socks5-tunnel). جزئیات در `SKELETON.md`.
