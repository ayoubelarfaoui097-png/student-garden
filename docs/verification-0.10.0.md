# Student Garden 0.10.0 verification

22 October 2026. Local development build, version code 10.

## Implemented behavior

- Video-first mixed search includes a small number of relevant playlists and channels. Video relevance order is retained; the suggested channel prefers the leading video's creator. Videos appear while the extra suggestions load. Dedicated channel, playlist and video filters remain available. Channel pages open the channel's videos and support continuation pages.
- Playlist rows follow the supplied NewPipe reference: a real thumbnail with a dark count overlay, title and uploader. Result rows have spacing instead of divider lines. Search, channel and playlist lists automatically request their next page near the end. Failed requests offer an explicit retry; empty or duplicate continuation pages stop pagination.
- Students can record a voice note directly below a video. Starting recording pauses playback, the waveform and pinned Stop remain visible, and stopping saves a local timestamped note. Playing a voice note also pauses the video.
- Capture frame pauses playback and copies only the decoded video surface through PixelCopy. The JPEG is stored privately with the video ID and captured position. Saved frame notes appear below the player and in Study; they can be edited with written text or have the image removed. Audio and images are included in backup export and restore.
- Navigation is named Video and Study. Study contains Agenda, Notes, Courses and Lessons. Tasks remain accessible from Today and from their reminder destination. Existing records, deadlines and lesson colors are preserved.
- Routine notifications use category colors and custom colored artwork, retaining Android's monochrome app status icon. Task and agenda reminders use explicit schedule times. Daily lesson, card and habit practice is opt-in and uses the chosen study time. Missed daily sessions wait for the next study time; task/agenda alerts more than two hours late are suppressed. Delivered-token history still prevents duplicates and respects permission revocation and completed records.
- Repeated video introductions, filter summaries and playback-policy paragraphs were removed. The app keeps its existing Source Sans 3/Noto Sans Arabic fonts, feature colors and light/dark themes. Design choices and primary references are in [UI research](ui-research-0.10.0.md).

## Build and automated checks

`assembleDebug assembleRelease lintDebug testDebugUnitTest` completed successfully
(`BUILD SUCCESSFUL in 10m 23s`). All **134 tests passed**, with no failures, errors
or skips. Coverage includes progressive search, automatic pagination, channel
navigation, video voice notes, frame-note saving and capture failure, JPEG/audio
backup restore, legacy note defaults, reminder opt-in/timing, private colored
notifications, and the existing study/card/fullscreen workflows.

Lint has **zero errors and 66 warnings**, including dependency notices, unused
legacy copy/resource suggestions and existing style warnings. Arabic reminder
plurals define all six required quantities. All four locales have matching 266
string keys. Exact hashes and totals are in [build results](build-results-0.10.0.json).
The Preview APK's signature was verified with apksigner. The optimized production
APK is unsigned and requires the owner's release key before distribution.

Database schema remains 1. Backup format remains 2 and version 1 imports remain
supported. Older notes default to an empty image list. This does not promise that
older app versions display image notes created by 0.10.

## Live service and Android evidence

Only an isolated AOSP API 35 emulator and fictional QA study records were used.
Existing user AVDs were not reset.

- A live catalog request returned **20 videos, one playlist and one channel**, with a continuation. Opening the channel returned **30 videos**, followed by **30 more**. The playlist result contained a thumbnail URL. The later creator-matching refinement and progressive loading are covered by the final build and UI tests.
- Searched for English lessons, switched to playlist results and visually checked the thumbnail/count overlay and spacing in `screenshots/0.10/playlists.png`.
- The upgraded app displayed Video and Study, and Study showed Agenda, Notes, Courses and Lessons without a Tasks tab. A previously saved QA Biology text note remained available.
- Opened the public Amoeba Sisters lesson [Photosynthesis (UPDATED)](https://www.youtube.com/watch?v=CMiPYHNNg28). Native playback produced video frames. Captured the displayed frame at **3:44**, then granted microphone permission, recorded a voice note and stopped. Both notes displayed **3:44**; the JPEG contained the video image without app controls. The recorded clip switched to Stop audio on playback. Screenshot: `screenshots/0.10/video-frame-and-voice-notes.png`.
- Granted notification permission from Settings and explicitly sent its test notification. Visually checked the category-colored app accent and book artwork in Android's notification shade: `screenshots/0.10/notification.png`. Daily categories remained opt-in; automated tests verify actual deadline delivery and suppression of stale or duplicate alerts.

Video frames and search thumbnails in verification captures belong to their
respective creators and are not bundled into the application's interface.
Manual device checks used the Preview candidate. The final optimized release was
compiled and linted; physical microphone quality, TalkBack, OEM notification
appearance and a broad device matrix were not established here. YouTube extraction
and ranking depend on the remote service and can change or be restricted. No
notes, recordings or frame captures are uploaded to YouTube. No external release
or F-Droid submission was made.
