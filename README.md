# CasinoMaster

Android observe-only app monitor.

## 0.4.0 update

This build adds the first usable monitoring workflow:

- **+ ADD APP** picker for launchable Android apps visible to the package manager.
- Saves one selected app locally.
- Opens the selected app from CasinoMaster.
- Optional floating **CM** control using Android's user-granted overlay permission.
- Floating panel for display-only bet amount and collect target settings.
- Draggable **BET** and **COLLECT** markers whose positions are saved locally.
- Explicit Android **MediaProjection** consent for screen inspection.
- Local/offline visual-change heuristic with states such as LEARNING, WAITING / STABLE, and SCREEN CHANGE.
- No Accessibility Service.
- No automatic taps, automatic bet placement, or automatic cash-out.

### Android behavior

Android 11+ limits package visibility, so the picker queries launchable activities rather than requesting broad QUERY_ALL_PACKAGES.

The floating control uses SYSTEM_ALERT_WINDOW and TYPE_APPLICATION_OVERLAY; Android requires the user to explicitly grant overlay access in Settings.

Screen inspection uses MediaProjection. Android 14+ requires user consent for each capture session and requires the appropriate foreground-service type.

### User flow

1. Open CasinoMaster.
2. Tap + ADD APP.
3. Select the app you want to monitor.
4. Set display-only amount and collect target, then save.
5. Tap OPEN SELECTED APP + FLOATING CONTROL.
6. Grant overlay access if Android asks.
7. Tap the floating CM button to open the manual control panel.
8. Use SHOW / MOVE MANUAL MARKERS to position visual guides.
9. Tap START SCREEN INSPECTION in CasinoMaster and approve Android's screen-capture dialog.
10. Return to the selected app. The floating control can show the offline visual state.

## Safety scope

The monitor is intentionally observe-only. It does not control another app, inject taps, place bets, press cash-out, or make wagering decisions.

The amount and multiplier fields are display/settings data only. The offline detector reports visual changes; it is not a prediction engine and should not be treated as a guarantee about a game's next result.

## Permissions

The 0.4.0 build declares:
- SYSTEM_ALERT_WINDOW for the optional floating control.
- FOREGROUND_SERVICE for the user-visible monitor service.
- FOREGROUND_SERVICE_MEDIA_PROJECTION for screen inspection.
- FOREGROUND_SERVICE_SPECIAL_USE for the floating monitor service.

It does not declare Accessibility, camera, microphone, location, contacts, SMS, or storage permissions.

## Build

Open the project in Android Studio and build the debug APK.

GitHub Actions builds the debug APK and uploads it as a workflow artifact.

## Security

Never bypass Play Protect or Android permission screens. Use trusted distribution and keep production signing keys private.
