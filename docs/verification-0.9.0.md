# Student Garden 0.9.0 verification

22 October 2026. Local development build; not published or submitted to F-Droid.

## Changes

- Flashcard and note recordings show a rolling waveform sampled from real microphone peaks every 80 ms. Silence stays at the baseline; recording retains the elapsed timer and pinned Stop control. Save is disabled until recording stops. Capture stops when the app leaves the foreground.
- Notes support recorded or imported audio alongside rich text and tables, including audio-only notes with a title. Clips can be played or removed. Recording retains the three-minute/10 MB limit, with up to 20 clips per note. Audio attachments are included in backup export and restore.
- Video search includes videos and playlists, preserves YouTube's relevance order by default, and supports result type, duration, upload date and sorting filters. Search and playlists have explicit pagination. Playlist links and watch links containing a playlist retain their context. Normal search words are not mistaken for playlist IDs.
- A playlist's other lessons remain available below the player. Students choose the next lesson; no automatic playback queue starts.
- Notes written below a video keep its ID and captured timestamp and appear in Studying. Timestamp controls seek to that point, and the full note editor can reopen the source. Draft text survives state restoration and fullscreen transitions. Leaving or switching playlist lessons protects unsaved note text.
- The visible fullscreen Back arrow and Android Back both return to inline playback first. Phones return to portrait. The original orientation setting is restored when leaving the player; inline fallback also works on devices that ignore orientation requests. Fullscreen excludes app navigation and hides system bars.

## Build and automated evidence

`assembleDebug assembleRelease lintDebug testDebugUnitTest` completed successfully
(`BUILD SUCCESSFUL in 12m 21s`). All **123 tests passed**, with zero failures,
errors or skipped tests. This includes audio-note recording/save guards, draft
restoration, byte-for-byte audio backup restore, missing/invalid media rejection,
legacy-note compatibility, search protocol/link validation, relevance order,
playlist navigation, timestamped note saving and fullscreen orientation recovery.

Android lint reported **zero errors and 54 warnings**, including dependency,
existing style/resource suggestions and a playlist-count plural suggestion. All
four locales contain the same 257 string keys. Database schema remains 1; backup
format remains 2 and version 1 imports remain supported. New note source fields
have defaults for older records. Exact APK hashes and totals are recorded in
[build-results-0.9.0.json](build-results-0.9.0.json).

The installable Preview APK is debug signed. The optimized production APK is
unsigned and needs the owner's release signing key before distribution. Both
are built from this source. No signing key is included in the source package.

## Observable device and service checks

Only isolated AOSP API 35 emulators and fictional QA study records were used;
existing user AVDs were not reset.

- Granted microphone permission through Android's prompt, recorded an audio-only
  note, observed the waveform and disabled Save, stopped capture, saved it,
  reopened it and played the resulting AAC/M4A clip. The private clip was
  approximately 456 KB. Screenshot: `screenshots/0.9/audio-note-recording.png`.
- Live search for “khan academy biology” returned videos and playlists in a stable
  server-provided order. Applying the playlist filter returned playlist results.
  The filter dialog is shown in `screenshots/0.9/search-filters.png`.
- A live catalog check returned 19 mixed results and 20 more results on the next
  page. Playlist-only search returned 19 results. The selected course returned
  100 videos followed by 39 more. A combined long-duration/year/view-count
  request returned 20 videos, each longer than 20 minutes. Date and sort settings
  are sent to YouTube; no local random ranking is added.
- Opened Khan Academy's public Biology overview lesson from the selected
  [biology playlist](https://www.youtube.com/playlist?list=PLMguVyWLTFUmEKYQ8tI_jVDp_Lhp7Xltu).
  Native video frames advanced. Saved a “QA Biology” note below the player and
  verified that it also appeared in Studying. Screenshot: `screenshots/0.9/video-notes.png`.
- Entered landscape fullscreen and visually verified video with no app menu,
  rail, toolbar or system bars (`screenshots/0.9/video-fullscreen.png`). Tapped
  the actual return arrow and observed a 1080×2400 portrait player. Leaving it
  restored `SCREEN_ORIENTATION_UNSPECIFIED`, then returned to the playlist and
  video library normally.

Video frames in verification screenshots belong to Khan Academy; they are not
bundled into the application UI. Manual 0.9 device checks used Preview. The
optimized 0.9 release was compiled and linted; physical-device audio quality,
TalkBack, OEM behavior and a broad device matrix were not established here.
YouTube access and extraction can change or be restricted. Search quality uses
YouTube's returned ranking; the app cannot guarantee the best lesson for every
student. Notes and audio stay local and are not sent to YouTube. See PRIVACY.md.
