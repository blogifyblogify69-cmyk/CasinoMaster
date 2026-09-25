# CasinoMaster

Android-safe observe-only prototype.

## 0.3.0 install-safe scope

This build intentionally removes the Accessibility Service from the APK.

It requests no runtime permissions and no sensitive manifest permissions for:
- Accessibility
- Camera
- Microphone
- Location
- Contacts
- SMS/phone
- Storage
- Overlay
- Network

The app currently provides:
- Android-friendly settings UI
- Display-only amount and multiplier
- Local settings storage
- Clear monitoring status
- Explicit observe-only safety scope

It does **not** place bets, press cash-out, control another app, or execute wagering actions.

## Why the previous APK was blocked

The previous build declared an Android Accessibility Service. Play Protect can block sideloaded apps that request sensitive device access. Removing the Accessibility Service removes that current sensitive-access component from this build.

This does not guarantee that every APK will be accepted by every device's security system. Google Play Protect can still block an APK for other reasons. The correct approach is to distribute a properly signed build from a trusted channel and never bypass Android security controls.

## Signing

Android requires every APK to be digitally signed before installation or update. Keep release private keys outside this repository. For production distribution, use a developer-owned release key and an appropriate distribution channel.

## Future screen monitoring

If screen monitoring is added later, it should use the narrowest Android API that fits the feature and require clear, user-initiated consent. For screen capture on modern Android, MediaProjection requires explicit user consent for each capture session and appropriate foreground-service handling.

## Build

Open the project in Android Studio and build the debug APK.

GitHub Actions builds the debug APK and uploads it as a workflow artifact.
