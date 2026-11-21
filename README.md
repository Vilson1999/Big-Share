<p align="center">
  <img src="https://raw.githubusercontent.com/Vilson1999/Big-Share/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="Big Share app icon" width="180" />
</p>

<h1 align="center">Big Share</h1>

<p align="center">
  <strong>Developer:</strong> Vilson Sonkar<br />
  <strong>Version:</strong> 1.0<br />
  <strong>Package:</strong> com.big.share<br />
  <strong>Language:</strong> Kotlin
</p>

<p align="center">Ultra-lite offline file sharing between nearby Android devices using Wi-Fi Direct.</p>

## App Details

### Send

Choose files on your device, discover nearby peers, and send selected files directly to another connected device.

### Receive

Make your device discoverable to nearby Big Share devices and prepare to receive incoming files. Received files are saved in the Downloads folder when supported by the device.

### Transfer

Track transfer progress, speed, and status while files move over a direct peer-to-peer connection. Transfers can also be cancelled.

### Wi-Fi Direct

Discover nearby devices and establish a local peer-to-peer connection without a router or internet connection.

### Bluetooth Discovery

Bluetooth discovery helpers assist nearby-device discovery alongside Android Wi-Fi Direct APIs.

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

The Android app is configured with:

- Application ID: `com.big.share`
- Version name: `1.0`
- Version code: `1`
- Minimum SDK: `28`
- Target SDK: `36`
- Compile SDK: `36`

See `app/build.gradle.kts` for the complete build and dependency configuration.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
