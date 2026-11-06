# HomePilot

Android client for controlling Home Assistant devices and viewing configured energy sensors.

## Build an installable debug APK

Requirements: JDK 17, Android SDK platform 35 and build tools 35.0.0, and Gradle 8.9.

```sh
gradle --no-daemon assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`. The GitHub Actions workflow builds and uploads this APK when run manually or after relevant source changes. Debug APKs are suitable for direct installation after enabling installation from the chosen source. A release APK requires signing configuration.

## Connect

Enter the base Home Assistant URL (for example `https://homeassistant.example:8123`) and a Long-Lived Access Token created in your Home Assistant profile. The token is held in AndroidX encrypted preferences. The app loads the state list from `/api/states` and calls Home Assistant service endpoints for supported controls. Use a trusted network and HTTPS for remote access.

The Energy tab uses the configurable entity IDs in Settings. Initial IDs are examples only; replace them with the actual sensor entity IDs in your Home Assistant instance. An unavailable sensor is shown as such rather than replaced with sample readings.

## Current scope

This is a functional first client, not a substitute for validating against a specific Home Assistant setup. It lists lights, switches, fans, climate, covers, and automations; actions invoke the corresponding Home Assistant domain service. The energy view reads current sensor states. Historical charts, areas/favorites, sensor unit formatting, and richer climate controls are not implemented yet. Action errors appear on the next refresh status only when refreshing fails.
