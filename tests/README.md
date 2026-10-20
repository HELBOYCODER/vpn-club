# Hellboy VPN — تست‌های E2E

تست‌های سرتاسری روی اپ اندروید با [e2e](https://e2e.tester.army/docs) (از TesterArmy، Apache-2.0) نوشته شده‌اند.
این تست‌ها فقط از test tags اپ استفاده می‌کنند و **هیچ مدل AI و کلیدی لازم ندارند**.

## تست‌ها
- `flows/app.e2e.ts`
  - اپ روی صفحه‌ی خانه با دکمه‌ی اتصال باز می‌شود.
  - ناوبری پایین (سرورها، اسکنر IP، تنظیمات، بازگشت به خانه) کار می‌کند.

## اجرا
نیاز: Node 24.8 یا جدیدتر، و یک emulator یا گوشی اندرویدی متصل.

```bash
cd tests
npm ci
cd ..
./gradlew :app:assembleDebug
cd tests
APK_PATH=../app/build/outputs/apk/debug/app-debug.apk npm test
```

روی CI در `.github/workflows/e2e-android.yml` هر push و PR اجرا می‌شود (emulator اندروید 34 با KVM).

## افزودن تست
- فقط Compose test tag یا `contentDescription` را هدف بگیرید؛ متن فارسی شکننده است.
- تست‌هایی که `agent.act` دارند به کلید مدل نیاز دارند (`AI_GATEWAY_API_KEY`). تا جای ممکن تست قطعی بنویسید.
- تست‌های VPN واقعی (اتصال و عبور ترافیک) داخل emulator قابل اعتماد نیستند و این مجموعه آن‌ها را پوشش نمی‌دهد.
