# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-12-XX

### Added
- Automatic calendar monitoring
- Full screen reminder display
- Lock screen display support
- Reminder rescheduling (5 min, 10 min, 30 min, 1h)
- Foreground service to ensure continuity
- Support for all configured reminders (not just the last one)
- Optimized synchronization with Garmin watches
- Automatic restart after phone reboot

### Technical
- Migration to Java 21
- Migration to Gradle 9.0
- Migration to Android Gradle Plugin 8.10.0
- Update to Android SDK 35 (Android 15)
- Update AndroidX dependencies to latest versions
- Automatic JDK and dependencies download configuration

### Improved
- Multiple reminders management for each event
- Unique requestCode for each reminder to avoid conflicts
- Improved logs for debugging

### Fixed
- Fixed bug where only the last reminder was triggered
- Fixed lint error with START_STICKY | START_REDELIVER_INTENT

---

## Version Format

- **MAJOR** : Incompatible API changes
- **MINOR** : Backward-compatible feature additions
- **PATCH** : Backward-compatible bug fixes
