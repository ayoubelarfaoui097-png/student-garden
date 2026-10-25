> Development draft for 0.12.0; not submitted or published. Use the current verification report before submitting.

# New App: Student Garden (com.jdinohaila.studentgarden)

Student Garden is a native, offline study organizer for Android 8 and later. It
combines tasks and an agenda, rich notes, lesson reviews, text/image/audio
flashcards, scheduled habits and a focus timer. It supports English, Arabic,
French and Spanish, and includes local reminders with optional exact alarms.

- Application ID: `com.jdinohaila.studentgarden`
- Source and license: https://github.com/ayoubelarfaoui097-png/student-garden (GPL-3.0-or-later)
- Release tag: `v0.12.0` (version code 10)
- Build: Gradle, JDK 17, Android SDK 36, build-tools 35.0.0
- Metadata and screenshots: `fastlane/metadata/android/en-US/`
- F-Droid recipe: `fdroid/metadata/com.jdinohaila.studentgarden.yml`
- Privacy: https://github.com/ayoubelarfaoui097-png/student-garden/blob/v0.12.0/PRIVACY.md
- Validation: https://github.com/ayoubelarfaoui097-png/student-garden/blob/v0.12.0/docs/verification.md

This submission is authorized by the project owner. The release uses Internet only for the optional video feature. It has no required accounts, advertising SDKs, analytics or Google Play Services.
Dependencies are pinned FLOSS packages from Google Maven and Maven Central;
third-party licenses and locally generated artwork/font sources are included.
F-Droid may build and sign the production application ID. The existing separately
signed `.preview` package is for testing and is not the requested F-Droid build.

The current source passed 110 automated tests and Android lint with no errors.
An API 35 emulator test verified real background alarms for lessons, tasks,
agenda plans, flashcards, habits and focus, including notification actions.
Physical-device microphone/speaker quality, manufacturer battery policies and
long-term alarm reliability have not been established. These limitations are
recorded openly in the verification report; no physical-device results are claimed.
