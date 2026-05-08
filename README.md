# Blackout Zone

Offline medical triage for disaster, blackout, and low-connectivity environments.

Blackout Zone is an Android application built for the Gemma 4 Good Hackathon. It is designed for situations where internet access is unavailable or unreliable, such as natural disasters, power outages, rural response settings, and field operations. The app runs a bundled on-device Gemma model and local medical protocol database entirely offline.

## Why This Matters

During emergencies, people often lose access to hospitals, phone networks, cloud services, and search engines at the exact moment they need clear next-step guidance. Blackout Zone focuses on one practical question:

What should someone do first when medical help is delayed and there is no internet?

The app is not a replacement for a clinician. It is a resilience tool that gives structured triage guidance, highlights red-flag symptoms, and grounds AI output in local first-aid protocols.

## Core Features

- Fully offline Android triage flow
- Bundled Gemma model for on-device generation
- Local SQLite protocol database for grounded medical guidance
- Fast rule-based red-flag detection for critical emergencies
- Atomic model extraction and file verification for large model reliability
- Release build hardened with R8/ProGuard rules
- Simple Flutter interface for symptom input and triage output

## How It Works

1. The user enters symptoms in plain language.
2. Kotlin checks for immediate red flags such as severe bleeding, breathing difficulty, stroke signs, unconsciousness, and cardiac symptoms.
3. If a red flag is detected, the app returns emergency guidance immediately without waiting for AI generation.
4. For non-red-flag cases, the app searches the local SQLite protocol database for relevant triage records.
5. The matched local protocols are injected into the Gemma prompt as grounding context.
6. The on-device model returns a concise triage response using the format:

```text
TRIAGE: RED/YELLOW/GREEN
Reason: ...
Recommended actions:
1) ...
2) ...
```

## Offline Architecture

```text
Flutter UI
   |
   v
Android MethodChannel
   |
   v
Kotlin MainActivity
   |
   +--> RedFlagDetector
   |
   +--> TriageFunctionBridge
   |       |
   |       v
   |   SQLite protocol database
   |
   v
GemmaInferenceEngine
   |
   v
Bundled local Gemma model
```

No cloud API is required for triage. The model and protocol database run locally on the Android device.

## Production Hardening

The project includes several implementation details intended to make the app reliable outside a demo environment:

- The large model file is bundled as a Flutter asset.
- The Android host copies the model into internal storage before inference.
- File copying uses a temporary file, byte-size verification, disk sync, and atomic rename.
- The engine detects tar-style model bundles and extracts the inner model file safely.
- The local SQLite database prevents the model from relying only on general language-model knowledge.
- R8/ProGuard rules preserve MediaPipe GenAI classes and JNI bindings in release builds.

## Download the APK

The APK is too large to store directly in the normal GitHub source tree. It is published as a GitHub Release asset.

Release download:

```text
https://github.com/ArbaazARAhmed/BlackOut_Zone_APP/releases/tag/v1.0.0
```

Download `app-release.apk` from the release page and install it on an Android phone or emulator.

## Judge Testing Steps

1. Download `app-release.apk` from the GitHub Release.
2. Install the APK on an Android device or emulator.
3. Turn on Airplane Mode.
4. Open Blackout Zone.
5. Enter sample symptoms, for example:

```text
35M, crushing chest pain for 20 minutes, sweating, shortness of breath
```

```text
Person has heavy bleeding from leg wound, cloth is soaking through
```

```text
Child has mild burn with blister after hot water spill
```

6. Confirm that the app returns triage guidance without internet access.

## Build From Source

Prerequisites:

- Flutter SDK
- Android SDK
- Android NDK
- Git LFS

Clone and fetch the large model:

```bash
git clone https://github.com/ArbaazARAhmed/BlackOut_Zone_APP.git
cd BlackOut_Zone_APP
git lfs pull
```

Install dependencies:

```bash
flutter pub get
```

Build the release APK:

```bash
cd android
./gradlew :app:assembleRelease
```

The APK will be generated at:

```text
build/app/outputs/flutter-apk/app-release.apk
```

## Technical Stack

- Flutter for the Android user interface
- Kotlin for native Android triage and model orchestration
- SQLite for offline medical protocol grounding
- MediaPipe GenAI / on-device Gemma inference
- Git LFS for large model storage
- GitHub Releases for APK distribution

## Important Medical Disclaimer

Blackout Zone provides first-aid style triage guidance for emergency and low-connectivity scenarios. It does not diagnose medical conditions and does not replace professional medical care. If emergency services are available, users should contact them immediately for severe symptoms such as trouble breathing, heavy bleeding, chest pain, stroke signs, unconsciousness, or rapidly worsening condition.

## Repository

```text
https://github.com/ArbaazARAhmed/BlackOut_Zone_APP
```
