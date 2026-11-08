# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Extended App control API: URI opening and activity launching with full intent control

## [1.0.0-beta.3] - 2025-01-10

### 🎉 Public Release
- **First public release of DroidWright source code**
- Source code is now publicly available on GitHub
- Repository opened for community contributions
- Comprehensive documentation added for UI element inspection

### Added
- Material Design 3 UI overhaul
- Sora Code Editor with TextMate syntax highlighting
- Script export feature (SAF-based)
- Enhanced console logger
- Example scripts with DroidScript metadata
- UI element inspection guide with Appium Inspector
- Documentation for finding UI elements (resource IDs, text, descriptions)
- Alternative tool recommendations (Developer Assistant, ADB tools)

### Changed
- Complete UI redesign of all screens
- Storage permission handling for Android 11+
- Code organization and refactoring
- Error handling improvements

### Fixed
- Scripts not showing on Scripts page
- Storage permission false negative on Android 11+ emulators
- App crash when exporting scripts
- Blank file export issue

### Removed
- ScreenshotManager and related code
- Unused storage permission checks on Android 11+

### Security
- Improved file I/O operations
- Better permission management on Android 11+

## [1.0.0-beta.2] - 2025-11-08

### Added
- In-app update detection
- Tampermonkey-style script format with metadata headers
- Script import from file and URL
- Script metadata editor
- Active state indicators

### Changed
- Script format requires metadata headers
- Database schema updated to version 2
- UI improvements and visual feedback

### Fixed
- ANR issues on startup
- Default/profile object errors
- Database schema conflicts

### Performance
- Optimized startup time
- Improved script validation
- Better memory management

## [1.0.0-beta.1] - 2025-11-01

### Added
- JavaScript-based automation framework
- UI interaction API (tap, swipe, scroll, type)
- App control API (launch, close)
- Real-time logging and logcat integration
- Material Design 3 UI
- Script management (create, edit, import)
- Code editor with syntax highlighting
- ANR prevention and resource monitoring
- Script pause/resume/stop functionality
- GitHub update checker
- Example scripts and documentation

### Security
- Permission management for accessibility service
- Display over other apps permission
- Storage permission handling

### Performance
- Resource monitoring and rate limiting
- ANR prevention mechanisms
- Optimized script execution

