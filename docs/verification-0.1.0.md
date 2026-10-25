# Android preview verification

The Android project is separate from the existing web project. The web checkout remains
clean at commit `7235efc63ad758ad9eceb2262cc9f1a5316ff036`. No hosted records, media or
web credentials were copied, deleted or changed.

## Automated checks

The final local verification on 16 October 2026 completed successfully:
`testDebugUnitTest lintDebug assembleDebug assembleRelease`.
All 28 tests passed with no failures, errors or skipped cases. Android lint reported
zero errors and 30 warnings, primarily dependency-update notices, unused resources,
API-specific attributes and Kotlin style suggestions. Warnings were not suppressed.

The test suite contains 28 tests: 8 repository tests, 7 scheduling tests, 3 backup tests
and 10 Compose interface tests. It covers:

- Review progression, short practice preserving the long interval, month-end dates,
  completed cards and lesson completion.
- Habit schedules, time zones, streaks, future-day rejection and persisted check-ins.
- Task editing, rich-note persistence, trash/restore and failed-save rollback.
- Duplicate review rejection and idempotent focus completion, including stale alarms
  arriving after pause.
- Backup round trips, merge conflicts, malformed manifests and ZIP path traversal.
- Full-screen note entry, card reveal, deck isolation, individual habit colors and
  custom focus pause/resume.
- Native rendering on a phone, a small 320dp phone, a tablet, Arabic RTL and dark mode.

Compose screenshots use Android native drawing under Robolectric. Fixture content in
screenshots is test data; the installed application starts with an empty workspace.
The HTML-specific avoid-ai-design scanner does not analyze Kotlin; its zero-file output
is not used as visual evidence.

## Device checks

A fresh Android 15 / API 35 AOSP x86_64 emulator without Google Play Services was used.
The preview APK and a temporarily test-signed optimized release both launched successfully.
The release refused `run-as` access because it is not debuggable.

- A one-minute focus session posted the completion notification after the app moved
  to the home screen. Android assigned the bundled bell to the alarm channel.
- Microphone permission was requested in context. A recording was stopped, saved as
  an audio card and opened for playback without a player error. The resulting AAC
  file was independently parsed at 44.1kHz, mono, 13.676553 seconds.
- Updating the preview with `install -r` retained the recorded media in private storage.
- French was selected in the optimized release; the activity recreated, retained its
  navigation destination and displayed translated settings.
- The merged application manifests contain no Internet permission. Release builds
  use R8 and resource shrinking. Automatic cloud backup and device transfer are excluded.

The emulator ran without host audio input/output. Recording-container and notification
behavior were checked; physical microphone quality, speaker playback and manufacturer
battery-saver behavior still need a phone check before a public release. No FPS or
startup-speed benchmark is claimed.

## Visual review

The installed frontend-design and avoid-ai-design skills guided the design contract.
Rendered review covered typography, reading surfaces, feature accents, contrast roles,
phone/tablet navigation, touch targets, RTL and the full-screen editor. It caught and
corrected Source Sans 3's default light variable-font weight. Font axes are now explicit.
Material surface and secondary colors are overridden so default violet tints do not return.

Store screenshots and the 512px icon are in `fastlane/metadata/android/en-US/images/`.
The device focus screenshot is in `docs/screenshots/`. Source assets and font licenses
are included. The source tree contains no sample records loaded at runtime.

## Distribution boundary

The preview has the separate `.preview` package ID and a test signature. The unsigned
optimized release is for source-based distribution preparation. Neither is an F-Droid
acceptance or submission. A public repository, a real release tag, completed repository
metadata and maintainer review are still required.
