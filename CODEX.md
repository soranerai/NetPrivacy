# NetPrivacy — quick project context

Android/LSPosed module that hides real SIM/telephony data or returns a synthetic
SIM profile to individual selected applications. It targets Android 10+ (min SDK
29), is written in Kotlin with Jetpack Compose/Material 3, and uses JDK 17.

## Architecture

- `NetPrivacyApp.kt` is the complete Compose UI: **Targets** assigns an app a mode
  and optional profile; **SIM presets** manages built-in and custom profiles.
- `model/SimModels.kt` contains only SIM profiles, SIM policies and SIM-specific
  validation. `model/WifiModels.kt` contains only Wi-Fi/DHCP profiles and
  Wi-Fi assignments. `model/HideConfig.kt` composes both domains into the
  persisted module configuration. SIM profiles are unique by country ISO.
  No model may contain or derive a real SIM identifier.
- `data/SimConfigStore.kt` persists the JSON configuration atomically.
- `policy/` serializes the policy and exposes it via the protected
  `PolicyProvider`; `TargetPolicyGrants.kt` restores app URI grants at boot.
- `hooks/TelephonyHooks.kt` and `hooks/WifiHooks.kt` apply policies in target
  app processes through the LSPosed entry point (`NetPrivacyHookEntry.kt`). Wi-Fi
  covers `WifiInfo` identity/link getters, `DhcpInfo`, and the Wi-Fi
  interface's `LinkProperties` address/DNS getters.
- App resources live under `app/src/netprivacy/res`. English is the default in
  `values/strings.xml`; Russian is in `values-ru/strings.xml`.

## UI and theme

All user-visible UI strings use Android string resources. Users can mark up to
five profiles as favorites; they are shown first and starred in the profile
picker. The `SIM` and `Wi-Fi` icons in a target row open the corresponding assignment
menus and use colour only to indicate configuration. `NetPrivacyTheme` tracks
the system light/dark setting. Its dark, AMOLED-oriented palette intentionally
matches `../vpnhide_next`: black surfaces, green primary accent and blue/cyan
network accents. Avoid dynamic system colours unless the product explicitly
asks for them, so both apps remain visually consistent.

## Build and verification

Run `./gradlew :app:assembleDebug` from the repository root. The debug APK is
written to `app/build/outputs/apk/debug/app-debug.apk`.

Tagged releases (`v*`) are built by `.github/workflows/release.yml`. Configure
the four `ANDROID_KEYSTORE_*` repository secrets listed in that workflow; the
pipeline then signs and publishes `app-release.apk`.

## Maintenance notes

- Keep all display text in resources and update both English and Russian files.
- Policy/profile schema changes must stay compatible with both
  `SimConfigStore` and `ConfigCodec`.
- Do not relax the provider's caller checks or add real SIM identifiers to the
  stored configuration.
