# Playback and video notebooks in 0.11.0

## Playback changes

The previous implementation serialized all NewPipe extraction behind one mutex,
without cancelling the active HTTP call when its coroutine was cancelled. Leaving
search could therefore leave playback queued behind an obsolete request. Extraction
still uses one gate to protect NewPipe's shared parser and the advanced-search
parameter override. The gate now cancels the active OkHttp call, including response
body reads, and preserves coroutine cancellation rather than exposing it as an I/O
failure. A deterministic test cancels a blocked search and verifies that a queued
playback request proceeds within two seconds.

Extracted lesson metadata and signed stream URLs are cached only in memory, for
three minutes, with a maximum of 12 lessons. Expired entries and least-recently-used
entries are removed. Explicit retry bypasses the cache. A 401, 403 or 410 stream
failure triggers one fresh extraction per recovery incident.

Startup prefers adaptive HLS when available, bounded to 720p/30fps. Direct startup
up to 720p is used when adaptive playback is unavailable. Streams at the same resolution prefer AVC/MP4 and lower frame
rates. The Auto option remains adaptive, constrained to 720p, 30 fps and 3 Mbps;
higher manual qualities remain available. Separate audio prefers AAC around
128 kbps instead of always requesting the highest bitrate.

Playback connections share an OkHttp pool. The player buffers 750 ms before initial
playback and 2 seconds after rebuffering, with a 15–45 second forward-buffer policy,
a 32 MiB target and a 10-second back buffer. These are policy targets, not a promise
that a remote stream will start within 750 ms. Android codec fallback is enabled.
Audio focus and headphone-disconnect handling are enabled.

Quality changes replace the media source on the existing ExoPlayer, preserving
position, playback speed and the user's paused state. Rotation retains the player;
the portrait surface remains attached while the notes and playlist scroll beneath
it. Reopening a note link honors its requested timestamp even when the activity
retains an older player model; ordinary reopening keeps its previous position. Leaving the screen cancels extraction and releases the player. Request
identities prevent an older cancelled load from hiding the current loading state.

A stream failure, six seconds without a first frame, or 12 seconds of active
rebuffering can choose one alternative stream.
If playback remains stalled, a retryable error replaces the endless spinner. Recovery
budgets reset after 30 seconds of healthy playback, so a later expired URL can be
refreshed without enabling an immediate retry loop. User-paused playback does not
trigger the buffering watchdog. Manual retry preserves the intended play/pause state.

Numeric timings for extraction, first frame and renderer readiness are written to
Android's local log under `VideoPlayback`. These logs contain no video ID, title,
URL, note text or audio. No telemetry is sent to a server.

The configuration follows Media3's documented [customization points](https://developer.android.com/media/media3/exoplayer/customization),
[track constraints](https://developer.android.com/media/media3/exoplayer/track-selection)
and [playback troubleshooting guidance](https://developer.android.com/media/media3/exoplayer/troubleshooting).
Performance remains dependent on the connection, YouTube's service and the device's
hardware decoder. Software-emulator timings are not a physical-phone benchmark.

## One notebook per video

A notebook remains a normal Study note with one video reference. Its rich HTML body
holds all written additions, with source timestamps. `videoMoments` associates each
voice recording and frame capture with its own timestamp. The existing flat media
lists remain authoritative for backup export and validation.

Text, voice and frame additions use a mutex-protected read/append/write transaction.
An incoming annotation UUID makes retries idempotent. An editor opened before a new
addition cannot overwrite it silently; it asks the user to reopen the changed note.

On load, import and restore from trash, older active notes for the same video are
combined. Exact HTML, tables, media UUIDs and source positions are retained before
redundant database records are removed in the same transaction. Importing an older
backup again does not duplicate known source annotations. The existing combined
note's identity and course association are retained. Oversized legacy groups that
cannot fit validation limits remain intact instead of being truncated.

A video notebook supports up to 500 audio files, 500 frame images and 2,000 timestamp
entries, within the existing 1 MB note-body limit. Ordinary notes retain their
20-attachment limits. The player shows a compact body preview and the latest eight
timestamp entries; Edit opens the full note, including all media and timestamp
links. Backup format 2 and database schema 1 remain unchanged; legacy JSON fields
use defaults. Downgrading to an older app may hide the new timestamp associations.
