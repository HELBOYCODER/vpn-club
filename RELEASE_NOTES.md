# Hellboy VPN 4.3.2 — Install fix

رفع تداخل نصب نسخه‌های Preview با امضای متفاوت. این نسخه با شناسهٔ مستقل `com.helboy.vpnclub.preview432` کنار نسخهٔ قبلی نصب می‌شود و نیازی به پاک کردن آن ندارد.

فایل Release نهایی در شبیه‌ساز اندروید، در حالی که APK عمومی ۴.۳.۱ نصب است، نصب و اجرا می‌شود؛ نصب دوبارهٔ همان فایل نیز بررسی می‌شود. اصلاحات اتصال ۴.۳.۱ و پوستهٔ قبلی حفظ شده‌اند.

Fixes the package/signature collision between separately signed previews. Version 4.3.2 installs alongside 4.3.1 using a separate package ID. CI installs the previous public APK, installs and launches the exact new release APK, and verifies reinstalling that release. The existing connection fixes and visual design are preserved.
