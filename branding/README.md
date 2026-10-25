# Student Garden sapling

An editable, original vector mark: a young curved trunk, two open leaves and one
new upward leaf. It represents learning that grows a little at a time. Flat fills
and a clear silhouette keep it legible at launcher and notification sizes.

## Assets

- `student-garden-icon.svg` / `.png`: 1024 × 1024 square icon, Jira blue background.
- `student-garden-icon-rounded.svg` / `.png`: 1024 × 1024 presentation preview.
- `student-garden-icon-preview.png`: 256 × 256 compact presentation preview.
- `student-garden-mark.svg` / `.png`: 512 × 512 transparent mark, darker green
  colors suitable for white surfaces.
- `student-garden-monochrome.svg` / `.png`: 512 × 512 white silhouette with a
  transparent background, intended for dark surfaces.

SVG paths remain editable in Inkscape or any SVG editor. There is no text, raster
content, external reference, gradient, or downloaded artwork. These assets use the
project's Apache-2.0 license.

The Android resources live in `app/src/main/res/drawable/`:

- `ic_garden.xml`: blue tile for app UI.
- `ic_garden_foreground.xml`: transparent adaptive icon foreground.
- `ic_garden_monochrome.xml`: Android 13+ themed icon silhouette.
- `ic_notification.xml`: white notification silhouette with tighter framing.

The existing adaptive launchers already reference the updated foreground and
monochrome resources. The 108-unit foreground fits inside the central 66-unit
safe circle, so the sapling survives round and other launcher masks. Use the
square export for store listings; Android supplies its own launcher mask.

## Colors

| Role | Hex |
| --- | --- |
| Jira blue | `#0052CC` |
| Trunk | `#E4F8EC` |
| Left leaf | `#A0EDBA` |
| Right leaf | `#3FD7AA` |
| New leaf | `#C5F5CF` |

Run `python3 branding/generate_assets.py` from the repository root to regenerate
the Android XML and SVG sources from one drawing. Export the SVGs to PNG using
Inkscape. The checked-in PNGs were exported by Inkscape 1.4.4.
