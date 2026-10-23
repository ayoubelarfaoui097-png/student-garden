# Student Garden for Android

A personal student desk that opens straight into today's work. This is a native Android
application with offline study records, private media, optional online videos, and no required account.
The web app remains separate and unchanged. A portable backup is the explicit transfer boundary.

## Identity

Jira blue #0052CC is the anchor. Paper-white #FFFFFF reading surfaces sit on #F6F7F9,
with dark ink #1C1E21 and secondary text #606770. The feature accents retain their jobs:
gold #FFC933 for notes; coral #F56C61 for tasks; calendar blue #3895F5 for planning;
green #20B66A for habits; teal #16B8A6 for focus; cyan #16A6C2 for lesson reviews;
orange #F59B38 for flashcards. Each accent pairs with a darker readable label. Habits
and agenda sessions can choose their own color; agenda sessions also accept #RRGGBB values.
The launcher and app header use an original young sapling: a small curved trunk and
three fresh leaves on the blue app tile. Dark mode uses deep neutral surfaces and readable accent variants.

Source Sans 3 is the interface and reading face. Arabic uses Noto Sans Arabic with
mirrored navigation. Fonts are bundled under SIL OFL and scale with system settings.

## Native structure

Version 0.10.0 continues the existing native layout. Today, Video, Study, Cards, and Habits
are the five persistent destinations; wide windows use a navigation rail. The top bar
provides Focus and Settings. Study combines Agenda, Notes, Courses and Lessons using the existing book icon; Tasks remain on Today. Lessons accept preset or custom #RRGGBB colors. Videos supports explicit YouTube searches and pasted video links, locally saved lessons and resume positions. The root video player has no bottom navigation or rail; landscape playback hides the app toolbar and system bars and restores them on exit. Cards
opens a deck library, then a nine-card grid with search. Notes and recall use full-screen
editors. Note formatting uses visible toggle states and a wrapped toolbar; native
table editors support draft recovery and confirmed removal of filled cells.
Study includes the day-filtered, week-navigable agenda. Settings exposes
backup, language, appearance, device lock, and Trash. Agenda editors show the
selected calendar day as a read-only label and expose only a time picker.

Color distinguishes features while navigation stays blue. Today prioritizes actual
work and review counts; empty agenda copy is omitted. Tasks use checkable rows, notes
use paper surfaces with a gold edge, and habits use independently colored check-ins.
The card front offers a single reveal action. Only the back shows the two-by-two rating
controls and the next long-term interval. No generated image is used as a UI screenshot
or embedded interactive page: these are real Compose controls and persistent records.

Use 48dp touch targets, 16dp page gutters, 24dp section gaps, 8dp control spacing, and
12dp sheet corners. A task is a row; a note is a quiet writing surface; a habit is a weekly
ledger. Avoid applying the same floating card to every type of content. No gradients,
glow, motivational filler, decorative numbers, or automatic entrance animations.

## Motion

Motion responds to an action: a short destination crossfade, a deliberate card flip,
a checked-day transition, and a bottom sheet that follows Android conventions. State
survives rotations and process recreation. Respect the system animator duration setting.
Long lists use keyed lazy layouts. Database reads, media work, and backup parsing stay
off the UI thread. Release builds use R8. Performance claims require measured evidence.

## Native behaviors

Cards support text, images, audio files, and microphone recordings; prompts and answers
stay separate. Again / Hard / Good never advance the separate 48h → 7 days → 1 month →
Complete step. Decks isolate study queues. Lessons use their own 48h → 7 days → 1 month
reviews. Personal habit schedules preserve fixed time zones and existing check-in history.

The focus timer uses a persisted end time and a native completion notification/alarm.
The foreground timer remains visible when needed; no always-on polling or network service.
Media selection uses the system picker. Recording requests microphone access only when
requested, checks revocation, and stops when the editor leaves. A pinned red status
shows Recording, elapsed time toward the three-minute limit, and Stop while recording.
Deletion messages offer Undo for three seconds; Trash retains recoverable records. Backups use the system
file picker and validate paths and sizes before restoring. Existing data is never silently
replaced by an import; previews and explicit confirmation explain a restore.

## Privacy and distribution

No analytics, advertising SDKs, Firebase, Google Play Services, account keys, or required hosted proxy. Video requests go directly to YouTube using NewPipeExtractor; personal study records stay local.
No personal email/password is included in the source or APK. Private app storage and an
optional device-credential lock protect local records. F-Droid metadata describes only
implemented features. Public source hosting, a release tag, and F-Droid maintainer review
remain publishing steps; a generated APK does not mean acceptance into the repository.

## Audio and video study

Recording uses real microphone amplitude, displayed as a bounded rolling waveform above a pinned Stop button. Silence remains a low baseline. Save is disabled during capture. Notes support audio-only content as well as attachments alongside rich text and tables.

Video search retains YouTube's relevance ordering by default, with server-side filters and automatic pagination near the end of each list. Playlist playback retains the selected playlist and never automatically starts the next lesson. Notes captured below a video keep their source timestamp and appear in Study. Fullscreen Back first restores inline playback and portrait on phones; it never leaves a library locked in landscape. Large-screen orientation requests may be ignored by Android, so inline playback also works without waiting for a physical rotation.

## Version 0.10 direction

Keep the existing contract: action blue #0052CC, white #FFFFFF reading surfaces,
page gray #F6F7F9, ink #1C1E21 and secondary #606770. Notes retain gold #FFC933;
notification categories reuse their existing feature accents. Source Sans 3 and
Noto Sans Arabic remain the only type families.

Video is a compact learning library: search controls first, then lazy thumbnail
rows with 16dp breathing space. Playlist thumbnails carry a dark count strip;
channels use round avatars. No result dividers, promotional introduction, repeated
filter summaries or persistent playback instructions. The screenshot supplied by
the user establishes the playlist layout; app colors continue to follow the theme.
Study contains Agenda, Notes, Courses and Lessons. Tasks stay on Today.

Frame captures and voice notes are timestamped local study records, with explicit
controls below the player and visible confirmation after saving. Routine reminders
use category accents and recognizable colored artwork; the system status icon
remains the app's monochrome mark. Date-based reviews require opt-in and use the
chosen study time. Stale catch-up alerts are suppressed.

Design review: the web scanner does not parse Kotlin (0 files scanned); it is not
evidence of native visual quality. Native screens must be inspected on Android.
This is a surgical pass inside the existing design system. A new palette, decorative
cards and a marketing-style header were rejected as unrelated to the student task.

### Video notebook and playback in 0.11

Keep one Study note per video. Text stays in the portable rich HTML body; recordings and frames retain individual timestamp references. Show a compact preview below the player and open the full note for editing. Keep the native video surface above the scrolling notes to avoid disposing it while the student reads. Start with adaptive playback up to 720p; keep Auto and explicit higher qualities available. Preserve pause, position and speed when switching quality.
