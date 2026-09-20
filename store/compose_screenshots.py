"""
Turns raw device captures into the Play Store screenshot set.

Build plan section 10: "Screenshots - 6 to 8, phone size. First two matter most: show a
completed colourful puzzle and the daily streak screen. Add short text overlays naming
the benefit."

The captures themselves come from a real device (see store/README.md); this only adds
the caption band. Keeping the two steps apart means the captions can be reworded without
replaying the device, which is the part that takes time.

    python store/compose_screenshots.py
"""

from __future__ import annotations

import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "store", "raw")
OUT = os.path.join(ROOT, "store", "screenshots")

# Mirrors the app's dark palette, so the caption reads as part of the product rather
# than as something bolted on in an image editor.
PAPER_DARK = (18, 21, 23)
INK_DARK = (232, 229, 225)
ACCENT_DARK = (91, 217, 223)

FONT_BOLD = r"C:\Windows\Fonts\arialbd.ttf"
FONT_REG = r"C:\Windows\Fonts\arial.ttf"

# Order matters: Play shows them in this order, and the plan says the first two carry
# the listing. First the payoff (pictures you revealed), then the habit (the streak).
SHOTS = [
    ("25-archive-15-solved.png", "Every puzzle hides a picture", "5,000 of them, solvable by logic alone"),
    ("23-daily-streak.png", "A new puzzle every day", "Keep the streak going"),
    ("31-midgame-greyed.png", "Drag to fill a whole row", "Clues fade out as you finish them"),
    ("33-board20.png", "Four sizes, up to 20\u00d720", "Easy in a minute, expert in twenty"),
    ("29-results-card.png", "No guessing, ever", "Every puzzle is solvable by pure logic"),
    ("34-settings.png", "Plays completely offline", "No account, no sign-in, no waiting"),
]

BAND_HEIGHT = 300


def compose(raw_path: str, headline: str, sub: str) -> Image.Image:
    shot = Image.open(raw_path).convert("RGB")
    w, h = shot.size

    canvas = Image.new("RGB", (w, h + BAND_HEIGHT), PAPER_DARK)
    canvas.paste(shot, (0, BAND_HEIGHT))
    draw = ImageDraw.Draw(canvas)

    headline_font = ImageFont.truetype(FONT_BOLD, 62)
    sub_font = ImageFont.truetype(FONT_REG, 40)

    def centre(text: str, font: ImageFont.FreeTypeFont, y: int, fill: tuple) -> None:
        box = draw.textbbox((0, 0), text, font=font)
        draw.text(((w - (box[2] - box[0])) / 2, y), text, font=font, fill=fill)

    centre(headline, headline_font, 96, INK_DARK)
    centre(sub, sub_font, 186, ACCENT_DARK)
    return canvas


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    made = 0
    for index, (name, headline, sub) in enumerate(SHOTS, start=1):
        source = os.path.join(RAW, name)
        if not os.path.exists(source):
            print(f"  skipped {name} (not in store/raw)")
            continue
        image = compose(source, headline, sub)
        target = os.path.join(OUT, f"{index:02d}-{os.path.splitext(name)[0]}.png")
        image.save(target)
        print(f"  {os.path.relpath(target, ROOT)}  {image.size[0]}x{image.size[1]}")
        made += 1
    print(f"\n{made} screenshots written to store/screenshots/")


if __name__ == "__main__":
    main()
