# Hellboy VPN 4.3.0 — Smart connection

## فارسی

- WARP و اسکنرش، Serverless، سایفون و نصب خودکار همهٔ پنل‌ها کاملاً از برنامه حذف شدند؛ موتور، داده‌ها و مرحلهٔ ساخت سایفون نیز حذف شده‌اند.
- برنامه بر اتصال هوشمند متمرکز است: هر بار اتصال، دریافت تازهٔ منابع عمومی و تست واقعی چند سرور را آغاز می‌کند؛ تا پنج گزینهٔ کم‌تاخیر نگه داشته می‌شوند.
- اتصال فقط بعد از عبور ترافیک واقعی و تست سرعت پذیرفته می‌شود. معیار فعلی: پاسخ کمتر از دو ثانیه و دانلود آزمایشی حداقل ۶۴ KiB/s؛ این معیار تضمین سرعت روی همهٔ اپراتورها نیست.
- کیفیت هر ۳۰ ثانیه با درخواست محدود بررسی می‌شود. دو شکست پیاپی یا سه نتیجهٔ ضعیف، انتخاب پشتیبان را آغاز می‌کند؛ با تمام شدن پشتیبان‌ها، منابع دوباره دریافت و سرور تازه جست‌وجو می‌شود.
- نبود اینترنت گوشی باعث انتظار می‌شود. پیدا نشدن سرور باعث تلاش دوباره با فاصلهٔ ۵ تا ۳۰ ثانیه می‌شود. دکمهٔ قطع و دکمهٔ قطع اعلان، کل جست‌وجو و تلاش مجدد را لغو می‌کنند.
- پوستهٔ اصلی هل‌بوی، گوی ذرات، دیوار بتنی، رنگ‌ها و جلوه‌های خون و آتش حفظ شده‌اند.

**Preview:** کلید امضای اصلی در CI موجود نیست. این نسخه با شناسهٔ مستقل `com.helboy.vpnclub.preview43` کنار نسخه‌های پیشین نصب می‌شود. تست خودکار جای آزمایش اتصال روی اینترنت گوشی شما را نمی‌گیرد.

## English

Removed WARP and endpoint scanning, Serverless, Psiphon (including its bundled data/native build) and every automatic panel installer. The original Hellboy/orb/concrete/fire skin remains.

Every connection starts fresh public-feed discovery and actual tunnel latency testing for a shortlist of up to five servers. Activation also requires a bounded HTTPS download. The current quality thresholds are latency ≤2 seconds and measured download ≥64 KiB/s; these are selection criteria, not a throughput guarantee.

One cancellable session owns discovery, connection and recovery. Two dead samples or three weak samples trigger backup selection; an exhausted pool starts new discovery. Offline devices wait for network return. Empty scans retry with 5–30 second backoff. Stop cancels both the tunnel and subsequent scans.

Without the original signing key, this is a separate preview package (`com.helboy.vpnclub.preview43`). Native connectivity on the user's carrier still requires device validation.
