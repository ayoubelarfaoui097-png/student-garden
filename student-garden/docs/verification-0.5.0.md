# Student Garden 0.5.0 verification

Date: 18 October 2026. The original web and native project folders and personal
study data were not edited. Historical reports are retained alongside this file.
All 79 tests pass. Lint reports zero errors and 42 non-blocking warnings, principally
dependency notices, extension suggestions and unused resources. Exact results and
APK hashes are in `build-results.json`.

## Changes verified

- Agenda uses the day selected in its calendar bar. The editor displays that day
  without a date-picker action. The native time picker changes hours and minutes
  while preserving the selected local date. Custom session colors persist through
  editing, backup, Trash recovery and reload; old sessions retain their schedules.
- Notes preserve selected text when formatting is applied. Bold, italic, underline,
  heading and list controls show their selected states. The toolbar wraps on phones.
  The body receives focus when a format is chosen before typing.
- Tables are native editable blocks inserted at the cursor, preserving text and
  formatting before and after the insertion. Cells support text, Unicode and line
  breaks. Controls add/remove rows and columns, protect filled cells, edit a table,
  and remove a whole table with confirmation. Tables use up to 30 rows and 6 columns.
- Text, formatting and table changes share Undo/Redo. Ctrl/Cmd+Z, Shift+Z and Ctrl+Y
  use the same document history as the toolbar. Undo history is local to the open
  editor; note content and unfinished table drafts survive screen recreation.
- Deletion prompts offer Undo for three seconds; records remain recoverable in
  Trash. Flashcard recording displays a pinned status, elapsed time and Stop.
- The launcher, header, themed launcher and notifications use the original sapling.
  Editable SVGs, PNGs and Android vectors are in `branding/`.

## Persistence and regression coverage

The database schema and backup format remain version 1. Native table HTML is stored
in the existing note body. Existing table HTML is retained when unrelated text is
edited; same-size cell edits preserve the untouched cells' rich HTML. Table cells
edited in the native grid contain text. Editing tables requires 0.5.0 or later.

Tests exercise actual Compose controls, HTML reloading, note and table draft recovery,
editing and removal protection, Undo/Redo, and backup round trips containing rich
notes and tables. The existing suite covers tasks, courses, decks, image/audio cards,
short and long review intervals, lessons, notifications, habits, focus timing, Trash,
backup validation and lifecycle navigation. English, Arabic, French and Spanish have the same 192 resource keys.
Rendered phone, small-phone, tablet, RTL and dark-appearance evidence uses fictional
records and is under `screenshots/0.5/automated/`.

## Device evidence and limits

See `device-checks-0.5.0.md` for the preview update, combined bold/italic typing,
table entry and saved-note reload. `device-checks-0.4.0.md` records the advancing
recording indicator. Native UI
tests and an emulator do not establish physical microphone quality, audible output,
TalkBack behavior, manufacturer power rules or long-running reliability.

## Distribution

The preview is debug signed and installable on Android 8+. Its package identity is
unchanged. The optimized release APK is unsigned. The release has no Internet
permission or required hosted service. The source archive excludes local caches,
AVDs, test databases, signing keys and the supplied reference ZIP.

Apache-2.0 source and F-Droid preparation files are included. The app has not been
published to or accepted by F-Droid. A public source repository, matching release
tag, final metadata URLs and maintainer review remain publication steps.
