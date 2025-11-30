# Quick Start Guide

This guide will help you get started quickly with the Wake Up project.

## ⚡ 5-Minute Setup

### 1. Clone the project

```bash
git clone https://github.com/your-username/wakeup.git
cd wakeup
```

### 2. Open in Android Studio

1. Open Android Studio
2. **File** → **Open** → Select the `wakeup` folder
3. Wait for Gradle synchronization (first time: 2-5 minutes)

### 3. Run the application

1. Connect an Android device or start an emulator
2. Click **Run** (▶️) or press **Shift+F10**
3. The application will install and launch automatically

## 🔧 Useful Commands

### Build the project

```bash
./gradlew build
```

### Install on a device

```bash
./gradlew installDebug
```

### Clean the project

```bash
./gradlew clean
```

### View logs

```bash
adb logcat | grep WakeUp
```

### Build a release APK

```bash
./gradlew assembleRelease
```

APK will be in: `app/build/outputs/apk/release/app-release.apk`

## 📱 Testing the Application

1. **Create a test event** in the calendar with reminders (e.g., 1 hour before, 1 minute before)
2. **Grant permissions** to the application on first launch
3. **Wait** for the service to start (notification visible)
4. **Verify** that reminders trigger at the right time

## 🐛 Quick Troubleshooting

### Project doesn't compile

```bash
# Clean and rebuild
./gradlew clean build
```

### Dependencies don't download

- Check your Internet connection
- Verify you have access to Maven Central and Google repositories

### Application doesn't start

- Check logs: `adb logcat | grep WakeUp`
- Verify device/emulator is connected: `adb devices`

### Reminders don't display

- Check that permissions are granted
- Check that events have configured reminders
- Check that the service is active (notification visible)

## 📚 Resources

- [README.md](README.md) - Complete documentation
- [CONTRIBUTING.md](CONTRIBUTING.md) - Contributing guide
- [CHANGELOG.md](CHANGELOG.md) - Version history

## 💡 Tips

- Use Android Studio for development (best integration)
- Enable developer mode on your phone for debugging
- Use `adb logcat` to see logs in real time
- The project automatically downloads JDK and SDK if configured

## 🆘 Need Help?

- Open an [issue](https://github.com/your-username/wakeup/issues)
- Check the [complete documentation](README.md)
- Read the [contributing guide](CONTRIBUTING.md)
