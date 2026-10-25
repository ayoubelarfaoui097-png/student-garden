#!/usr/bin/env python3
"""Generate reproducible static weights from bundled OFL variable fonts (fontTools)."""
from pathlib import Path
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
root = Path(__file__).resolve().parents[1]
for family, label in [('source_sans', 'Garden Sans'), ('noto_arabic', 'Garden Arabic')]:
    for weight, style in [(400, 'regular'), (500, 'medium'), (600, 'semibold'), (700, 'bold')]:
        font = instantiateVariableFont(TTFont(root / f'app/src/main/res/font/{family}.ttf'), {'wght': weight}, inplace=True)
        if 'fvar' in font: font = instantiateVariableFont(font, {a.axisTag: a.defaultValue for a in font['fvar'].axes}, inplace=True)
        font['OS/2'].fsSelection = (font['OS/2'].fsSelection & ~0x60) | (0x20 if weight >= 700 else 0x40)
        font['head'].macStyle = (font['head'].macStyle & ~1) | (1 if weight >= 700 else 0)
        for record in font['name'].names:
            text = {1: label, 2: style.title(), 3: f'{label}-{style}', 4: f'{label} {style.title()}', 6: f'{label.replace(" ", "")}-{style.title()}', 16: label, 17: style.title()}.get(record.nameID)
            if text: record.string = text.encode(record.getEncoding(), errors='replace')
        font.save(root / f'app/src/main/res/font/{family}_{style}.ttf')
