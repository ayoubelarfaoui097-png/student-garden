# Source and design research

This workspace continues the owner's Apache-2.0 native Student Garden project.
The original native source and separate web project were left in place. The ZIP
contains a separate website design handoff; its tool prompts are reference content,
not instructions to run Stitch or publish a website.

For this iteration the android-cli skill guided environment setup and emulator
interaction; android-permissions-security guided the manifest and permission audit.
The existing native design and font assets were retained, with Jira blue alignment.
Earlier design research belongs to the earlier native project's history.

The design-system/mobile-app profile applies: retain Student Garden's established colors,
choose native navigation and a student agenda, and avoid generic marketing or dashboard
patterns. The HTML scanner cannot evaluate Kotlin Compose; visual and accessibility review
must cover the native UI.

F-Droid inclusion requires a publicly accessible source repository, a free license, free
build dependencies, functional declared features, and a distinct package ID. Native Android
features and enhancements matter when wrapping a site. This version is designed to work
without a hosted website or proprietary service.

Primary references:
- https://f-droid.org/en/docs/Inclusion_Policy/
- https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://github.com/android/skills
- https://developer.android.com/develop/ui/compose/layouts/adaptive
- https://developer.android.com/develop/ui/compose/performance
- https://developer.android.com/training/permissions/requesting
- https://developer.android.com/training/data-storage/shared/photopicker

Implementation dependencies (all retrieved from Google Maven or Maven Central):
- Android Gradle Plugin 8.13.2; Gradle 8.13; Kotlin 2.3.21.
- AndroidX Compose BOM 2026.03.00; Navigation 3 1.0.1; Lifecycle 2.10.0.
- Coil 3.3.0; Compose Rich Editor 1.0.0-rc14 (Apache-2.0, as declared in its POM).
- Kotlinx Coroutines 1.10.2; Kotlinx Serialization 1.9.0.
- Source Sans 3, Source Serif 4 and Noto Sans Arabic from Google Fonts, with bundled OFL files.

Additional primary references:
- https://developer.android.com/develop/ui/compose/text/fonts
- https://github.com/MohamedRejeb/Compose-Rich-Editor
- https://coil-kt.github.io/coil/
- https://developer.android.com/build/releases/agp-8-13-0-release-notes
- https://f-droid.org/en/docs/Build_Metadata_Reference/

The 0.5 note editor retains Compose Rich Editor 1.0.0-rc14 for native text spans.
Its pinned [RichTextState source](https://github.com/MohamedRejeb/compose-rich-editor/blob/v1.0.0-rc14/richeditor-compose/src/commonMain/kotlin/com/mohamedrejeb/richeditor/model/RichTextState.kt)
was checked for selection, formatting and HTML operations. Native table blocks
retain their HTML in the existing note body; no online editor service is used.

The 0.6 reminder update follows the official [alarm scheduling guidance](https://developer.android.com/develop/background-work/services/alarms) and [notification permission guidance](https://developer.android.com/develop/ui/views/notifications/notification-permission). Exact-alarm access is optional, and inexact alarms remain available without it. Static OFL font derivatives are generated locally with fontTools; the build uses the committed font files and needs no font service.
