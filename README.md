<div align="center">

# 🔥 Hellboy VPN

کلاینت اندروید با طراحی اصلی هلبوی، اتصال هوشمند و نصب سریع پنل Netra.

[Downloads / دانلود](https://github.com/HELBOYCODER/vpn-club/releases) · [Changes / تغییرات](RELEASE_NOTES.md) · [ZeroNet comparison / تطبیق امکانات](docs/ZERONET_PARITY.md)

</div>

## فارسی

نسخه ۴.۲ طراحی اصلی هلبوی و گوی ذرات، بافت بتنی، رنگ‌ها و جلوه‌های خون/آتش را حفظ می‌کند. بهبود ظاهر شامل فونت فارسی و کنترل‌های خواناتر است.

امکانات: VLESS/REALITY، VMess، Trojan، Shadowsocks، Hysteria2، XHTTP، WARP، سایفون، حالت بدون سرور، اسکن IP کلادفلر، ویرایش کانفیگ، QR و اشتراک، تونل تفکیکی، DNS و مسیریابی. در نسخه جدید: نصب **Netra**، جست‌وجو و علاقه‌مندی‌ها، کاشی تنظیمات سریع، ویجت میانبر، ورود فایل و Share اندروید، DNS با DoH و اشتراک رمزدار روی شبکه محلی.

### نصب Netra

تنظیمات ← پنل شخصی روی کلادفلر ← نصب Netra. با توکن حساب خودتان نصب کنید؛ رمز پنل را یادداشت کنید. لینک ساب خودکار اضافه می‌شود. برای تغییرات حساب کلادفلر از داشبورد استفاده کنید؛ توکن در ورکر ذخیره نمی‌شود. نصب جدید BPB جایگزین شده ولی ساب‌های قبلی شما نگه داشته می‌شوند.

### اشتراک روی Wi-Fi یا هات‌اسپات

گزینه «اشتراک روی شبکه محلی» را روشن و VPN را دوباره وصل کنید. در دستگاه دوم از IP محلی گوشی، پورت HTTP `10819` یا SOCKS `10818`، نام کاربری `hellboy` و رمز نمایش‌داده‌شده استفاده کنید. مخصوص مسیر Xray/WARP/بدون سرور؛ سایفون مستقیم را پوشش نمی‌دهد.

### دانلود و امضا

فقط فایل APK در صفحه انتشار همان نسخه را دانلود کنید. اگر نام انتشار **Preview** است، یک نسخه آزمایشی کنار نسخه قبلی نصب می‌شود؛ دلیل آن نبودن کلید امضای ثابت نسخه قبلی در CI است. نسخه قبلی را برای نصب Preview حذف نکنید.

امکانات پیشرفته ZeroNet از جمله MASQUE، هیبرید WARP و هسته Rust هنوز معادل تأییدشده ندارند؛ [جدول کامل](docs/ZERONET_PARITY.md) وضعیت را مشخص می‌کند.

## English

An Android client using the PattNG Xray core, Psiphon and hev TUN bridge. The original Hellboy, particle orb, concrete wall and fire/blood skin are preserved. Version 4.2 adds Netra deployment, selected-server search/favourites, file and Share import, Quick Settings and widget shortcuts, authenticated LAN proxies and DoH.

This is not a Zray port or a claim of complete ZeroNet parity. Read the [feature comparison](docs/ZERONET_PARITY.md) for native-core gaps and network validation limits.

A preview release has a separate application ID and installs alongside the existing app. Stable upgrades require the existing signing key. APK checksum and signing certificate accompany the download.

## Build and verify

JDK 17, Android SDK 35, Gradle 8.10.2. Psiphon also requires the Go toolchain specified by the workflow.

```sh
bash tools/build_psiphon.sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
# Separate preview APK; does not overwrite the original app:
./gradlew :app:assembleRelease -PhellboyPreview=true
```

Real external-network probes: `HELLBOY_LIVE_TESTS=1 ./gradlew :app:testDebugUnitTest`.
The workflow captures emulator screenshots and publishes only after tests, lint, builds and launch checks succeed. Screenshots are attached to the release and CI UI evidence artifact.

Stable signing: `HELLBOY_KEYSTORE_B64`, `HELLBOY_KEYSTORE_PASSWORD` repository secrets (`HB_*` legacy names also supported). Do not commit signing keys or Cloudflare tokens.

[Third-party notices](THIRD_PARTY.md). Netra is fetched at a pinned upstream revision. Vazirmatn font notice is bundled under `app/src/main/assets/licenses`.
