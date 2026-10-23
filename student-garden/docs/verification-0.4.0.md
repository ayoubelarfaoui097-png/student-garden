# Student Garden 0.4.0 verification

Date: 18 October 2026. This release continues the existing native Android app.
The original web and native project folders and personal study data were not edited.
The 0.3.0 report is retained in `verification-0.3.0.md`.

## Changes checked

- Agenda sessions have their own named or custom #RRGGBB color. Valid custom values
  are normalized, malformed input cannot be saved, and the timeline adjusts markers
  and checks for contrast on light and dark surfaces.
- New agenda sessions use the selected agenda date. Editing their color preserves
  their original date, time, links, duration and completion state.
- The deletion snackbar offers Undo and dismisses after three seconds. Later recovery
  is available in Settings → Trash. A new deletion replaces an older prompt.
- Flashcard recording shows a pinned red Recording indicator, elapsed time, progress
  toward the three-minute limit and a Stop button. Save returns after recording stops.
  Leaving during recording stops the microphone and offers the existing discard guard.
- The launcher, app header, themed launcher and notifications use the original growing
  sapling. Editable SVGs, PNGs and Android vectors are included in `branding/`.

## Automated evidence

The exact test totals, lint results and APK hashes are recorded in `build-results.json`.
Regression coverage includes existing tasks, notes, courses, decks, review intervals,
lesson reminders, habits, focus timing, backups and lifecycle behavior. New coverage
checks agenda color persistence, legacy database and backup compatibility, custom-color
validation and contrast, deletion timeout/Undo, and recording controls on small phones.

The database schema and backup format remain version 1. Legacy sessions without a color
load as blue; no destructive migration is performed. Tests compare existing schedules
and completion values and round-trip custom colors through backup and Trash restoration.
English, Arabic, French and Spanish contain the same 173 resource keys.

Rendered Compose screenshots are under `screenshots/0.4/automated/`. They use fictional
records. The application does not seed those records into a user's database.

## Device evidence and limits

The isolated API 35 verification emulator is used for release checks; the user's existing
AVD is not reset. See `device-checks-0.4.0.md` for this release's observed device results.
The 0.3.0 report separately records prior image/audio playback and notification checks.
Physical-device microphone quality, audible output, TalkBack, manufacturer power rules
and long-running use remain outside the evidence from an emulator and automated tests.

## Distribution

The preview APK is debug signed and installable on Android 8+. Its package identity is
unchanged, allowing installation over the previous preview with the same signing key.
The optimized release APK is unsigned. The source archive excludes local caches, device
data, signing keys and the supplied reference ZIP. The release still has no Internet
permission or required hosted service.

The project contains Apache-2.0 source and F-Droid preparation files. It has not been
published to or accepted by F-Droid. Public source hosting, a release tag, final metadata
URLs and maintainer review remain necessary publishing steps.
