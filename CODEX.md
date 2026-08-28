# SIM Hide — quick project context

Android/LSPosed module that hides real SIM/telephony data or returns a synthetic
SIM profile to individual selected applications. It targets Android 10+ (min SDK
29), is written in Kotlin with Jetpack Compose/Material 3, and uses JDK 17.

## Architecture

- `SimHideApp.kt` is the complete Compose UI: **Targets** assigns an app a mode
  and optional profile; **SIM presets** manages built-in and custom profiles.
- `model/SimModels.kt` defines profiles, per-app policies, up to five ordered
  favorite profile IDs, and the built-in synthetic profiles. Profiles are unique
  by country ISO. It must never contain or derive a real SIM identifier.
- `data/SimConfigStore.kt` persists the JSON configuration atomically.
- `policy/` serializes the policy and exposes it via the protected
  `PolicyProvider`; `TargetPolicyGrants.kt` restores app URI grants at boot.
- `hooks/TelephonyHooks.kt` applies the policy in target app processes through
  the LSPosed entry point (`SimHideHookEntry.kt`).
- App resources live under `app/src/simhide/res`. English is the default in
  `values/strings.xml`; Russian is in `values-ru/strings.xml`.

## UI and theme

All user-visible UI strings use Android string resources. Users can mark up to
five profiles as favorites; they are shown first and starred in the profile
picker. `SimHideTheme` tracks
the system light/dark setting. Its dark, AMOLED-oriented palette intentionally
matches `../vpnhide_next`: black surfaces, green primary accent and blue/cyan
network accents. Avoid dynamic system colours unless the product explicitly
asks for them, so both apps remain visually consistent.

## Build and verification

Run `./gradlew :app:assembleDebug` from the repository root. The debug APK is
written to `app/build/outputs/apk/debug/app-debug.apk`.

## Maintenance notes

- Keep all display text in resources and update both English and Russian files.
- Policy/profile schema changes must stay compatible with both
  `SimConfigStore` and `SimPolicyCodec`.
- Do not relax the provider's caller checks or add real SIM identifiers to the
  stored configuration.
