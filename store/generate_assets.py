"""
Generates the app icon, the launcher icons and the Play feature graphic.

Build plan section 10 calls the icon "the single highest-leverage asset you will make.
It determines install rate more than the app does. Make it a recognisable nonogram grid
motif, legible at 48px."

Everything here is generated rather than hand-drawn so the whole set can be regenerated
from one palette change, and so the launcher icon and the store icon can never drift
apart. Run from the repository root:

    python store/generate_assets.py

Palette is copied from app/src/main/kotlin/com/ganim/nonogram/ui/theme/Color.kt. If that
changes, change it here too - there is no way to share constants across the boundary.
"""

from __future__ import annotations

import os
from PIL import Image, ImageDraw, ImageFont

# --- palette (mirrors ui/theme/Color.kt) -------------------------------------------------

PAPER = (250, 249, 247)
INK = (22, 25, 28)
ACCENT = (0, 105, 110)
GRID_LINE = (218, 214, 207)
PAPER_DARK = (18, 21, 23)
INK_DARK = (232, 229, 225)
ACCENT_DARK = (91, 217, 223)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STORE = os.path.join(ROOT, "store")
RES = os.path.join(ROOT, "app", "src", "main", "res")

# The motif: a 5x5 grid whose filled cells form a diamond.
#
# Two things this has to do at 48px: read as a *grid*, and have a silhouette.
#
# The first attempt filled a contiguous blob and drew the separators underneath it. At
# any size that collapses into one black shape with notched corners - it says nothing
# about puzzles. So every cell is drawn as its own square with a visible gap, empty ones
# in a pale tint rather than left as background. The lattice is then explicit no matter
# how far it is scaled down.
#
# The diamond is symmetric and unbroken, which is what survives shrinking; a scattered
# fill pattern just turns into noise.
MOTIF = [
    [0, 0, 1, 0, 0],
    [0, 1, 1, 1, 0],
    [1, 1, 1, 1, 1],
    [0, 1, 1, 1, 0],
    [0, 0, 1, 0, 0],
]


def draw_motif(
    draw: ImageDraw.ImageDraw,
    left: float,
    top: float,
    size: float,
    ink: tuple,
    empty: tuple,
    frame: tuple | None,
) -> None:
    """
    Draws the grid motif inside a square of `size` at (`left`, `top`).

    Every cell is drawn, filled or not. Leaving empty cells as bare background is what
    made the first version illegible - the filled cells merged and the grid disappeared.
    """
    n = len(MOTIF)
    cell = size / n
    # Two pixels, not one. The gap is split either side of a cell, so a one-pixel gap
    # leaves half a pixel between neighbours, PIL rounds it away, and adjacent filled
    # cells merge into a blob - which is exactly how the grid stopped reading at 48px.
    gap = max(2.0, cell * 0.12)

    for row in range(n):
        for col in range(n):
            x0 = left + col * cell + gap / 2
            y0 = top + row * cell + gap / 2
            x1 = left + (col + 1) * cell - gap / 2
            y1 = top + (row + 1) * cell - gap / 2
            draw.rectangle([x0, y0, x1, y1], fill=ink if MOTIF[row][col] else empty)

    # Below roughly 80px the frame costs more width than it earns: the cells shrink to
    # mush and the grid is what carries the icon, not the border.
    if frame is not None and size >= 80:
        width = max(2, int(size * 0.05))
        # Sits outside the cells, so it frames the grid rather than clipping it.
        draw.rectangle(
            [left - width, top - width, left + size + width, top + size + width],
            outline=frame,
            width=width,
        )


def rounded_square(size: int, radius_ratio: float, fill: tuple) -> Image.Image:
    """An opaque rounded square, used as the icon's paper."""
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    radius = int(size * radius_ratio)
    draw.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=fill)
    return image


def store_icon(size: int = 512) -> Image.Image:
    """
    The 512x512 Play listing icon.

    Full-bleed and opaque: Play applies its own rounding and shadow, and an icon with its
    own transparent corners ends up double-rounded.
    """
    image = Image.new("RGB", (size, size), PAPER)
    draw = ImageDraw.Draw(image)
    inset = size * 0.15
    draw_motif(draw, inset, inset, size - 2 * inset, INK, GRID_LINE, ACCENT)
    return image


def adaptive_foreground(size: int) -> Image.Image:
    """
    The foreground layer of the adaptive launcher icon.

    Android crops an adaptive icon to whatever mask the launcher uses, and only the
    middle 72 of 108 units is guaranteed visible. The motif is sized to that safe zone,
    so it survives a circle mask as well as a squircle.
    """
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    safe = size * (72 / 108)
    motif = safe * 0.92
    offset = (size - motif) / 2
    draw_motif(draw, offset, offset, motif, INK, GRID_LINE, ACCENT)
    return image


def monochrome_foreground(size: int) -> Image.Image:
    """
    The themed-icon layer for Android 13+.

    Launchers tint this with the user's wallpaper colours, so it must be one solid colour
    against transparency - any palette in here would be thrown away. The empty cells are
    dropped entirely and only the diamond remains, because a tinted lattice of near-equal
    shades reads as a smudge.
    """
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    safe = size * (72 / 108)
    motif = safe * 0.92
    offset = (size - motif) / 2
    n = len(MOTIF)
    cell = motif / n
    gap = max(2.0, cell * 0.12)
    for row in range(n):
        for col in range(n):
            if not MOTIF[row][col]:
                continue
            x0 = offset + col * cell + gap / 2
            y0 = offset + row * cell + gap / 2
            draw.rectangle(
                [x0, y0, offset + (col + 1) * cell - gap / 2, offset + (row + 1) * cell - gap / 2],
                fill=(0, 0, 0, 255),
            )
    return image


def legacy_launcher(size: int, round_icon: bool) -> Image.Image:
    """Pre-API-26 launcher icon, which has to supply its own shape."""
    base = rounded_square(size, 0.5 if round_icon else 0.18, PAPER)
    draw = ImageDraw.Draw(base)
    inset = size * (0.20 if round_icon else 0.12)
    draw_motif(draw, inset, inset, size - 2 * inset, INK, GRID_LINE, ACCENT)
    return base


def feature_graphic(width: int = 1024, height: int = 500) -> Image.Image:
    """
    The 1024x500 Play feature graphic.

    Dark, because it sits above a listing most people read on a phone in dark mode, and
    because the plan's visual direction is calm and high-contrast rather than bright.
    No text: Play overlays the app name and icon on this in several placements, and
    anything written here risks colliding with them.
    """
    image = Image.new("RGB", (width, height), PAPER_DARK)
    draw = ImageDraw.Draw(image)

    # A wide field of grid cells, fading out to the right, with one solved motif sitting
    # on the left - the app's premise in one picture.
    cell = 50
    for row in range(height // cell + 1):
        for col in range(width // cell + 1):
            x0, y0 = col * cell, row * cell
            # Fade the lattice towards the right so it does not fight the icon overlay.
            fade = max(0.0, 1.0 - (x0 / width) * 1.3)
            if fade <= 0.02:
                continue
            shade = tuple(
                int(PAPER_DARK[i] + (44 - PAPER_DARK[i]) * fade) for i in range(3)
            )
            draw.rectangle([x0, y0, x0 + cell, y0 + cell], outline=shade, width=2)

    motif_size = height * 0.62
    draw_motif(
        draw,
        left=width * 0.07,
        top=(height - motif_size) / 2,
        size=motif_size,
        ink=INK_DARK,
        empty=(38, 44, 49),
        frame=ACCENT_DARK,
    )
    return image


def write(image: Image.Image, *path_parts: str) -> None:
    path = os.path.join(*path_parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)
    print(f"  {os.path.relpath(path, ROOT)}  {image.size[0]}x{image.size[1]}")


def main() -> None:
    print("Store assets:")
    write(store_icon(), STORE, "icon-512.png")
    write(feature_graphic(), STORE, "feature-graphic-1024x500.png")

    print("Launcher icons:")
    # Adaptive foreground, at the five standard densities. 108dp is the adaptive canvas.
    for bucket, dp_scale in (
        ("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4),
    ):
        px = int(108 * dp_scale)
        write(adaptive_foreground(px), RES, f"mipmap-{bucket}", "ic_launcher_foreground.png")
        write(monochrome_foreground(px), RES, f"mipmap-{bucket}", "ic_launcher_monochrome.png")

        legacy_px = int(48 * dp_scale)
        write(legacy_launcher(legacy_px, round_icon=False), RES, f"mipmap-{bucket}", "ic_launcher.png")
        write(legacy_launcher(legacy_px, round_icon=True), RES, f"mipmap-{bucket}", "ic_launcher_round.png")

    print("\nDone. Screenshots are captured from a real device by store/capture_screenshots.sh.")


if __name__ == "__main__":
    main()
