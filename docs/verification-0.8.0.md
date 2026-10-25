# Student Garden 0.8.0 verification

22 October 2026. Local development build, not published or submitted to F-Droid.

## Build and regression checks

`assembleDebug assembleRelease lintDebug testDebugUnitTest` completed successfully.
All **110 unit and Compose tests passed**, with no failures, errors or skipped tests.
Android lint reported **zero errors and 53 warnings**, including dependency notices,
existing style suggestions and resource warnings. Exact APK hashes and totals are in
`build-results-0.8.0.json`. The source includes the new video/lesson compatibility tests.

The preview APK is debug signed. The optimized production APK remains unsigned.
NewPipeExtractor v0.26.5, Media3 1.11.1, OkHttp 4.12.0 and Coil networking 3.3.0
are pinned. The extractor explicitly selects Rhino interpreted mode. The unused
JSR-223 engine is excluded; R8 ignores references from unused desktop Dynalink and
Java-bean conversion code. Real playback is checked on Android, not only on the JVM.

## Observable Android checks

A fresh AOSP API 35 emulator was created under `/tmp/student-garden-avds`; existing
user AVDs were not reset. Only fictional QA study records were used.

- Installed the prior 0.7.0 preview, created a QA lesson using the UI, then installed
  the new preview as an update. Its complete stored database row matched exactly,
  including its ID, JSON, timestamps and Trash state, before any edit.
- The bottom navigation shows Today, Videos, Studying, Cards and Habits. Studying
  retains the original book icon and includes Tasks, Agenda, Notes, Courses and Lessons.
- Edited the legacy lesson through the UI to use `#8F3FBA`. SQLite confirmed the
  saved custom color and unchanged review deadline/stage. The focused hex field
  stayed visible above the keyboard and Save stayed reachable.
- Opened a public YouTube lesson using its pasted link. Real video frames advanced
  in the native player. Saving it persisted its metadata and resume position.
- The fullscreen control rotated into landscape. Visually verified actual video,
  with no bottom menu, no navigation rail, no app toolbar and hidden system bars.
  Android displayed its standard first-use fullscreen tutorial; it was dismissed.
- Back returned to portrait, and Back again restored the Videos area and menu.
  Saved videos displayed the lesson with **Resume at 6:06** (366,944 ms).
- Temporarily test-signed the optimized production APK outside the repository,
  installed it alongside Preview, searched YouTube in the actual release UI and
  played the first result. Visually verified real video frames in `video-release.png`;
  no application fatal exception was logged. The delivered production APK is unsigned.
- A separate live JVM check searched for a lesson, returned 20 results and resolved
  seven playback options and six caption tracks for the selected public lesson.

Screenshots: `screenshots/0.8/studying-lessons.png`, `video-portrait.png`, and
`video-landscape.png`, and `video-release.png`. Video frames are verification captures from the Amoeba Sisters
lesson [Photosynthesis (UPDATED)](https://www.youtube.com/watch?v=CMiPYHNNg28);
they are not bundled in the application's UI or used as original artwork.

## Compatibility and limits

Database schema remains 1. Lessons missing a color default to cyan. New saved-video
records use the existing generic record store. New backups use version 2; this app
still imports version 1. Older app versions cannot import version 2 backups.
No notes, schedules or media are uploaded by the video feature. See `PRIVACY.md`.

YouTube extraction is unofficial and can be blocked or change. Playback availability
is not guaranteed. Public on-demand videos are supported; no login, restricted/paid
content, live-stream guarantee, video downloads, AI, autoplay feed or background
playback is implemented. The normal Android security and provider restrictions apply.
Physical audio quality, OEM battery behavior, TalkBack and broad device performance
were not established by the emulator checks. The combined build is GPL-3.0-or-later,
with original Apache-2.0 notices preserved. No external publishing was performed.
