# Device checks — Student Garden 0.3.0

18 October 2026. Isolated AOSP API 35 emulator `student-garden-verify`; no existing
AVD was reset. Fixtures were created for testing and are not shipped in the APK.

| Check | Result and evidence |
| --- | --- |
| Upgrade from 0.2.0 | PASS. A lesson created through the old UI retained the exact ID, title, learned time, deadline and stage after an in-place preview update. |
| System picture picker | PASS. Selected a PNG through Downloads, saved it in a deck, and opened its picture front. |
| Tap picture to reveal | PASS. Front hid Blue; tapping the picture showed Blue and the rating controls. |
| Recording and playback | PASS. Granted microphone access contextually, recorded a short AAC clip, saved it with an image card, and playback entered Stop audio without a media error. Audible speaker/microphone quality was not verified. |
| Card interval | PASS. The first advance scheduled 48 hours. Restored practice displayed 7 days as the next advance; it did not reset the stage. |
| Lesson background notification | PASS. A near-future deadline injected only into the isolated QA database posted a local reminder while the app was backgrounded. Text contained no lesson title. |
| Focus in background | PASS. Entered 1 custom minute, started focus, left the screen, and received Focus session finished. Export contained exactly one completed one-minute focus block. Precise-alarm access was not granted. |
| Android backup export | PASS. Used CreateDocument to save a ZIP. Inspected five records, the image and recording, including card stage and completed focus history. |
| Android backup merge | PASS. Selected that ZIP through OpenDocument, confirmed the preview, and received Backup merged. Existing records were retained. |
| Device credential gate | PASS. Set a test credential on the isolated emulator, enabled the app lock, backgrounded the app, returned to the gate, and authenticated through Android before Settings became visible again. |
| Optimized release offline | PASS. Temporarily test-signed the final unsigned release outside the project, installed the production package ID, enabled airplane mode, and restored the preview ZIP. Picture rendering, hidden answer, reveal, audio playback and the next 7-day interval worked in the minified build. |
| Cold notification launch | PASS. In the optimized build, granted notifications, backgrounded the app, killed its background process with am kill (not force-stop), and confirmed the process was gone. Tapping the notification restarted the app directly in Lessons with the imported lesson visible. |
| Warm notification intent | PASS. From Settings, delivered another notification navigation token to the existing optimized activity. Lessons opened without creating a second activity. |
| Runtime crash log | No application FATAL EXCEPTION observed during these checks. |

The optimized release was signed temporarily only to install it for QA. The delivered
release APK remains unsigned; the delivered installable APK is explicitly the preview.
No release signing key or device backup is included in the source archive.

Physical-device sound quality, TalkBack, manufacturer battery restrictions, performance
benchmarks and broad Android-version coverage remain outside these emulator checks.
