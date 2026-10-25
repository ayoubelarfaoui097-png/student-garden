# Video notebooks, 0.12.0

25 October 2026. Local implementation in the existing native Android app.

Askify’s [product](https://askify.video/) and [help](https://askify.tawk.help/category/how-to-use) informed the continuous sequence of timestamps, text and captures. Student Garden retains its existing blue, gray, white, Source Sans 3 and Noto Arabic identity. The notebook uses one reading surface, without a formatting toolbar, microphone actions, decorative cards or explanatory marketing text. Study continues to offer its existing richer note editor.

Applied the installed frontend-design and avoid-ai-design skills for deliberate hierarchy and concise copy, and Android CLI/adaptive guidance for native verification and landscape behavior. Android CLI skill discovery did not identify another relevant general mobile-design skill worth installing. The avoid-ai-design source scanner only supports web files: its zero-file scan is not evidence about Compose design quality. Native screenshots are the visual evidence.

The fullscreen capture control shares Media3’s controller visibility. A tap reveals it alongside playback controls; normal watching hides it. PixelCopy reads the video surface, excluding app controls. Capturing preserves playing or paused state. See [official Media3 customization](https://developer.android.com/media/media3/ui/customization).

Frames keep an unobstructed image: transparent timestamp and remove controls use white foregrounds with shadows for contrast. Each timestamp opens its source position. Text is edited inline. Removal offers Undo. Existing rich HTML and recordings remain compatible with the portable note model; new video entries are text or frames.

Exports use Android PdfDocument and standard OOXML containers for DOCX/PPTX, without a server. Exported content is text and images; recorded audio is not embedded. Study HTML formatting is flattened to readable text in these exports. Files are selected with Android’s document picker.

## Playlist failure

The previous optimized APK reproduced a generic network error for a public playlist that the identical extractor could load on the JVM. Its R8 mapping showed stripped protobuf continuation fields. The fix follows the [protobuf Java Lite guidance](https://github.com/protocolbuffers/protobuf/blob/main/java/lite.md) and [NewPipe’s own shrinker rules](https://github.com/TeamNewPipe/NewPipe/blob/dev/app/proguard-rules.pro): preserve GeneratedMessageLite fields and reflection-loaded timeago patterns. Pagination requests also follow the current playlist lifecycle and avoid duplicate items. Private, removed or remote-service failures still require an appropriate error state.
