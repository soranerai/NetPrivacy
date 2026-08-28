# NetPrivacy

LSPosed-модуль и Android-приложение для скрытия или согласованной подмены
SIM-данных для выбранных приложений.

## Возможности

- выбор целей с поиском по имени и package ID;
- отдельный режим и профиль для каждого приложения;
- встроенные SIM-пресеты и создание пользовательских;
- атомарная policy-конфигурация без kernel-модуля;
- `system_server` читает policy из `/data/system/netprivacy/policy.json`;
- `com.android.phone` получает policy только через закрытый Binder bridge.

## Сборка

```bash
./gradlew :app:assembleDebug
```

Нужен JDK 17 и Android SDK. Debug APK будет создан в
`app/build/outputs/apk/debug/app-debug.apk`.

## Текущий статус

UI, модель профилей, атомарная публикация policy и Binder bridge готовы.
Telephony hooks реализуются поэтапно с проверкой на Android 12–16.

## Безопасность

Политика содержит только синтетические данные профилей. Реальные ICCID, IMSI
и другие идентификаторы не сохраняются приложением.
