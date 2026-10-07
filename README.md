# 🛡️ VPN CLUB (وی‌پی‌ان کلاب)
### اپلیکیشن اختصاصی و پرسرعت اندروید برای شبکه جهانی سرورهای VPN Gate (پروژه دانشگاه تسوکوبا ژاپن)

![VPN Club Icon](https://raw.githubusercontent.com/HELBOYCODER/vpn-club/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png)

[![Build VPN Club APK](https://github.com/HELBOYCODER/vpn-club/actions/workflows/build-apk.yml/badge.svg)](https://github.com/HELBOYCODER/vpn-club/actions/workflows/build-apk.yml)
[![Latest Release](https://img.shields.io/github/v/release/HELBOYCODER/vpn-club?color=00F5D4&label=Release)](https://github.com/HELBOYCODER/vpn-club/releases/latest)

---

## 📖 معرفی پروژه (Overview)

**VPN CLUB** یک کلاینت مدرن و مستقل برای اندروید است که به طور اختصاصی برای استفاده آسان و بدون دردسر از شبکه گسترده و دانشگاهی **VPN Gate** (پروژه تحقیقاتی دانشگاه تسوکوبای ژاپن به سرپرستی Daiyuu Nobori) طراحی شده است.

### 🌟 ویژگی‌های کلیدی:
1. 🇮🇷 **موتور تطبیق و اتصال هوشمند با اینترنت ایران (Iran Network Adapter):**
   - **غربالگری رنج‌های مسدود:** حذف خودکار رنج‌های مشهور و فیلترشده تسوکوبا (`219.100.*` و `130.158.*`) و اولویت‌بخشی به سرورهای داوطلب در کره جنوبی، رومانی، روسیه و سایر کشورها.
   - **اولویت‌بندی پورت‌های باز:** تمرکز ویژه روی پورت‌های **995 (MS-SSTP)** و پورت‌های بالای غیراستاندارد به دلیل عدم اعمال فیلترینگ شدید SNI روی آن‌ها.
   - **سنجش زنده سوکت (Live TCP Reachability Probe):** تست واقعی و موازی باز بودن پورت و IP از روی اینترنت همراه اول، ایرانسل یا وای‌فای کاربر در کمتر از ۱.۵ ثانیه.
   - **دیده‌بان تعویض خودکار سرور (Watchdog Auto-Failover):** در صورت عدم دریافت پاسخ در مدت ۹ ثانیه، اپلیکیشن بلافاصله و خودکار به سرور سازگار بعدی سوییچ می‌کند.
   - **تنظیمات ضد تراتل سلولار:** تزریق `mssfix 1280` و `tun-mtu 1400` برای جلوگیری از قطعی فرگمنت در دکل‌های همراه اول و ایرانسل.
2. ⚡ **موتور محلی OpenVPN سراسری (Device-Wide VpnService):** مجهز به کتابخانه بومی و بهینه‌سازی‌شده OpenVPN با پشتیبانی از معماری‌های ARM64، ARMv7، x86 و x86_64.
3. 🔑 **تزریق خودکار نام کاربری و رمز عبور:** طبق استاندارد سرورهای VPN Gate، نام‌کاربری و رمز عبور به صورت پیش‌فرض (`vpn` / `vpn`) به شکل کاملاً خودکار در کانفیگ‌ها تزریق شده و کاربر نیازی به هیچ‌گونه تنظیمات دستی ندارد.
4. 🔄 **ساب‌اسکریپشن زنده و آینه‌های ضد فیلتر:** اتصال چندگانه به API رسمی دانشگاه تسوکوبا و میرورهای سریع گیت‌هاب (با کش محلی و دیتابیس پشتیبان استارتر در assets).
5. 📊 **داشبورد تلمتری و مانیتورینگ زنده:** نمایش سرعت زنده دانلود و آپلود، تاخیر پینگ، مدت زمان نشست و حجم ترافیک مصرفی.
6. 🛡 **محافظت در برابر نشت DNS و IPv6:** اعمال خودکار DNSهای پاک (Cloudflare و Google) و شناسه ULA IPv6 پایدار برای رفع افت ارتباط در شبکه‌های همراه اول، ایرانسل و رایتل.
7. 🎨 **رابط کاربری چشم‌نواز نئون و سایبر (Jetpack Compose + Material 3):** انیمیشن‌های روان، حالت تاریک اختصاصی و پشتیبانی کامل از چینش فارسی (RTL).
8. 📱 **آیکون لانچر اختصاصی:** طراحی وکتور و بیت‌مپ سازگار با استانداردهای لانچر اندروید در تمام رزولوشن‌ها.

---

## 🛠 معماری فنی (Architecture)

- **Language:** Kotlin 2.1.0
- **UI Framework:** Jetpack Compose (BOM 2024.12.01) + Material 3
- **VPN Engine:** Native C/C++ OpenVPN core compiled with NDK (module `:vpnLib`)
- **Networking:** OkHttpClient 4.12.0 with GZIP, auto-redirect & timeout handling
- **Target SDK:** 35 (Android 15) | **Min SDK:** 24 (Android 7.0+)
- **CI/CD:** Automated GitHub Actions APK builder with automated releases

---

## 📥 دانلود و نصب (Download)

آخرین نسخه بیلدشده را می‌توانید مستقیماً از بخش Releases گیت‌هاب دریافت کنید:
👉 **[دانلود فایل APK نسخه v1.2.0](https://github.com/HELBOYCODER/vpn-club/releases/latest)**

---

## 👨‍💻 سازنده

توسعه‌داده شده توسط **[HELBOYCODER](https://github.com/HELBOYCODER)** با موتور ارکستراسیون خودکار **Hellboy Engine**.
