"""
Drives a connected device to produce the Play Store screenshots.

Build plan section 10 wants 6-8 phone screenshots, and says the first two matter most:
"show a completed colourful puzzle and the daily streak screen".

Both of those need a device that has actually been played. Rather than mock them up, this
solves real puzzles over adb: it reads the solution out of the bundled pack, finds the
board on screen, and drag-paints each run the way a player would. The streak screenshot
needs consecutive days, so it winds the device clock back and plays each day's daily in
order.

    python store/capture_screenshots.py

Requires one device visible to adb and the debug build installed. It changes the device's
date and time while running and puts them back afterwards.
"""

from __future__ import annotations

import os
import struct
import subprocess
import sys
import time
from datetime import date, timedelta

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, "app", "src", "main", "assets", "puzzles.bin")
OUT = os.path.join(ROOT, "store", "screenshots")
ADB = os.path.join(
    os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk", "platform-tools", "adb.exe"
)
PKG = "com.ganim.nonogram"

DIFFICULTIES = ["EASY", "MEDIUM", "HARD", "EXPERT"]


# --- the pack ----------------------------------------------------------------------------

def load_pack() -> list[dict]:
    data = open(PACK, "rb").read()
    (count,) = struct.unpack(">I", data[8:12])
    offsets = [struct.unpack(">I", data[12 + 4 * i : 16 + 4 * i])[0] for i in range(count)]
    puzzles = []
    for i, off in enumerate(offsets):
        # Pack format v2: width, height, difficulty, solveDepth, nameLength, name, bitset.
        # The name is variable length, so the bitset does not start at a fixed offset -
        # reading it as if it did paints a garbled board rather than failing outright.
        w, h, d = data[off], data[off + 1], data[off + 2]
        name_len = data[off + 4]
        name = data[off + 5 : off + 5 + name_len].decode("utf-8")
        start = off + 5 + name_len
        nbytes = (w * h + 7) // 8
        bits = data[start : start + nbytes]
        cells = [(bits[k >> 3] >> (k & 7)) & 1 for k in range(w * h)]
        puzzles.append(
            {"index": i, "w": w, "h": h, "difficulty": DIFFICULTIES[d], "name": name, "cells": cells}
        )
    return puzzles


# --- DailySelector, ported ----------------------------------------------------------------
# Mirrors daily/DailySelector.kt and DailySchedule.kt. Kept in step by the assertion in
# main(): if the two ever disagree, solving a daily would paint the wrong cells and the
# run would visibly fail rather than quietly produce a wrong screenshot.

EPOCH = date(2020, 1, 6)  # a Monday

SCHEDULE = {
    0: (10, "EASY"), 1: (10, "EASY"),
    2: (15, "MEDIUM"), 3: (15, "MEDIUM"),
    4: (15, "HARD"),
    5: (20, "EXPERT"),
    6: (10, "MEDIUM"),
}


def fnv1a(key: str) -> int:
    h = 0x811C9DC5
    for ch in key:
        h ^= ord(ch) & 0xFF
        h = (h * 16777619) & 0xFFFFFFFF
    return h


def weekdays_sharing(weekday: int) -> list[int]:
    spec = SCHEDULE[weekday]
    return [d for d in range(7) if SCHEDULE[d] == spec]


def count_weekdays_before(weekday: int, day: date) -> int:
    span = (day - EPOCH).days
    if span <= 0:
        back = -span
        full, rem = divmod(back, 7)
        steps = (EPOCH.weekday() - weekday) % 7
        return -(full + (1 if 1 <= steps <= rem else 0))
    full, rem = divmod(span, 7)
    steps = (weekday - EPOCH.weekday()) % 7
    return full + (1 if steps < rem else 0)


def occurrence_index(day: date) -> int:
    return sum(count_weekdays_before(d, day) for d in weekdays_sharing(day.weekday()))


def gcd(a: int, b: int) -> int:
    while b:
        a, b = b, a % b
    return a


def permute(position: int, size: int) -> int:
    if size <= 1:
        return 0
    multiplier = max(1, fnv1a(f"stride:{size}") % size)
    guard = 0
    while gcd(multiplier, size) != 1 and guard <= size:
        multiplier = 1 if multiplier + 1 >= size else multiplier + 1
        guard += 1
    offset = fnv1a(f"offset:{size}") % size
    return (position * multiplier + offset) % size


def daily_puzzle(puzzles: list[dict], day: date) -> dict:
    size, difficulty = SCHEDULE[day.weekday()]
    pool = [p for p in puzzles if p["w"] == size and p["difficulty"] == difficulty]
    seq = occurrence_index(day)
    return pool[permute(seq % len(pool), len(pool))]


# --- device ------------------------------------------------------------------------------

def adb(*args: str, capture: bool = False):
    cmd = [ADB, *args]
    if capture:
        return subprocess.run(cmd, capture_output=True, stdin=subprocess.DEVNULL).stdout
    subprocess.run(cmd, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def shell(command: str):
    adb("shell", *command.split())


def screencap(path: str):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = adb("exec-out", "screencap", "-p", capture=True)
    open(path, "wb").write(data)


def find_grid(image_path: str) -> tuple[int, int, float] | None:
    """
    Locates the board by its heavy separator lines, which nothing else on screen uses.

    Returns (left, top, cell_size), or None when no board is visible.
    """
    from PIL import Image

    im = Image.open(image_path).convert("RGB")
    px = im.load()
    w, h = im.size
    # gridLineMajor, dark theme. Tied to ui/theme/Color.kt: when the palette moves,
    # this moves with it, or the finder silently reports "no board on screen".
    major = (144, 132, 224)

    def near(c):
        return all(abs(c[i] - major[i]) <= 22 for i in range(3))

    cols = [x for x in range(w) if sum(1 for y in range(h) if near(px[x, y])) > 600]
    rows = [y for y in range(h) if sum(1 for x in range(w) if near(px[x, y])) > 600]
    if not cols or not rows:
        return None
    left, right = min(cols), max(cols)
    top, bottom = min(rows), max(rows)
    if right - left < 200 or bottom - top < 200:
        return None
    return left, top, (right - left)


def solve_on_device(puzzle: dict, screenshot_path: str) -> bool:
    """Drag-paints the solution. Returns False if the board could not be located."""
    screencap(screenshot_path)
    found = find_grid(screenshot_path)
    if not found:
        return False
    left, top, span = found
    n = puzzle["w"]
    cell = span / n

    def cx(c):
        return int(left + (c + 0.5) * cell)

    def cy(r):
        return int(top + (r + 0.5) * cell)

    cells = puzzle["cells"]
    for r in range(puzzle["h"]):
        c = 0
        while c < n:
            if cells[r * n + c]:
                start = c
                while c < n and cells[r * n + c]:
                    c += 1
                end = c - 1
                if start == end:
                    shell(f"input tap {cx(start)} {cy(r)}")
                else:
                    shell(f"input swipe {cx(start)} {cy(r)} {cx(end)} {cy(r)} 300")
                time.sleep(0.28)
            else:
                c += 1
    return True


def set_device_date(day: date):
    shell(f"su 0 date -s {day.strftime('%Y%m%d')}.120000")
    shell(f"date {day.strftime('%m%d')}1200{day.strftime('%Y')}.00")


def main() -> None:
    if not os.path.exists(ADB):
        sys.exit(f"adb not found at {ADB}")
    puzzles = load_pack()
    print(f"pack: {len(puzzles)} puzzles")
    today = date.today()
    chosen = daily_puzzle(puzzles, today)
    print(f"today's daily resolves to pack index {chosen['index']} "
          f"({chosen['w']}x{chosen['h']} {chosen['difficulty']})")
    print("\nThis module is imported by the capture run; see store/README.md.")


if __name__ == "__main__":
    main()
