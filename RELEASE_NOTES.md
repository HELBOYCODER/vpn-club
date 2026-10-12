# 🔥 Hellboy VPN v3.8.1 — سرورهای ساب، انتخاب دستی، «فقط ساب‌های من»

## 🇮🇷 فارسی
- **باز شدن سرورهای هر ساب:** در «ساب‌های من» با «▼ سرورهای این ساب» همه‌ی سرورها (حتی بدون پینگ، با دلیل) دیده می‌شوند؛ **لمس هر سرور = اتصال دستی**.
- **منبع سرورها:** «خودکار (همه‌ی ساب‌ها)» یا «فقط ساب‌های من» — دکمه‌ی اتصال، اسکن، تعویض خودکار و بررسی سلامت فقط از ساب‌های شما استفاده می‌کنند. بدون ساب، خودکار.
- **رفع علت اصلی وصل نشدن ساب‌های BPB/Zeus:** SNI ورکرهای workers.dev در ایران فیلتر است و تست بدون فرگمنت همیشه شکست می‌خورد. حالا سرورهای CDN ناموفق دوباره با **IP منتخب + پروفایل‌های فرگمنت ایران** تست می‌شوند و ترکیب سالم برای اتصال ذخیره می‌شود.
- سرورهای ساب ذخیره می‌شوند؛ اگر ساب بدون VPN دریافت نشد، اتصال با سرورهای ذخیره‌شده امتحان می‌شود.
- کانفیگ‌های پشتیبانی‌نشده با دلیل دقیق نمایش و لاگ می‌شوند.

## 🇬🇧 English
- **Open each subscription:** "▼ servers of this sub" lists every server (even without ping, with the reason); **tap any server to connect manually**.
- **Server source:** "Automatic (all subs)" or "Only my subs" — connect, scan, failover and pool checks use only your subs. Falls back to automatic when you have none.
- **Root-cause fix for BPB/Zeus subs not connecting:** workers.dev SNI is filtered in Iran so plain tests always failed. Failed CDN servers are now retried with the **selected clean IP + Iran fragment profiles**, and the working combination is saved for connecting.
- Sub servers are stored; if the sub can't be fetched without VPN, connecting falls back to the stored servers.
- Unsupported configs are shown and logged with the exact reason.
