# بارگذاری پروژه در گیت‌هاب — گام‌به‌گام

## روش ۱: با git (توصیه‌شده)

این پوشه از قبل یک مخزن git با یک commit اولیه است. کافی است:

```bash
# ۱) در گیت‌هاب یک مخزن جدید (Empty) بسازید؛ مثلاً pelc — بدون README اولیه
# ۲) ریموت را اضافه کنید و پوش کنید:
cd pelc
git remote add origin https://github.com/<نام-کاربری>/pelc.git
git branch -M main
git push -u origin main
```

اگر به‌جای HTTPS از SSH استفاده می‌کنید:

```bash
git remote add origin git@github.com:<نام-کاربری>/pelc.git
```

## روش ۲: فقط با آپلود فایل در وب

1. فایل `pelc-github-ready.zip` را از کنار این پروژه بردارید.
2. در گیت‌هاب مخزن جدید بسازید (Empty).
3. صفحهٔ مخزن → **Add file → Upload files** → محتویات zip را drag&drop کنید.
4. Commit changes بزنید.

## فعال‌سازی ساخت خودکار APK

1. پس از push، تب **Actions** را باز کنید؛ workflow «Android CI» با هر push اجرا می‌شود.
2. در پایان اجرا، در بخش **Artifacts** همان اجرا، فایل `pelc-debug-apk` را دانلود کنید.
3. برای انتشار عمومی: یک تگ بسازید تا workflow «Release» اجرا شود:

```bash
git tag v0.1.0
git push origin v0.1.0
```

۴. در تب **Releases** یک نسخه با دو فایل ساخته می‌شود:
   - `pelc-v0.1.0-install.apk` — APK قابل نصب (امضای debug؛ برای تست و نصب مستقیم)
   - `pelc-v0.1.0.aab` — باندل برای Google Play (پس از امضای اختصاصی شما)

## ساخت لوکال APK (بدون CI)

پیش‌نیاز: Android Studio (یا JDK 17 + Android SDK 35 + NDK 27 + CMake 3.22.1)

```bash
git clone https://github.com/<نام-کاربری>/pelc.git && cd pelc
./scripts/fetch-fonts.sh        # دانلود فونت‌های OFL (اختیاری)
./gradlew :app:assembleDebug    # خروجی: app/build/outputs/apk/debug/app-debug.apk
```

بار اول، CMake فایل‌های HarfBuzz را خودش دانلود می‌کند؛ اگر می‌خواهید بیلد کاملاً آفلاین باشد، قبل از بیلد `./scripts/fetch-native-deps.sh` را اجرا کنید.

## چک‌لیست پیش از عمومی‌کردن مخزن

- [ ] فایل‌های فونت تجاری وارد مخزن نشده باشند (`assets/fonts` فقط فونت‌های OFL)
- [ ] فایل `local.properties` در مخزن نباشد (در `.gitignore` هست)
- [ ] اگر APK رسمی می‌دهید، keystore را **هرگز** commit نکنید
