# UI direction and skill research for 0.10

The user's NewPipe screenshot is the visual reference: horizontal playlist rows,
wide thumbnails, a dark playlist-count strip, readable titles and quiet uploader
text. Student Garden keeps its own blue, white and feature accents, bundled fonts,
light/dark themes and native navigation. Result rows use breathing space rather
than divider lines. Repeated introductions, filter summaries and playback policy
paragraphs have been removed from the video flow.

## Skills selected

There is no universal best UI skill. For this existing native Android app, the useful
combination is deliberate design, consistency with the current app, Android platform
knowledge and actual screen verification:

- [Anthropic frontend-design](https://github.com/anthropics/skills/blob/main/skills/frontend-design/SKILL.md)
  emphasizes a deliberate visual direction and production implementation. The
  locally available frontend-design skill was used to keep a concrete token/layout
  plan rather than introduce an unrelated visual template.
- The locally installed avoid-ai-design skill was used for a surgical review inside
  the existing design system. Its JavaScript scanner does not parse Kotlin and
  scanned zero files; that result is not claimed as native UI validation. Native
  screenshots and workflow tests provide the useful evidence here.
- The local android-cli and edge-to-edge guidance provides the native development,
  inspection and inset workflows. Web-only component kits are not a replacement
  for Android controls, accessible touch targets, state restoration or recording
  lifecycle handling.

## Platform decisions

- [Android notification design](https://developer.android.com/design/ui/mobile/guides/home-screen/notifications)
  calls for timely, relevant notifications and supports an app accent and useful
  large artwork. Category-colored artwork accompanies the app's monochrome status
  icon. Routine study reminders do not use a fully colored notification background.
- [Media3 surface guidance](https://developer.android.com/media/media3/ui/surface)
  favors SurfaceView where suitable. The app retains that player surface.
  [PixelCopy](https://developer.android.com/reference/android/view/PixelCopy)
  copies the decoded video surface for frame notes; app controls and navigation
  are excluded. This is a local image capture, with a matching video timestamp.

No new remote skills or UI dependencies were installed. The selected local skills
and Android documentation were sufficient for this app. Implementation evidence,
checks and limitations are recorded in verification-0.10.0.md.

## Audit and final visual review

The native review found repeated explanatory copy (CP1/CP5, source and pixels) and
result separators/density that did not match the supplied list reference (S1,
rendered). These were addressed with shorter controls, thumbnail rows and spacing.
No unrelated palette or font change was introduced. The single design contract
continues across Video, Study, editors and reminder settings (X1 consistency).
The final native captures show the playlist count strip, timestamped media notes
and Android notification artwork. The web scanner still provides no Kotlin
coverage; visual review and workflow tests are the basis for this assessment.
