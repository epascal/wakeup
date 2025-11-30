# Wake Up

Android application that monitors the phone's calendar and displays event reminders in full screen, even when the screen is locked.

## 📱 Features

- **Calendar reading** : Accesses system calendar events and reminders
- **Automatic detection** : Continuously monitors upcoming reminders (all configured reminders)
- **Full screen display** : Displays reminders even on locked screen
- **Reminder management** : Allows rescheduling a reminder in 5 minutes, 10 minutes, 30 minutes, or 1 hour
- **Done button** : Allows closing the reminder screen
- **Background service** : Runs as foreground service to ensure continuity
- **Garmin sync** : Notifications are optimized for synchronization with Garmin watches

## 🚀 Prerequisites

- **Android Studio** (recent version recommended)
- **JDK 21** or higher (automatically downloaded by Gradle if configured)
- **Android SDK** with API 35 (Android 15)
- **Gradle 9.0** or higher

## 📦 Installation

### Option 1: Clone and build

```bash
# Clone the repository
git clone https://github.com/epascal/wakeup.git
cd wakeup

# The project is configured to automatically download:
# - JDK (Java 21)
# - Gradle dependencies
# - Android SDK Platform 35

# Build the project
./gradlew build

# Install on a connected device
./gradlew installDebug
```

### Option 2: Open in Android Studio

1. Open Android Studio
2. File → Open → Select the project folder
3. Android Studio will automatically:
   - Download Gradle 9.0
   - Download JDK 21 if needed
   - Sync dependencies
   - Download SDK Platform 35 if needed
4. Wait for complete synchronization
5. Run → Run 'app' or press Shift+F10

## 🔧 Configuration

### Automatic configuration

The project is configured to automatically install all dependencies:

- **JDK** : Automatically downloaded via `org.gradle.java.installations.auto-download=true`
- **Android SDK** : Automatically downloaded during compilation
- **Dependencies** : Downloaded from Maven Central and Google

### Manual configuration (optional)

If you prefer to use a local JDK, create a `local.properties` file at the project root:

```properties
sdk.dir=/path/to/your/android/sdk
```

**Note** : The `local.properties` file is already in `.gitignore` and will not be committed.

## 📋 Required Permissions

The application automatically requests the following permissions:

- `READ_CALENDAR` : To read calendar events and reminders
- `SYSTEM_ALERT_WINDOW` : To display interface above other applications
- `DISABLE_KEYGUARD` : To display on locked screen
- `WAKE_LOCK` : To wake the screen on reminder
- `POST_NOTIFICATIONS` : For notifications (Android 13+)
- `SCHEDULE_EXACT_ALARM` : To schedule exact alarms (Android 12+)

## 🎯 Usage

1. **First launch** : The application will request necessary permissions
2. **Automatic start** : The monitoring service starts automatically
3. **Reminder detection** : When an event reminder is detected (within 30 seconds), the reminder screen automatically displays
4. **Reminder management** : Use buttons to reschedule the reminder or "Done" to close
5. **All reminders** : The application triggers **all** configured reminders for each event (1 hour before, 1 minute before, etc.)

## 🏗️ Project Structure

```
wakeup/
├── app/
│   ├── src/main/
│   │   ├── java/org/wakeup/
│   │   │   ├── MainActivity.java          # Main activity
│   │   │   ├── ReminderActivity.java      # Full screen activity for reminders
│   │   │   ├── CalendarMonitorService.java # Monitoring service
│   │   │   ├── ReminderReceiver.java      # Receiver for reminders
│   │   │   ├── BootReceiver.java          # Restart after boot
│   │   │   └── ServiceNotificationDismissReceiver.java
│   │   ├── res/                           # Resources (layouts, drawables, etc.)
│   │   └── AndroidManifest.xml
│   └── build.gradle                       # App module configuration
├── build.gradle                           # Project configuration
├── settings.gradle                        # Modules configuration
├── gradle.properties                     # Gradle properties
└── README.md
```

## 🔨 Build and Deployment

### Debug Build

```bash
./gradlew assembleDebug
```

APK will be generated in `app/build/outputs/apk/debug/app-debug.apk`

### Release Build

```bash
./gradlew assembleRelease
```

APK will be generated in `app/build/outputs/apk/release/app-release.apk`

**Note** : To sign the release APK, configure a keystore (see [Android documentation](https://developer.android.com/studio/publish/app-signing))

## 📊 Versions

- **Minimum SDK** : 26 (Android 8.0)
- **Target SDK** : 35 (Android 15)
- **Compile SDK** : 35 (Android 15)
- **Java** : 21
- **Gradle** : 9.0
- **Android Gradle Plugin** : 8.10.0

## 🛠️ Dependencies

- `androidx.appcompat:appcompat:1.7.0`
- `com.google.android.material:material:1.12.0`
- `androidx.constraintlayout:constraintlayout:2.2.0`

## 🐛 Troubleshooting

### Service doesn't start

- Check that permissions are granted
- Check that the service notification is visible
- Restart the application

### Reminders don't display

- Check that calendar events have configured reminders
- Check that the application has `READ_CALENDAR` permission
- Check logs with `adb logcat | grep WakeUp`

### Build fails

- Check that you have an Internet connection (to download dependencies)
- Clean the project: `./gradlew clean`
- Delete the `.gradle` folder and try again

## 🤝 Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for more details.

## 📝 License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0) with additional non-commercial restrictions.

**Key points:**
- ✅ Free for personal and non-commercial use
- ✅ You can modify and distribute the code
- ✅ You must share your modifications under the same license
- ❌ **Commercial use is prohibited** without explicit written permission
- ❌ Cannot be used in commercial products or services

See the [LICENSE](LICENSE) file for full details.

## 📧 Contact

For any questions or suggestions, please open an issue on GitHub.

## 🙏 Acknowledgments

- AndroidX for support libraries
- Android community for resources and documentation
