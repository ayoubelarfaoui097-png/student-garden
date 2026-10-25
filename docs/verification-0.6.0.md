# Student Garden 0.6.0 verification

18 October 2026. Version code 6. The complete unit/Compose suite passed **94 tests**
with zero failures or skipped tests. Android lint reports **zero errors and 49
warnings**, mainly dependency notices, extension suggestions and unused resources.
The preview and optimized unsigned release both build successfully. Exact results
and APK hashes are in `build-results.json`. Previous reports are retained.

## Bold and notes

Bold uses an explicit weight rather than relying on a style toggle's inherited
weight. Heading size is independent, so choosing or removing a heading preserves
bold. Separate static font faces (Garden Sans and Garden Arabic, derived locally
from the bundled OFL fonts) distinguish regular, medium, semibold and bold on Android.
A native rendering test checks that both Latin and Arabic bold use substantially
more ink than regular text. No font downloads or online services are used.

Compose tests cover selected text, mixed regular/bold selections, pending styles
before typing, combining bold/italic/underline, heading independence, lists, clear
formatting, Undo/Redo, table editing, row/column protection, draft recreation and
HTML persistence. Through the Android UI, a regular/bold/regular comparison was
saved and reopened after a cold launch; the middle line is visibly bold and the
other two are regular. See `screenshots/0.6/bold-device.png`.

## Reminder behavior

| Feature | Reminder rule |
| --- | --- |
| Lessons | Once per due review; the next interval starts after completing the actual review. |
| Tasks | Once per due date/time, while unfinished. Tasks without a due date have no alarm. |
| Agenda | At the plan's start, while unfinished and the session has not ended. |
| Flashcards | One daily notification when cards are due. The default time is 9 AM, adjustable in Settings. Short learning intervals do not cause repeated card alerts. |
| Habits | At the daily time on scheduled weekdays, in each habit's saved time zone, unless checked or archived. |
| Focus | At the timer's completion, using its existing monotonic timing and sound settings. |

Notes and courses do not have independent deadlines. Trashed records and completed
cards/lessons/tasks/plans are excluded. Settings has category switches, permission
status, the daily time, optional precise-alarm access and a real test notification.

Repository changes recalculate the next alarm. Private receivers restore schedules
after boot, package replacement, clock/time-zone changes and exact-alarm permission
grants. Tests exercise the receivers' rescheduling code and separately check their
private manifest declarations and event filters. These are simulated system-event
tests, not physical reboot tests.

Automated tests cover disabled categories, blocked notification channels, denied
permission, completed/expired/trashed items, deadline edits, deduplication across
multiple tasks, daily recurrence, future habit start dates, checked weekdays,
fixed habit time zones and a daylight-saving transition. A denied notification does
not consume its delivery token. Cold/warm launch tests handle notification requests
once; Compose tests verify every reminder destination and preservation of a note
that was still being edited.

## Real Android delivery and UI

One instrumentation test passed on the isolated AOSP API 35 emulator. It saved
fictional upcoming records, scheduled actual AlarmManager alarms, put the activity
in the background and waited. All six application notifications (IDs 44–49) arrived
at the deadline. The test invoked their actual PendingIntents, checked the expected
activity destinations and verified that reopening did not immediately repeat them.
The receiver delivery methods were not called directly by this test. See
`reminder-instrumentation-result.txt`, `reminder-alarm-result.txt` and
`device-checks-0.6.0.md`. The additional ID 2147483647 in the raw result belongs to
the instrumentation runner.

Through the native UI, notification permission was denied, the app remained usable,
an explanation was shown on retry, and granting permission restored the allowed
status. The daily time changed from 9 AM to 9 PM and persisted. The built-in test
notification appeared in Android's tray; tapping it returned to Settings. A screenshot
is saved as `screenshots/0.6/test-notification-device.png`.

## Data and regression coverage

Database schema and backup format remain version 1. The 0.5 preview was updated
with `adb install -r`, keeping the same package ID and signing certificate. All four
pre-existing fictional records and both note HTML bodies matched their original
stored rows exactly after the checks. Test fixtures are confined to the isolated AVD.
Original personal projects and web data were not modified or imported.

The prior agenda colors/selected-date workflow, three-second deletion Undo,
recording indicator, flashcard progression, habits, focus, backup, Trash and native
table workflows remain covered. Navigation now dismisses an old deletion message
so it cannot cover controls on the next page; the Trash recovery regression passes.
English, Arabic, French and Spanish have matching sets of **207 string keys**.

## Limits and distribution

Android may delay inexact alarms or restrict delivery under battery settings.
Exact-alarm access is optional; force-stopping the app prevents alarms until it is
opened again. Physical microphone/speaker quality, manufacturer power policies,
TalkBack and long-running real-device behavior are not established by these tests.
An additional API 26 test attempt stopped while fetching an unavailable test runtime;
all reported automated and device results use API 35. The minimum declared API is 26.

The signed preview is installable; the optimized release is unsigned. No Internet
permission, account, analytics, ads or proprietary service is required. Source
packaging excludes AVDs, local databases, keys and caches. F-Droid source preparation
and metadata are included, but a public repository, release tag and F-Droid review
are still required; the app has not been submitted or published.
