# Student Garden 0.7.0 device checks

20 October 2026. Isolated AOSP API 35 emulator, 1080 × 2400 at 420 dpi.
All fixtures are fictional and confined to the QA emulator.

## Update compatibility

Installed version code 7 over the 0.6.0 preview using the same package and signing
key. Before launching the update, the stored rows in `records`, `note_contents`
and `habit_checks` were compared with the pre-update snapshot. All 6 existing
records, all 3 saved note HTML bodies and the empty check table matched exactly.
Database schema remains 1. No data migration or record deletion is introduced.

## Blank-question picture cards

Added a test deck containing one card with an empty question, the app-owned tree
image and answer “ein Baum”. The front displays the image in the vertical center.
After tapping Reveal, the answer and image form one centered group. No question
space or horizontal divider is drawn. Rating controls remain outside the card and
reachable. Captures were visually inspected:

- `screenshots/0.7/blank-question-front-device.png`
- `screenshots/0.7/blank-question-answer-device.png`

These captures use the application's normal Coil decoder on Android. The separate
Robolectric layout tests use BitmapFactory because its ImageDecoder implementation
cannot decode Coil file sources; they still decode an actual PNG.

## Review order

The numbered-card sequence and completion result are recorded in
`review-order-device-0.7.0.json`. The session is driven through the actual Review,
Reveal and 48 hours controls, with a fresh Android layout observation before each
interaction. The observed order was 1, 5, 4, 2, 3, 6, 7, 8. All eight cards advanced
to stage 1 and created eight review records. The six earlier records and three
note bodies still matched exactly. The fixtures were removed and the isolated
emulator was stopped afterward.

Full automated regression results are in `build-results.json` and
`verification.md`. The actual reminder-alarm checks from 0.6.0 remain documented in
`device-checks-0.6.0.md`; notification code was not changed in this update.
