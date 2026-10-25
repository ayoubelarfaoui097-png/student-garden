#!/usr/bin/env python3
"""Regenerate Student Garden SVG and Android vector sources (standard library only)."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BRANDING = ROOT / 'branding'
DRAWABLE = ROOT / 'app/src/main/res/drawable'
BLUE = '#0052CC'
# One common 108-unit drawing. All foreground geometry stays within the central
# 66-unit adaptive-icon safe circle. Paths have no strokes or raster effects.
PARTS = [
    ('stem', '#E4F8EC', 'M49,78 C51,71 51,65 51,60 C50,56 46,52 42,49 L44,46 C48,49 51,52 53,55 C53,47 54,40 57,35 L60,36 C57,45 56,55 56,62 C60,57 65,53 70,51 L72,54 C63,58 57,64 56,70 L57,78 Z'),
    ('left-leaf', '#A0EDBA', 'M52,58 C38,59 29,50 29,38 C43,37 54,44 52,58 Z'),
    ('right-leaf', '#3FD7AA', 'M55,67 C55,53 65,44 79,45 C78,58 70,68 55,67 Z'),
    ('new-leaf', '#C5F5CF', 'M54,48 C44,42 46,32 58,27 C63,36 63,44 54,48 Z'),
]


def vector(name, size, *, background=False, monochrome=False, notification=False):
    viewport = 64 if notification else 108
    lines = [f'<vector xmlns:android="http://schemas.android.com/apk/res/android"',
             f'    android:width="{size}dp" android:height="{size}dp"',
             f'    android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">']
    if background:
        lines += [f'    <path android:fillColor="{BLUE}"',
                  '        android:pathData="M20,4 H88 Q104,4 104,20 V88 Q104,104 88,104 H20 Q4,104 4,88 V20 Q4,4 20,4 Z"/>']
    if notification:
        lines += ['    <group android:translateX="-22" android:translateY="-20">']
    indent = '        ' if notification else '    '
    for id_, color, path in PARTS:
        lines += [f'{indent}<path android:fillColor="{("#FFFFFF" if monochrome else color)}"',
                  f'{indent}    android:pathData="{path}"/>']
    if notification:
        lines += ['    </group>']
    lines += ['</vector>', '']
    (DRAWABLE / name).write_text('\n'.join(lines))


def svg(name, size, *, background=False, monochrome=False, mark=False, rounded=False):
    viewbox = '22 22 64 64' if mark else '0 0 108 108'
    lines = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="{viewbox}" role="img" aria-labelledby="title desc">',
             '  <title id="title">Student Garden — growing sapling</title>',
             '  <desc id="desc">A young tree with a small curved trunk and three upward-growing leaves.</desc>']
    if background:
        radius = ' rx="23"' if rounded else ''
        lines += [f'  <rect id="jira-blue-background" width="108" height="108"{radius} fill="{BLUE}"/>']
    for id_, color, path in PARTS:
        if monochrome:
            color = '#FFFFFF'
        elif mark:
            color = {'stem': '#17694F', 'left-leaf': '#158C61', 'right-leaf': '#0D9E79', 'new-leaf': '#43B47C'}[id_]
        lines += [f'  <path id="{id_}" fill="{color}" d="{path}"/>']
    lines += ['</svg>', '']
    (BRANDING / name).write_text('\n'.join(lines))


vector('ic_garden.xml', 48, background=True)
vector('ic_garden_foreground.xml', 108)
vector('ic_garden_monochrome.xml', 108, monochrome=True)
vector('ic_notification.xml', 24, monochrome=True, notification=True)
svg('student-garden-icon.svg', 1024, background=True)
svg('student-garden-icon-rounded.svg', 1024, background=True, rounded=True)
svg('student-garden-mark.svg', 512, mark=True)
svg('student-garden-monochrome.svg', 512, monochrome=True, mark=True)
print('Generated four Android vectors and four editable SVG assets.')
