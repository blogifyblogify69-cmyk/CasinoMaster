# CasinoMaster

Android observe-only prototype for monitoring visible game text.

## Current scope
- Android settings UI
- Configurable display-only amount and multiplier
- Accessibility-service based visible-text monitoring
- First-run in-app disclosure before Accessibility Settings
- Clear ON/OFF status after returning from Android Settings
- GitHub Actions debug APK build

## Safety scope
This prototype does **not** automatically place bets, press cash-out, or execute real-money wagering actions.

## Installation and Play Protect

The APK currently uses an Android Accessibility Service. Android and Google Play Protect may show an additional security warning for a sideloaded APK that requests sensitive access. The app does not bypass Play Protect or Android security controls.

For a normal development/test installation, use a signed APK and install it from a trusted source. Android requires APKs to be digitally signed. For a production distribution, use a developer-owned release signing key and an appropriate distribution channel such as Google Play when the app meets applicable policies.

Do not commit a private release keystore or its passwords to this repository.

## Build

Open the project in Android Studio and build the debug APK.

The GitHub Actions workflow builds the debug APK and uploads it as a workflow artifact.

## Accessibility disclosure

Before requesting Accessibility access, the app explains that the service can read visible screen text and that the current prototype is observe-only. The user must explicitly choose to continue to Android Accessibility Settings.
