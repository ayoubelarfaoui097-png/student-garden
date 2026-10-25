# Student Garden 0.12.0 verification

25 October 2026. Local implementation, version code 12. No remote release was published.

## Changes

- Video → Notebooks owns video-linked notes; Study lists ordinary notes. Existing storage, schema 1 and backup format 2 remain compatible. New video entries are text or captured frames. Earlier audio remains playable.
- A continuous timestamped notebook supports inline text edits, frame removal with Undo and source-position playback. Frame timestamps and remove buttons have transparent backgrounds.
- Landscape fullscreen exposes capture alongside player controls. Both hide while watching. PixelCopy reads only the native video surface, preserving playback state. Initial controller visibility is synchronized after rotation.
- Local PDF, DOCX and PPTX export uses Android’s document picker. Text and images are included; audio is not embedded. Study rich formatting becomes plain text in exports. Arabic paragraph direction and long-text pagination are covered by export checks.
- Playlist continuations retain the protobuf fields needed in optimized APKs. Requests follow the active playlist lifecycle; pagination deduplicates results and keeps retry behavior.

Design decisions and primary research sources: [UI research](ui-research-0.12.0.md).

## Automated checks

The first complete build ran debug, Android test, optimized Preview, unsigned release, lint and all 154 local tests successfully. A final run after the native visibility and bilingual export corrections is recorded in the build results alongside the APK hashes.

The regression tests cover legacy rich HTML and duplicate timestamps, concurrent additions and stale edits, frame removal/restore, separation from Study, text editing and export menus, valid OOXML with Unicode and image parts, long slide text, controller visibility, and capture preserving both playing and paused state. Existing backup, review, reminder and ordinary note tests remain included.

The native API 35 Android export test passed. It creates a multi-page PDF with Arabic and English text and a generated image, then renders it using Android PdfRenderer and checks nonempty output. The generated Android PDF was also rendered visually on the host. Android-produced DOCX and PPTX files opened and converted in LibreOffice without repair; rendered pages include Arabic text and the image. The earlier Robolectric PDF failure was its missing native PdfDocument implementation; PDF verification therefore runs on Android.

All four locales contain the same 281 string keys. Whitespace checks pass. Exact final test counts, lint results and artifact fingerprints are in [build results](build-results-0.12.0.json).

## Android observations

Used only the task-created AOSP API 35 AVD `student-garden-012`, with fictional study records. Existing AVD data was not cleared. Cold boot under concurrent build load initially produced a System UI timeout; testing continued after startup completed. The native test was rerun after startup and passed.

- The prior optimized 0.11 Preview failed to load public playlist `PLMguVyWLTFUmEKYQ8tI_jVDp_Lhp7Xltu`. The optimized 0.12 candidate loaded its title, thumbnails and lessons. [Playlist screenshot](screenshots/0.12/playlist.png).
- Opened Biology overview, entered landscape fullscreen and captured at 1:31. The notebook contains the clean video frame without a camera icon, playback controls or app chrome baked into it. [Captured frame](screenshots/0.12/captured-frame.png).
- A bilingual fictional notebook displays text and frames in sequence, with transparent frame controls. [Notebook screenshot](screenshots/0.12/notebook.png).
- Exported a notebook using the actual Android PDF document picker to Downloads. The app confirmed completion and the file contains the note and frame.
- The optimized Preview installs over the same Preview package and matches the 0.11 signing certificate. It is non-debuggable and contains a baseline profile. The production APK remains unsigned.

These are functional emulator observations, not a physical-device performance benchmark. Public video and playlist availability still depends on the remote service and connection. Verification imagery from Khan Academy belongs to its creator and is not bundled in the app’s interface. The two-circle illustration is generated test data.

## Final artifacts

The complete final build of debug, Android test, optimized Preview and unsigned release APKs, lint and tests succeeded in 4m 39s. The last unit-test/lint rerun succeeded in 1m 16s: **154 tests, zero failures, errors or skips**. Lint reports **zero errors and 60 warnings**. The signed Preview is **6,804,912 bytes**. APK hashes and the exact source-input fingerprint are in the build results. The source archive excludes local databases, keys, Gradle caches and build outputs.

A stress run exposed two UI tests asserting before asynchronous save/navigation completion. They now wait for the expected UI state; the notebook check waits until its editor has closed and the saved text is visible. The complete suite passed after these corrections.

The corrected Word export was opened and visually checked in LibreOffice: English paragraphs retain left-to-right direction and Arabic paragraphs retain right-to-left direction. The slide exporter measures text height, including explicit line breaks. Text extraction from the final native PDF and the DOCX/PPTX files converted by LibreOffice retains all 80 numbered short lines and all 100 repetitions of the long test text’s key words. The last of 23 test slides was visually checked for clipping.

The native export test passed again with that multiline fixture. The final optimized APK was installed and launched successfully. The earlier optimized candidate with identical player/playlist code confirmed that the previously failing playlist loads, fullscreen capture saves a clean image, and both capture and back controls stay hidden after rotation until player controls appear. [Fullscreen watching](screenshots/0.12/fullscreen-watching.png) shows the unobstructed video.
