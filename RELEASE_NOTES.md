# Hellboy VPN 4.3.3 — Protocol selector

در این نسخه، **انتخاب واقعی نوع اتصال** در صفحهٔ تنظیمات اضافه شده است: خودکار، VLESS، VMess، Trojan، Shadowsocks و Hysteria2.

- انتخاب کاربر ذخیره می‌شود و در اتصال بعدی اعمال می‌شود.
- موتور کشف سرور، سرورهای ذخیره‌شده و تست تونل فقط روی پروتکل انتخاب‌شده انجام می‌شوند.
- گزینهٔ «خودکار» تمام پروتکل‌های پشتیبانی‌شدهٔ موتور فعلی Xray را در نظر می‌گیرد.
- گزینه‌های MSN-GUARD شامل MASQUE، WireGuard/WARP، Psiphon و Tor هنوز پیاده‌سازی نشده‌اند و غیرفعال مشخص شده‌اند، نه اینکه دکمه‌های نمایشی اتصال باشند.
- پوسته و منوی اصلی Hellboy VPN حفظ شده است.
- نسخهٔ Preview با شناسهٔ مستقل `com.helboy.vpnclub.preview433` منتشر می‌شود تا با کلید امضای Previewهای قبلی تداخل نکند.

این نسخه **ادغام کامل MSN-GUARD نیست** و اجرای پروتکل‌های جدید بدون موتور مربوطه را ادعا نمی‌کند.

## English

Version 4.3.3 adds a working protocol filter to Settings. Select Automatic, VLESS, VMess, Trojan, Shadowsocks or Hysteria2. The choice is persisted and applied to future scans, including cached candidates and native outbound delay measurements.

Unavailable MSN-GUARD transports (MASQUE, WireGuard/WARP, Psiphon and Tor) are clearly identified as not yet implemented. No fake transport buttons.

Preview uses the separate package ID `com.helboy.vpnclub.preview433` because existing preview APKs may have incompatible release signing certificates. No full MSN-GUARD integration is claimed.
