# Hellboy VPN 4.2.0

## فارسی

- طراحی اصلی حفظ شده: چهره هلبوی، گوی ذرات، دیوار بتنی، رنگ‌ها و جلوه‌های خون/آتش. فونت فارسی و خوانایی کنترل‌ها بهتر شده است.
- نصب سریع **Netra** جایگزین نصب BPB شد. ساب‌های BPB قبلی حذف نمی‌شوند. KV و رمز پنل خودکار ساخته می‌شود؛ شناسه، رمز Trojan و مسیر ساب تصادفی‌اند. کد Netra از نسخه مشخص دریافت و SHA-256 آن بررسی می‌شود. توکن حساب در ورکر Netra ذخیره نمی‌شود.
- مسیر Skip Login در Netra نصب‌شده غیرفعال است تا رمز پنل دور زده نشود.
- جست‌وجوی سرورها، فیلتر پروتکل، علاقه‌مندی‌ها، ورود فایل متنی/Base64 و دریافت کانفیگ از Share اندروید.
- کاشی تنظیمات سریع اندروید و ویجت میانبر اتصال/قطع.
- اشتراک HTTP/SOCKS روی Wi-Fi یا هات‌اسپات با رمز تصادفی، در حالت‌های Xray/WARP/بدون سرور. پس از تغییر تنظیمات باید دوباره وصل شوید. در سایفون مستقیم فعال نیست.
- DNS رمزدار DoH با آدرس IP و اعتبارسنجی DNS؛ حذف عقب‌نشینی DNS بدون اطلاع کاربر.
- رفع تشخیص اشتباه نسخه قدیمی به‌عنوان به‌روزرسانی، تعویض ناخواسته سرور در حالت گیمینگ، حذف بی‌صدای قوانین مسیریابی بعد از خطای هسته و حذف لینک ساب خصوصی از لاگ HTTP.
- درخواست‌های تغییر حساب کلادفلر پس از خطای شبکه خودکار تکرار نمی‌شوند تا منابع تکراری ساخته نشود؛ خطای بررسی نام ورکر به‌عنوان «نام آزاد» تلقی نمی‌شود.
- تست، lint و راه‌اندازی اپ در شبیه‌ساز پیش از انتشار.

**Preview:** اگر کلید امضای ثابت موجود نباشد، فایل منتشرشده آزمایشی است و کنار نسخه قبلی نصب می‌شود؛ آن را به‌روزرسانی مستقیم نسخه ۴.۱.۰ در نظر نگیرید.

همه امکانات ZeroNet هنوز منتقل نشده‌اند. وضعیت دقیق MASQUE، هیبرید WARP، هسته Zray و سایر موارد در [جدول تطبیق](docs/ZERONET_PARITY.md) آمده است. تست شبیه‌ساز جای تست واقعی اتصال روی اینترنت ایران را نمی‌گیرد.

## English

Original Hellboy/orb/concrete/fire artwork and palette retained; Persian typography and controls refined. Netra replaces BPB installation with isolated KV, random credentials, password protection and a pinned, checksum-verified upstream worker. The upstream Skip Login handler is disabled after checksum verification to prevent bypassing the panel password. Existing subscriptions remain usable.

Added selected-server search, protocol filters, favourites, text/Base64 file and Android Share import, Quick Settings tile, shortcut widget, authenticated LAN HTTP/SOCKS sharing and IP-addressed DoH.

Fixed semantic update ordering, gaming watchdog switching, silent routing-rule removal, private subscription paths in HTTP logs and replay of mutating HTTP requests. Publication requires unit tests, lint, builds and emulator launch.

Without the original release signing key, this is a separate **preview app**, not an in-place update. See [ZeroNet parity](docs/ZERONET_PARITY.md) for unimplemented native-core capabilities and validation limits.
