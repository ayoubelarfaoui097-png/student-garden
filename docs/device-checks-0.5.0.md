# Student Garden 0.5.0 device checks

Date: 18 October 2026. An isolated AOSP API 35 emulator was used. Existing user
AVDs were not reset. No personal study records were used as test fixtures.

| Check | Observed result |
| --- | --- |
| Update from 0.4.0 | PASS. Installed the 0.5.0 preview with `adb install -r`; both existing test records matched every stored field, including ID, JSON content, timestamp and Trash state. |
| Bold and italic before typing | PASS. Through the native UI, entered a title, enabled Bold and Italic, then typed “Learn every day”. The saved body contains `<b><i>Learn every day</i></b>`. |
| Table entry | PASS. Inserted a table from the toolbar, entered Term / Meaning / Book / Livre into the four cells after checking input focus, applied the table and saved the note. Every cell is present in the saved HTML. |
| Restart and reload | PASS. Force-stopped and reopened the app, opened the saved note, and verified the paragraph and all four cells are visible. |
| Existing record integrity | PASS. The original upgrade-test task and deck still match their pre-update database rows exactly after the note checks. |

SQLite checks included the write-ahead journal. Data snapshots and test tooling
remain outside the distributable source archive. `screenshots/0.5/notes-device.png`
shows the real note editor after reloading the fictional note.

The automated suite separately covers selected-text formatting, toggling styles off,
headings, lists, clear formatting, shared keyboard/toolbar Undo and Redo, table draft
recovery, row/column changes, filled-cell protection, table removal/Undo, HTML and
backup round trips, and agenda time changes that preserve the selected date.

The preview APK signature and version were verified. The optimized release builds
successfully and is delivered unsigned. These checks do not establish physical
microphone quality, audible speaker output, TalkBack behavior or OEM battery rules.
The 0.4.0 device report records the observed advancing flashcard recording indicator.
