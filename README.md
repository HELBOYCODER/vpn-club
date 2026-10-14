# Hellboy VPN

کلاینت اندروید با پوستهٔ اصلی هل‌بوی و اتصال هوشمند خودکار.

دکمهٔ اتصال را بزنید: برنامه منابع سرور را تازه دریافت می‌کند، چند سرور را با درخواست واقعی داخل تونل تست می‌کند و به گزینهٔ کم‌تاخیر و دارای سرعت قابل‌قبول وصل می‌شود. قطعی یا افت کیفیت چندباره باعث انتخاب پشتیبان می‌شود؛ اگر پشتیبان‌ها هم جواب ندهند، جست‌وجوی تازه شروع می‌شود. دکمهٔ قطع تمام تلاش‌ها را متوقف می‌کند.

سه صفحه: خانه، سرورهای منتخب و تنظیمات. چهرهٔ هل‌بوی، گوی ذرات، بافت بتنی، رنگ‌ها و افکت‌های اصلی حفظ شده‌اند. WARP، Serverless، سایفون، اسکن IP و نصب پنل‌ها در نسخهٔ ۴.۳ حذف شده‌اند.

[انتشارها و فایل نصبی](https://github.com/HELBOYCODER/vpn-club/releases) · [توضیحات نسخه](RELEASE_NOTES.md)

Preview 4.3 با شناسهٔ مستقل کنار نسخه‌های قبلی نصب می‌شود؛ کلید امضای اصلی در CI موجود نیست. منابع عمومی ممکن است در شبکهٔ کاربر مسدود یا موقتاً خالی باشند. برنامه تا زمان زدن دکمهٔ قطع، با فاصلهٔ زمانی دوباره تلاش می‌کند.

## English

Android VPN client with automatic fresh server discovery, actual tunnel validation and recovery. Every connect starts a fresh scan and keeps up to five measured candidates. Activation checks both latency and a bounded HTTPS download. Repeated failure or low quality selects a backup, then refreshes the feeds if the pool is exhausted. Stop cancels all retries.

WARP, Serverless, Psiphon, clean-IP scanning and automatic panel installers have been removed. The original Hellboy face, particle orb, concrete textures, palette and fire/blood effects remain.

The app uses PattNG Xray and the hev TUN bridge. Unit tests, lint, APK builds, emulator navigation and search/stop checks gate publication. CI cannot prove connectivity on an Iranian mobile network. Without the original release signing key, version 4.3 is a separate preview application.

## Build

Java 17, Android SDK 35 and Gradle 8.10.2:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease -PhellboyPreview=true
```

Signing environment: `HELLBOY_KEYSTORE`, `HELLBOY_KEYSTORE_PASSWORD`, optional `HELLBOY_KEY_ALIAS` (default `hellboyvpn`).

[Third-party notices](THIRD_PARTY.md) · [Recovery behavior](docs/SMART_CONNECTION.md)
