# فونت‌های همراه برنامه

فونت‌های این پوشه در زمان Build وارد APK می‌شوند. برای وزن پایین APK،
فایل‌های واقعی فونت داخل مخزن git ذخیره نمی‌شوند؛ به‌جای آن اسکریپت زیر
فونت‌های OFL را از مخزن رسمی Google Fonts دانلود می‌کند:

```bash
./scripts/fetch-fonts.sh
```

## فهرست فونت‌ها و مجوزها

| فایل | سبک | مجوز |
|---|---|---|
| Amiri-Regular.ttf / Amiri-Bold.ttf | نسخ | SIL OFL 1.1 |
| ScheherazadeNew-Regular.ttf | نسخ | SIL OFL 1.1 |
| ReemKufi[wght].ttf | کوفی | SIL OFL 1.1 |
| ArefRuqaa-Regular.ttf / ArefRuqaa-Bold.ttf | رقعه | SIL OFL 1.1 |
| Lateef-Regular.ttf | نسخ | SIL OFL 1.1 |
| Gulzar-Regular.ttf | نستعلیق | SIL OFL 1.1 |
| NotoNastaliqUrdu[wght].ttf | نستعلیق | SIL OFL 1.1 |
| NotoNaskhArabic[wght].ttf | نسخ | SIL OFL 1.1 |
| NotoKufiArabic[wght].ttf | کوفی | SIL OFL 1.1 |
| Harmattan-Regular.ttf | نسخ | SIL OFL 1.1 |

## فرض مهم (برای فازهای بعد)

سبک‌های «ثلث» و «دیوانی» در حال حاضر **فونت آزاد OFL باکیفیت ندارند**.
برنامه از ابتدا واردکردن قلم کاربر (TTF/OTF با SAF) را پشتیبانی می‌کند؛
اگر مالک فونت ثلث هستید، فقط فایل را در همین پوشه قرار دهید و مدخل آن را
به `fonts.json` اضافه کنید — باید مطمئن باشید مجوز انتشار آن را دارید.
