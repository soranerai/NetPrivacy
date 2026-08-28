# NetPrivacy

LSPosed-модуль и Android-приложение для скрытия или согласованной подмены
SIM-данных для выбранных приложений.

## Возможности

- выбор целей с поиском по имени и package ID;
- отдельный режим и профиль для каждого приложения;
- встроенные SIM-пресеты и создание пользовательских;

## Сборка

```bash
./gradlew :app:assembleDebug
```

Нужен JDK 17 и Android SDK. Debug APK будет создан в
`app/build/outputs/apk/debug/app-debug.apk`.
