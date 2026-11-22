<p align="center">
  <img src="https://raw.githubusercontent.com/Vilson1999/Big-Share/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="Big Share app icon" width="180" />
</p>

<h1 align="center">Big Share</h1>

<p align="center">
  <strong>Developer:</strong> Vilson Sonkar<br />
  <strong>Latest release:</strong> <a href="https://github.com/Vilson1999/Big-Share/releases/latest">v1.0.7</a><br />
  <strong>Package:</strong> com.big.share<br />
  <strong>Language:</strong> Kotlin
</p>

<p align="center">Ultra-lite offline file sharing between nearby Android devices using Wi-Fi Direct.</p>

## App Details

### Send

Choose files on your device, discover nearby peers, and send selected files directly to another connected device. The picker supports images, videos, music, installed apps, APK files, and browsing files from Android's system picker.

### Receive

Make your device discoverable to nearby Big Share devices and prepare to receive incoming files. Received files are saved in the Downloads folder when supported by the device.

### Transfer

Track transfer progress, speed, and status while files move over a direct peer-to-peer connection. Transfers can also be cancelled.

### Wi-Fi Direct

Discover nearby devices and establish a local peer-to-peer connection without a router or internet connection.

### Bluetooth Discovery

Bluetooth discovery helpers assist nearby-device discovery alongside Android Wi-Fi Direct APIs.

### Profiles and sharing

Set a display name and avatar for nearby-device identification. Files can also be shared to Big Share from Android's system share menu.

## Releases and Updates

The latest published release is **v1.0.7**. The project publishes separate APK downloads for ARMv7 and ARMv8 devices. Open the [latest release](https://github.com/Vilson1999/Big-Share/releases/latest) for release notes and download links, or use these always-latest downloads:

- [ARMv7 (`armeabi-v7a`) APK](https://github.com/Vilson1999/Big-Share/releases/latest/download/MiniShare-armv7-armeabi-v7a.apk)
- [ARMv8 (`arm64-v8a`) APK](https://github.com/Vilson1999/Big-Share/releases/latest/download/MiniShare-armv8-arm64-v8a.apk)

GitHub Actions builds and publishes APKs when code is pushed to the configured branches or a version tag is pushed. For builds without a version tag, the release tag is generated automatically from the workflow run number (`v1.0.<run number>`). Check the Releases page for the current version and its release details.

## Technology

- **Primary language:** Kotlin
- **UI:** Jetpack Compose and Material 3
- **Platform:** Android
- **Connectivity:** Android Wi-Fi Direct APIs and Bluetooth discovery helpers
- **Minimum Android version:** Android 9 (API 28)
- **Target Android version:** Android 16 (API 36)

## Getting Started

1. Clone this repository and open it in Android Studio.
2. Allow Gradle to finish syncing.
3. Connect an Android device that supports Wi-Fi Direct.
4. Build and run the `app` configuration.
5. Grant the nearby-device and file-access permissions when prompted.
6. Enable Wi-Fi and use the Send or Receive flow to connect with another device.

## Project Structure

```text
app/src/main/java/com/example/
├── model/       # Transfer data models
├── p2p/         # Wi-Fi Direct and Bluetooth discovery
├── picker/      # Device media scanning
├── transfer/    # File transfer engine
├── ui/          # Compose screens and components
└── viewmodel/   # App state and UI coordination
```

## Build Configuration

The Android app's Gradle configuration currently specifies:

- Application ID: `com.big.share`
- Version name: `1.0`
- Version code: `1`
- Minimum SDK: `28`
- Target SDK: `36`
- Compile SDK: `36`

The published GitHub release version is tracked separately from these Gradle values. See `app/build.gradle.kts` for the complete build and dependency configuration.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
