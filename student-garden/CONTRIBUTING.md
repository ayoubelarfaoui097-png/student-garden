# Contributing

Use JDK 17 and Android SDK 36. Run `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease` before proposing changes.

Follow DESIGN.md. User-facing text belongs in all four strings.xml locale files. Keep data and file work off the main thread. Preserve database and backup compatibility; add migration tests before changing formats. Add meaningful workflow tests for behavior changes.

Use only freely redistributable, open-source dependencies with pinned versions and license attribution. Keep networking isolated to the optional video feature. Do not add trackers or required accounts. The application links GPL-3.0-or-later NewPipeExtractor; preserve its obligations and the original Apache-2.0 notices. Do not commit private records, media, backups, local SDK paths, build outputs or signing keys.

Release process: bump versionCode/versionName, update the Fastlane changelog, test the APK, publish all source to the chosen public repository, and create the matching release tag. F-Droid metadata URLs must point to that real repository.
