# Student Garden 0.6.0 device checks

An isolated AOSP API 35 AVD was used. Existing user AVDs were not reset.

| Check | Result |
| --- | --- |
| Upgrade from 0.5 | PASS. Same preview package and debug signing certificate, installed with `adb install -r`. |
| Data preservation | PASS. Four original records and two HTML note bodies match every pre-upgrade stored field, including IDs, JSON, timestamps and Trash state. |
| Actual background alarms | PASS. Native AlarmManager registered three alarms: lesson reviews, grouped study reminders and focus completion. Each used a real broadcast PendingIntent. |
| Six notifications delivered | PASS. IDs 44–49 were present at 21:38:00 UTC with the activity in the background. Exact-alarm access and notification permission were granted in this AVD. |
| Notification destinations | PASS. Actual notification PendingIntents opened focus, tasks, agenda, cards, habits and lessons. Cold/warm request handling and unfinished note recovery also pass automated tests. |
| Duplicate suppression | PASS. After canceling the delivered alerts and resuming/loading the app, they did not immediately reappear. |
| Native permission flow | PASS. Revoked permission is reported as blocked. Denying the system prompt leaves the app usable. Retry shows an explanation; granting changes status to allowed. |
| Daily practice time | PASS. The native picker changed 9 AM to 9 PM; private preferences persisted minute 1260. |
| Test alert and actual tray tap | PASS. The Settings test button posts an Android notification, and tapping its visible tray entry returns to Settings. |
| Bold on/off and persistence | PASS. Typed regular text, enabled bold for a second paragraph, switched it off for the third, saved, force-stopped, reopened and viewed the saved note. |
| Bold HTML | PASS. `<p>Regular text</p><p><b>Bold text</b></p><p><span style="font-weight: 400;">Regular again</span></p>` |

Raw instrumentation output and delivered IDs are retained in the adjacent text
files. The runner's own notification uses ID 2147483647. Screenshots use fictional
records and show the saved bold comparison and real Android test notification.

Device testing does not cover physical microphone quality, audible playback,
manufacturer battery policies, TalkBack, physical reboots or long-term delivery.
The emulator was stopped after testing. Historical device reports remain available.
