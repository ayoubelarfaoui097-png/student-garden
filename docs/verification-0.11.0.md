# Student Garden 0.11.0 verification

23 October 2026. Local build, version code 11.

## Implemented behavior

- One video notebook combines text, recorded voice and captured frames. Every entry retains its source position. The same note opens from Study and below the video. Existing separate notes merge transactionally, preserving exact rich HTML, tables and media. Concurrent additions append safely; retries are idempotent and stale editors cannot overwrite new additions.
- Repeat opens reuse recently extracted streams in memory. Leaving search cancels its active network request so the next video can acquire the extractor. Startup prefers adaptive quality up to 720p; Auto and higher manual qualities remain available. Playback shares connections and retains the same player on quality changes, including position, pause state and speed.
- Codec fallback, fresh-URL recovery and bounded lighter-stream recovery replace some failures and prolonged stalls. A terminal stall offers Retry. Buffering checks stop while paused and restart when playback resumes. No automatic next-video feed was added.
- The video remains above the scrolling note and playlist area. Landscape fullscreen hides app navigation and the system bars. Its back arrow returns to portrait.
- The installable Preview variant now uses R8 and resource shrinking, matching release optimization while retaining the Preview package and development signature. Production release remains unsigned.

Implementation choices, storage limits and primary technical references are in
[playback and notebook details](playback-0.11.0.md).

## Manual Android checks

Only the dedicated AOSP API 35 emulator `student-garden-audio-09` and fictional QA
records were used. Existing user AVDs were not reset.

- Upgraded the prior Preview installation without clearing its data. The former Photosynthesis frame note and voice note became one Study note containing the image and one recording. A separate Biology overview note remained. Screenshot: `screenshots/0.11/merged-study-note.png`.
- Opened the combined note. Its original image, voice clip and 3:44 source positions were present. The source button opened the public [Amoeba Sisters lesson](https://www.youtube.com/watch?v=CMiPYHNNg28).
- Actual native video frames rendered. Added a written entry from the player at 5:26; it appeared inside the existing notebook. Scrolled to the notebook while the player remained visible. Screenshot: `screenshots/0.11/video-notebook.png`.
- Entered landscape fullscreen: the app's bottom menu, toolbar and system bars were absent. Tapping its back arrow restored a 1080×2400 portrait application window and retained the video. Screenshot: `screenshots/0.11/fullscreen.png`.

These initial screenshots used the functional Preview candidate. They establish
layout and note behavior, not playback performance. An optimized direct-start
candidate later took 19,848 ms to its first frame, including a source stall and
recovery to Auto; extraction took 4,647 ms and Auto rendered 3,178 ms after its
preparation. Reopening that Auto stream took 1,137 ms to its first frame, with
15 ms spent retrieving cached metadata. These observations led to adaptive Auto
startup and an earlier, six-second first-frame recovery in the final build.

The candidate measurements were made without a concurrent host build, on a
software emulator. They are observations from one public video and connection,
not a physical-device benchmark or a guarantee for every video. Final optimized
checks and automated results are recorded below after verification.

Video imagery in verification screenshots belongs to its respective creator and
is not bundled in the app interface. Network conditions and remote YouTube changes
can still affect availability and playback. This release does not claim to fix every
possible remote-service or device problem.

## Final build and automated checks

`assembleDebug assembleRelease assemblePreview lintDebug testDebugUnitTest`
completed successfully (`BUILD SUCCESSFUL in 9m 31s`). All **146 tests passed**,
with zero failures, errors or skips. The 12 new tests cover concurrent unified-note
saves, exact legacy HTML/media migration, duplicate-safe restore, combined-note
backup restore, stale-editor protection, stream-cache expiry/eviction, cancellation
of a blocked HTTP call, adaptive/direct fallback selection, quality-switch player
reuse, cancelled-load state, repeat source-timestamp navigation, ordinary resume, one-notebook presentation and a persistent video surface.

Lint reports **zero errors and 66 warnings**. All four locales have matching
271 string keys. `git diff --check` is clean. Exact APK hashes and source-input
fingerprints are in [build results](build-results-0.11.0.json).

The optimized Preview is **6,766,416 bytes**, non-debuggable and installable. Its
signature verifies and matches 0.10 Preview. R8, resource shrinking and the merged
baseline profile are present. The normal development debug APK is 30,884,037 bytes.
The production release is unsigned and requires the owner's release key. No GitHub
release, store submission or remote publication was performed.

## Final optimized APK on Android

The final signed, non-debuggable Preview updated the existing installation without
clearing data. The combined note retained its text, image and recording.
A first catalog request failed and displayed the connection/retry screen; Retry
successfully rendered video after 16,700 ms, including 6,795 ms of extraction.
This confirms recovery behavior and also shows that first loads can still be slow.

In the last repeat-open journey, a request with expired metadata took **10,430 ms**
to its first frame (**7,401 ms** extraction). An immediate reopening took
**4,972 ms**, with **13 ms** retrieving cached metadata. These are two observations
on one public lesson and connection, using the optimized APK without a host build
running; they do not establish a universal speedup or physical-device benchmark.

After letting playback advance, reopened the note's **3:44** source link. The
native player returned to that position, then was paused at **03:48** after a few
seconds of playback. The exact requested-position behavior and ordinary resume
are also covered by automated tests. Screenshot: `screenshots/0.11/optimized-player.png`.
The full combined note is shown in `screenshots/0.11/optimized-notebook.png`.
