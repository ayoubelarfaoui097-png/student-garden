#!/usr/bin/env python3
"""Generate Student Garden's original three-note completion bell (Apache-2.0)."""
import math
import struct
import wave
from pathlib import Path

output = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw/focus_bell.wav'
with wave.open(str(output), 'wb') as sound:
    sound.setnchannels(1)
    sound.setsampwidth(2)
    sound.setframerate(22050)
    for sample in range(22050 * 3):
        time = sample / 22050
        value = sum(
            math.sin(2 * math.pi * frequency * time) * math.exp(-3 * (time - start))
            if time >= start else 0
            for frequency, start in [(660, 0), (880, .4), (1100, .8)]
        ) * .22
        sound.writeframesraw(struct.pack('<h', int(max(-1, min(1, value)) * 32767)))
print(output)
