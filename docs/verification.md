# Student Garden 0.7.0 verification

20 October 2026. Version code 7. The complete unit/Compose suite passed **103 tests**
with zero failures or skipped tests. Android lint reports **zero errors and 42
warnings**, primarily existing dependency notices, extension suggestions and unused
resources. The installable preview and optimized unsigned release both build.
Exact results and APK hashes are in `build-results.json`. The 0.6.0 report is retained
as `verification-0.6.0.md`.

## Flashcard layout

A blank or whitespace-only question creates no text, separator or associated gaps.
The image, answer and audio controls form one group centered vertically inside the
card when they fit. Long content remains scrollable, with rating controls outside
the scrolling region. Questions that contain text still appear, and their divider
appears only when the answer is revealed.

Compose tests verify blank and real questions, front/back visibility, measured
vertical centering, reveal behavior and long content on a 320 × 640 dp phone.
A real PNG is decoded for the picture test. Robolectric uses BitmapFactory because
its ImageDecoder does not support Coil file sources; the real Android device check
uses the app's unchanged default decoder. Both front and answer were visually
inspected on the isolated API 35 emulator. See `screenshots/0.7/` and
`device-checks-0.7.0.md`.

## Random review order

Each review session shuffles its initial eligible cards. Refreshing the clock,
changing unrelated data or rotating the screen preserves the active card and the
remaining queue. Newly due cards and returning short-interval learning cards join
after cards already waiting. Trashed or no-longer-due cards leave the queue.
Practice all visits each card once.

Four deterministic queue tests verify complete permutations, distinct seeded
sessions, stable refreshes, new/returning cards and duplicate/deleted ID handling.
Compose tests verify saved session order and reveal state across recreation,
completion of a 12-card practice session without duplicates, and a newly due card
not interrupting a revealed answer. The prior image-card editing regression was
updated to tolerate shuffled review order while preserving its schedule assertions.

Through the real Android UI, eight fictional numbered cards were reviewed in the
order **1, 5, 4, 2, 3, 6, 7, 8**. Every card appeared once. All eight advanced to
stage 1 and produced eight review records. See `review-order-device-0.7.0.json`.

## Data and previous features

The 0.7.0 preview was installed over 0.6.0 with the same package and signing key.
All six pre-existing fictional records and three saved note HTML bodies matched
exactly before and after the update and native review checks. The test fixtures
were removed afterward. Database schema and backup format remain 1. The user's
physical phone, original web project and personal study files were not modified.

The full regression suite also covers rich notes and bold font rendering, tables,
agenda colors and selected-day planning, deletion Undo and Trash, recording status,
backup validation, focus timing, habits, lesson progression and reminder rules.
The six-category real AlarmManager delivery test passed on 18 October for 0.6.0;
reminder code is unchanged and that device test was not rerun for this update.
Its evidence remains in `device-checks-0.6.0.md` and
`reminder-instrumentation-result.txt`.

## Distribution and limits

The preview APK is installable as an update to Student Garden Preview. The optimized
release APK is unsigned. Source packaging excludes local databases, media, keys,
AVDs and caches. The source includes production build metadata for F-Droid. Acceptance, signing
and distribution are handled separately by F-Droid's maintainers.

Reported UI and regression results use API 35. Physical microphone/speaker quality,
manufacturer battery policies, TalkBack and long-running real-device behavior
remain outside these checks. Android can delay notifications under power
restrictions; force-stopping prevents alarms until the app is reopened.

## F-Droid source preparation

The concrete recipe points to `ayoubelarfaoui097-png/student-garden` and tag
`v0.7.0`. F-Droid server 2.4.5 metadata lint completed without diagnostics. Its
source scanner found zero fatal problems and no warnings in the clean source
copy. The scanner removed the Gradle wrapper from its disposable scan copy as
part of its normal handling; the published source retains the wrapper. See
`fdroid-source-scan-0.7.0.json`.

The same F-Droid tool also scanned the optimized 0.7.0 unsigned production APK
with zero fatal findings. The APK hash is recorded in
`fdroid-binary-scan-0.7.0.json`. This is local validation; the maintainer build and
review remain separate.
