#!/usr/bin/env python3
"""Draws the mod's block textures and icon. Original pixel art, no third-party assets."""
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
BLOCK = ROOT / "src/main/resources/assets/create_induction/textures/block"
ICON = ROOT / "src/main/resources/assets/create_induction/icon.png"
DOCS_ICON = ROOT / "docs/icon.png"

STEEL = [(58, 62, 70), (78, 83, 92), (101, 107, 117), (128, 134, 144), (160, 166, 175)]
RED = [(110, 28, 30), (150, 38, 38), (190, 52, 46), (222, 84, 66)]
BLUE = [(26, 44, 104), (36, 64, 146), (50, 92, 190), (84, 132, 224)]
COPPER = [(120, 62, 38), (158, 86, 50), (196, 112, 66), (226, 146, 96), (244, 184, 138)]
WOOD = [(74, 52, 36), (96, 68, 46), (120, 88, 58), (142, 108, 74)]
DARK = (30, 32, 38)


def canvas():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def noise(rng, palette, base, spread=1):
    return palette[max(0, min(len(palette) - 1, base + rng.randint(-spread, spread)))]


def frame(img, palette):
    """Bevelled border: light top/left, dark bottom/right."""
    for i in range(16):
        img.putpixel((i, 0), palette[-1])
        img.putpixel((0, i), palette[-1])
        img.putpixel((i, 15), palette[0])
        img.putpixel((15, i), palette[0])


def magnet_end(palette, seed, letter):
    rng = random.Random(seed)
    img = canvas()
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), noise(rng, palette, 2))
    frame(img, palette)
    for (x, y) in letter:
        img.putpixel((x, y), palette[-1])
        img.putpixel((x + 1, y + 1), palette[0]) if (x + 1, y) not in letter and x + 1 < 15 and y + 1 < 15 else None
    return img


def magnet_side():
    rng = random.Random(3)
    img = canvas()
    for y in range(16):
        palette = RED if y < 7 else BLUE if y > 8 else STEEL
        for x in range(16):
            base = 3 if palette is STEEL else 2
            img.putpixel((x, y), noise(rng, palette, base))
    for x in range(16):
        img.putpixel((x, 6), RED[0])
        img.putpixel((x, 9), BLUE[-1])
        img.putpixel((x, 0), RED[-1])
        img.putpixel((x, 15), BLUE[0])
    for y in range(16):
        img.putpixel((0, y), img.getpixel((1, y)))
        img.putpixel((15, y), tuple(max(0, c - 30) for c in img.getpixel((14, y))[:3]) + (255,))
    return img


def coil_side():
    rng = random.Random(4)
    img = canvas()
    for y in range(16):
        for x in range(16):
            if y < 2 or y > 13:
                img.putpixel((x, y), noise(rng, STEEL, 2))
            else:
                band = (y - 2) % 3
                img.putpixel((x, y), noise(rng, COPPER, [3, 2, 0][band], 0 if band == 2 else 1))
    for x in range(16):
        img.putpixel((x, 0), STEEL[-1])
        img.putpixel((x, 1), STEEL[1])
        img.putpixel((x, 14), STEEL[3])
        img.putpixel((x, 15), STEEL[0])
    for y in range(2, 14):
        img.putpixel((0, y), COPPER[4] if (y - 2) % 3 == 0 else COPPER[2])
        img.putpixel((15, y), COPPER[0])
    return img


def coil_end(seed, core):
    rng = random.Random(seed)
    img = canvas()
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d > 6.5:
                img.putpixel((x, y), noise(rng, STEEL, 2))
            elif d > 3.5:
                ring = int(d - 3.5)
                img.putpixel((x, y), noise(rng, COPPER, [3, 1, 2][ring % 3], 0))
            else:
                img.putpixel((x, y), noise(rng, core, 2))
    frame(img, STEEL)
    for i in range(4, 12):
        img.putpixel((i, 4), core[-1])
        img.putpixel((4, i), core[-1])
        img.putpixel((i, 11), core[0])
        img.putpixel((11, i), core[0])
    return img


def bearing_side():
    rng = random.Random(7)
    img = canvas()
    for y in range(16):
        for x in range(16):
            if y < 4:
                img.putpixel((x, y), noise(rng, STEEL, 3))
            elif y == 4:
                img.putpixel((x, y), DARK)
            elif y > 12:
                img.putpixel((x, y), noise(rng, STEEL, 1))
            else:
                img.putpixel((x, y), noise(rng, WOOD, 2 if x % 5 else 0))
    for x in range(16):
        img.putpixel((x, 0), STEEL[-1])
        img.putpixel((x, 3), STEEL[1])
        img.putpixel((x, 12), STEEL[0])
        img.putpixel((x, 15), STEEL[0])
    for x in (2, 13):
        for y in (7, 10):
            img.putpixel((x, y), STEEL[4])
            img.putpixel((x, y + 1), STEEL[0])
    return img


def bearing_top():
    rng = random.Random(8)
    img = canvas()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 2:
                img.putpixel((x, y), noise(rng, COPPER, 3))
            elif d < 3:
                img.putpixel((x, y), DARK)
            elif d < 6.3:
                img.putpixel((x, y), noise(rng, STEEL, 3))
            elif d < 7.2:
                img.putpixel((x, y), STEEL[0])
            else:
                img.putpixel((x, y), noise(rng, STEEL, 2))
    frame(img, STEEL)
    for (x, y) in ((7, 4), (8, 4), (7, 11), (8, 11), (4, 7), (4, 8), (11, 7), (11, 8)):
        img.putpixel((x, y), RED[2] if y < 8 and x in (7, 8) else BLUE[2] if y > 8 and x in (7, 8) else COPPER[3])
    return img


def bearing_bottom():
    rng = random.Random(9)
    img = canvas()
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            img.putpixel((x, y), DARK if d < 3 else noise(rng, WOOD if d < 6 else STEEL, 1 if d < 6 else 2))
    frame(img, STEEL)
    return img


def bearing_inner():
    rng = random.Random(10)
    img = canvas()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            img.putpixel((x, y), noise(rng, COPPER, 2) if d < 3 else DARK if d < 4 else noise(rng, STEEL, 1))
    frame(img, STEEL)
    return img


def icon():
    """A coil wrapped around a two-pole magnet, 16x16, scaled up."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(1, 15):
        for x in range(5, 11):
            img.putpixel((x, y), RED[2] if y < 8 else BLUE[2])
        img.putpixel((5, y), RED[3] if y < 8 else BLUE[3])
        img.putpixel((10, y), RED[0] if y < 8 else BLUE[0])
    for y in range(4, 12):
        row = COPPER[3] if y % 2 == 0 else COPPER[1]
        for x in range(2, 14):
            img.putpixel((x, y), row)
        img.putpixel((2, y), COPPER[4])
        img.putpixel((13, y), COPPER[0])
    for x in range(1, 15):
        img.putpixel((x, 3), STEEL[3])
        img.putpixel((x, 12), STEEL[1])
    return img


N = {(6, 5), (6, 6), (6, 7), (6, 8), (6, 9), (6, 10), (7, 6), (8, 7), (8, 8), (9, 9), (10, 5), (10, 6), (10, 7), (10, 8), (10, 9), (10, 10)}
S = {(7, 5), (8, 5), (9, 5), (10, 5), (6, 6), (6, 7), (7, 8), (8, 8), (9, 8), (10, 9), (10, 10), (6, 11), (7, 11), (8, 11), (9, 11)}


def main():
    BLOCK.mkdir(parents=True, exist_ok=True)
    DOCS_ICON.parent.mkdir(parents=True, exist_ok=True)
    textures = {
        "permanent_magnet_north": magnet_end(RED, 1, N),
        "permanent_magnet_south": magnet_end(BLUE, 2, S),
        "permanent_magnet_side": magnet_side(),
        "induction_coil_side": coil_side(),
        "induction_coil_front": coil_end(5, STEEL),
        "induction_coil_back": coil_end(6, WOOD),
        "dynamo_bearing_side": bearing_side(),
        "dynamo_bearing_top": bearing_top(),
        "dynamo_bearing_bottom": bearing_bottom(),
        "dynamo_bearing_inner": bearing_inner(),
    }
    for name, image in textures.items():
        image.save(BLOCK / f"{name}.png")
    small = icon()
    small.resize((128, 128), Image.NEAREST).save(ICON)
    small.resize((512, 512), Image.NEAREST).save(DOCS_ICON)
    print(f"wrote {len(textures)} textures and the icon")


if __name__ == "__main__":
    main()
