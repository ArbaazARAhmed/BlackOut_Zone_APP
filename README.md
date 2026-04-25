# blackout_zone_triage

Blackout Zone Triage is an **offline-first triage assistant** designed for disaster/blackout scenarios (no internet). It runs an on-device Gemma model on Android and returns action-oriented triage guidance.

## Getting Started

### Prereqs

- **Flutter** installed and working (`flutter doctor`)
- **Android SDK + NDK installed**
- Accept Android SDK/NDK licenses:

```bash
sdkmanager --licenses
```

### Add the on-device model (offline)

The Android host code expects a model file named **`gemma4.task`** to be packaged as a Flutter asset.

- Put the model at: `assets/gemma4.task`
- `pubspec.yaml` already includes `assets/`

### Run

```bash
flutter pub get
flutter run
```

A few resources to get you started if this is your first Flutter project:

- [Learn Flutter](https://docs.flutter.dev/get-started/learn-flutter)
- [Write your first Flutter app](https://docs.flutter.dev/get-started/codelab)
- [Flutter learning resources](https://docs.flutter.dev/reference/learning-resources)

For help getting started with Flutter development, view the
[online documentation](https://docs.flutter.dev/), which offers tutorials,
samples, guidance on mobile development, and a full API reference.
