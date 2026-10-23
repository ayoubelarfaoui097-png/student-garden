# Student Garden 0.4.0 device evidence

An isolated API 35 emulator was used. The user's original AVD was not reset.

- Installed 0.3.0, created a fictional task through the UI, then installed 0.4.0
  with `adb install -r`. The updated UI displayed the task. SQLite snapshots,
  including the write-ahead journal, matched every saved record field exactly:
  ID, record kind, JSON content, update timestamp and Trash state.
- Granted microphone access through Android's permission dialog. A flashcard
  displayed Recording, a pinned Stop button and an advancing elapsed timer.
  The screenshot shows 00:13 / 03:00 in `screenshots/0.4/recording-device.png`.
- Host audio was disabled in the emulator. This checks recording state and the UI;
  it does not establish microphone quality or audible speaker output.

The 64-test automated run separately covered stopping recording, Back during a
replacement recording, preserving the clip for Save, the three-second Undo prompt,
agenda colors, backup compatibility and existing student workflows. This local
revision was superseded by 0.5.0 before source packaging.
