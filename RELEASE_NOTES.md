# 🔥 Hellboy VPN v3.9.0 — ویرایشگر کامل PattNG + رفع وصل نشدن ورکرهای کلادفلر

## 🇮🇷 فارسی
- **ویرایشگر کامل کانفیگ (همه‌ی گزینه‌های PattNG):** نام، آدرس، پورت، UUID/رمز، Flow، Encryption، AlterId/Security، Method؛ شبکه (tcp/ws/httpupgrade/xhttp/grpc) با Host/Path/Header/ServiceName/gRPC mode/Authority/XHTTP mode/extra؛ امنیت (none/tls/reality) با SNI، Fingerprint، ALPN، ECH، Cipher suites، PublicKey/ShortId/SpiderX/mldsa65Verify؛ FinalMask؛ و «لینک خام».
- **سرورهای ساب هم ویرایش می‌شوند** (منوی ⋮ کنار هر سرور ساب: اتصال، ویرایش، کپی، QR، اشتراک) و ویرایش با رفرش ساب از بین نمی‌رود.
- **رفع علت اصلی «بدون پینگ» سرورهای BPB/Zeus/workers.dev:** تست با cp.cloudflare.com بود که ورکرهای کلادفلر به آن وصل نمی‌شوند؛ حالا تست با گوگل.
- تست دوم ساب‌ها اول با «ایرانسل · Fragment» (دور زدن فیلتر SNI)؛ IP منتخب دیگر روی همه‌ی سرورهای کلادفلر اجباری نیست.
- هسته پارامترهای بیشتری از لینک را اعمال می‌کند (gRPC multi/authority، ECH، cipher suites، fm، pqv، VLESS encryption).
- قطع اینترنت گوشی تشخیص داده می‌شود: دیگر تعویض/دریافت بی‌فایده تکرار نمی‌شود.
- **سایفون از صفحه‌ی اول به تنظیمات منتقل شد.**

## 🇬🇧 English
- **Full config editor (all PattNG options):** remarks, address, port, UUID/password, flow, encryption, alterId/security, method; network (tcp/ws/httpupgrade/xhttp/grpc) with host/path/header/serviceName/gRPC mode/authority/XHTTP mode/extra; security (none/tls/reality) with SNI, fingerprint, ALPN, ECH, cipher suites, pbk/sid/spx/pqv; FinalMask; raw link mode.
- **Subscription servers are editable too** (⋮ on each sub server: connect, edit, copy, QR, share); edits survive sub refreshes.
- **Root-cause fix for BPB/Zeus/workers.dev showing no ping:** tests used cp.cloudflare.com, which Cloudflare Workers can't reach; tests now use Google.
- Sub retry tries Irancell Fragment first (SNI filter); the selected IP is no longer forced on every Cloudflare server.
- Core honors more link params (gRPC multi/authority, ECH, cipher suites, fm, pqv, VLESS encryption).
- Phone internet outages are detected: no more useless failover/fetch loops.
- **Psiphon moved from the home page to Settings.**
