package com.helboy.vpnclub.ui.proto.tools

const val REPO_URL = "https://github.com/HELBOYCODER/vpn-club"
const val ISSUES_URL = "$REPO_URL/issues"
const val RELEASES_URL = "$REPO_URL/releases"

/** One line of the third-party list (a short copy of THIRD_PARTY.md and NOTICE). */
data class Credit(val name: String, val use: String, val license: String, val url: String? = null)

val CREDITS: List<Credit> = listOf(
    Credit("Psiphon tunnel-core", "اتصال Psiphon", "GPL-3.0", "https://github.com/Psiphon-Labs/psiphon-tunnel-core"),
    Credit("Xray-core (فورک PattNG)", "هستهٔ اصلی VPN", "MPL-2.0"),
    Credit("hev-socks5-tunnel", "تبدیل TUN به SOCKS", "MIT"),
    Credit("Metal Mania", "فونت عنوان‌ها", "SIL OFL 1.1", "https://fonts.google.com/specimen/Metal+Mania"),
    Credit("Narcic Gateway (valid7996)", "موتور پروتکل‌ها و Aether", "GPL-3.0", "https://github.com/valid7996/Narcic-Getway"),
    Credit("MahsaNG (GFW-knocker)", "گزینه‌های Psiphon", "GPL-3.0", "https://github.com/GFW-knocker/MahsaNG"),
    Credit("BPB-Wizard / BPB-Worker-Panel", "نصب پنل Cloudflare", "GPL-3.0", "https://github.com/bia-pain-bache/BPB-Wizard"),
    Credit("Aether (patterniha)", "موتور Psiphon", "AGPL-3.0"),
    Credit("ZeroNet (zeghostwriter)", "ایده‌ها بدون کد", "MIT", "https://github.com/zeghostwriter/ZeroNet"),
    Credit("Netra Panel (netrair)", "پروفایل Netra", "MIT", "https://github.com/netrair/netra-panel"),
    Credit("flag-icons", "پرچم کشورها", "MIT", "https://github.com/lipis/flag-icons"),
)

const val LICENSE_NOTE =
    "Hellboy VPN نرم‌افزار آزاد است و زیر مجوز GPL-3.0 منتشر می‌شود. چون Psiphon (GPL-3.0) در برنامه همراه است، " +
        "هر نسخهٔ منتشرشده باید با شرایط سازگار با GPL-3.0 و همراه با سورس عرضه شود. " +
        "شما آزادید برنامه را اجرا، مطالعه، تغییر و با همین شرایط منتشر کنید؛ این برنامه هیچ تضمینی ندارد."

const val THIRD_PARTY_NOTE =
    "فهرست کامل اجزای شخص ثالث و متن مجوزها در فایل‌های THIRD_PARTY.md و NOTICE داخل مخزن پروژه است. " +
        "اتصال به Psiphon نیاز به پیکربندی رسمی خود Psiphon دارد و همراه برنامه نیست؛ Zeus Panel همراه برنامه نیست و فقط به درخواست شما روی حساب Cloudflare خودتان نصب می‌شود."

/** What the privacy summary says: (title, body) pairs. */
val PRIVACY: List<Pair<String, String>> = listOf(
    "یک کلاینت، نه سرویس VPN" to
        "Hellboy VPN به سرورها، سابسکریپشن‌ها و شبکه‌هایی وصل می‌شود که خودت اضافه می‌کنی. هیچ‌کدام را ما اداره نمی‌کنیم و ترافیک تو هرگز به ما نمی‌رسد.",
    "روی دستگاه تو می‌ماند" to
        "کانفیگ‌ها، رمزها، تنظیمات، آمار و لاگ‌ها در فضای خصوصی اپ ذخیره می‌شوند و با حذف اپ پاک می‌شوند.",
    "جمع‌آوری نمی‌شود" to
        "اپ هیچ کتابخانهٔ آنالیتیکس، تله‌متری، گزارش کرش، تبلیغات یا ردیابی ندارد و حسابی وجود ندارد که با آن شناسایی شوی.",
    "قابلیت‌های اختیاری با سرویس‌های دیگر تماس می‌گیرند" to
        "تشخیص موقعیت، تست سرعت، دانلود فایل‌های مسیریابی (گیت‌هاب)، بررسی به‌روزرسانی و DNS یا زیرساخت Tor که خودت انتخاب کرده‌ای، هرکدام بیرون تماس می‌گیرند و ناگزیر IP تو را می‌بینند.",
    "فهرست اپ‌های نصب‌شده" to
        "پروکسی per-app فقط برای نمایش فهرست، اپ‌های نصب‌شده را می‌خواند. این کار روی خود دستگاه انجام می‌شود و هرگز ارسال نمی‌شود؛ فقط انتخاب تو ذخیره می‌شود.",
    "شناسهٔ دستگاه" to
        "شناسه‌ای که اینجا می‌بینی یک عدد تصادفی مخصوص همین نصب است. اپ آن را جایی نمی‌فرستد؛ فقط برای این است که اگر خودت خواستی، یک کانفیگ قفل‌شده به این دستگاه محدود شود.",
    "محدودیت‌های صادقانه" to
        "دستگاه روت‌شده یا آلوده هیچ تضمینی ندارد. VPN فقط محل خروج ترافیک را عوض می‌کند و تو را ناشناس نمی‌کند.",
)
