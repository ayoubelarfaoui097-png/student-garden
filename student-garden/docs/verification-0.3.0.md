# Student Garden 0.3.0 verification

Date: 18 October 2026. Current evidence is from this workspace. The original native
and web projects and their saved data were left in place. The supplied ZIP was
read as a design reference; its website-generation instructions were not executed.

## Automated coverage

The suite contains 50 tests: 19 Compose workflow tests, 12 repository tests,
7 scheduling tests, 5 backup tests, 4 lesson-notification tests and 3 activity lifecycle tests. Exact results
and build artifacts are recorded in `build-results.json`. The final run passed all
50 tests, with zero lint errors and 40 non-blocking lint warnings (principally
dependency notices, Kotlin extension suggestions and unused resources).

| Feature | Checks |
| --- | --- |
| Tasks and courses | Navigation, task editing, independent status/priority, course links, deletion to Trash, restore |
| Notes | Full-screen rich editor, saving, HTML persistence, failed-save rollback, unsaved draft restoration |
| Planner | Week navigation and selected-day isolation; task/course relationships in backup round trip |
| Picture, text and audio cards | Hidden answers, reveal, deck isolation, nine-card library plus search, content edits retain progress |
| Card scheduling | Again 30 seconds, Hard 5 minutes, Good 10 minutes; separate 48h / 7d / month / Complete progression |
| Lessons | Create at learned time + 48h; actual review + 7d; actual review + calendar month; third review completes |
| Calendar rules | January 31 clamps to February 28, fixed habit time zone, weekday eligibility, streaks |
| Habits | Color edits, archiving and retained dated checks; invalid future checks rejected |
| Focus | Custom minutes, pause, monotonic timing, reboot fallback, idempotent completion |
| Backups | All record kinds, rich notes, image/audio bytes, dates, review stages and checks survive a round trip |
| Backup failures | Merge keeps current edits; malformed ZIP and traversal rejected; missing media fails before overwriting output |
| Reminders | Next deadline only, deduplication, completion/cancellation, denied permission, preserving a note draft when opening lessons |

Compose rendering covers a phone, 320dp small phone, tablet navigation rail, Arabic
RTL and dark appearance. Screenshots use fictional test data. All four resource
sets (English, Arabic, French and Spanish) have matching keys. No sample records
are seeded into the shipped app.

## Android emulator checks

An isolated AOSP Android 15 / API 35 emulator was created for this task. Existing
AVDs were not reset. A lesson was created through the 0.2.0 UI, then the 0.3.0 APK
was installed as an update. The saved lesson's ID, content, learned time, due time
and stage matched byte-for-byte after that upgrade.

A near-future lesson deadline was injected only into the isolated QA database,
so delivery could be exercised without waiting 48 hours. With notification
permission granted and the app in the background, Android posted the generic
lesson reminder. The initial tap behavior exposed a navigation bug; the corrected
version handles both cold launches and an existing activity and keeps the previous
draft in its navigation stack.

Device media checks used the system file picker to attach a generated blue-square
PNG, Android's microphone permission dialog to record a short AAC clip, and actual
playback (the control entered Stop audio). The picture front hid the answer, tapping
the picture revealed Blue, and choosing 48 hours scheduled a future review.
Screenshots are under `screenshots/0.3/`.

Additional device results are recorded in `device-checks.md`. The final optimized
release also passed backup restore, picture reveal, audio playback and cold/warm
notification navigation in airplane mode. It was temporarily test-signed only for
installation in the isolated emulator; the delivered release file is unsigned.

## Security and distribution

The release manifest has no Internet permission. The app's alarm receivers and
locale service are private. The launcher activity is public; AndroidX's profile
installer receiver is protected by Android's DUMP permission. Debug builds also
include development-only preview/test activities. F-Droid must build the release
variant. File pickers grant access only to chosen files. Microphone permission is
requested after Record audio. Notifications and exact alarms check current access
and handle revocation. Android cloud backup and device transfer are disabled.

Records and media are stored in private app storage. Device credentials provide an
optional access gate, not separate database encryption. Explicit exported ZIP
backups are readable. Trash remains local and recoverable; restore wanted Trash
records before exporting. Preferences and running timers are not included in
backup format v1. Database version and backup format remain unchanged.

Apache-2.0 source, font licenses, pinned dependencies, Gradle distribution checksum,
Fastlane store text and an F-Droid metadata template are included. The source
archive excludes local caches, device data, signing keys and the reference ZIP.

## Limits and publication status

This evidence does not establish that every Android device or every possible
input is correct. Physical-device microphone quality, audible speaker output,
TalkBack, OEM battery restrictions and long-running use still need checking.
Background reminders are inexact; Android may delay them. Force-stopping the app
stops its alarms until it is opened again. The web app's existing data is separate
and is not automatically migrated or synchronized.

The preview APK is debug signed and installable on Android 8+. The release APK is
optimized and unsigned. The project is prepared for submission work, not published
or accepted by F-Droid. Publishing requires the actual public source repository,
release tag, final metadata URLs and maintainer review, as described in the
[official submission guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
and [inclusion policy](https://f-droid.org/docs/Inclusion_Policy/).
