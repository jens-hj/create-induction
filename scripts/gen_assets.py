#!/usr/bin/env python3
"""Draws the block textures and writes the block models. Original pixel art in Create's and vanilla's palettes."""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/create_induction"
TEXTURES = ASSETS / "textures/block"
MODELS = ASSETS / "models/block"


def pal(text):
    return [tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,) for h in text.split()]


# Tones run dark to light.
ANDESITE = pal("353b39 404543 505351 60635f 686c68 707570 828784 8f9691 9aa49d a8b3ab")
WOOD = pal("553a1f 5a4424 614b2e 70522e 7a5a34 82613a 886539")
IRON = pal("2b2b2e 353438 3f3e42 494848 595858 676161 757171 8d8b8b 989797 b1b0b0")
COPPER = pal("6e3524 904931 9a5038 a75a40 b26247 c26b4c c87456 d67b5b e3826c")
RED = pal("5e1a24 7a2530 902d36 9c3137 b8434a d2625f")
BLUE = pal("1f2a55 2a3668 334177 3757a0 3b65af 5f8bd0")
BLACK = (22, 22, 24, 255)


def lattice(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 0xFFFF * 2 - 1


def noise(x, y, seed):
    """Blotchy noise in -1..1: soft patches a few pixels wide plus a little grain, like vanilla's stone."""
    cx, cy, fx, fy = x // 3, y // 3, (x % 3) / 3, (y % 3) / 3
    top = lattice(cx, cy, seed) * (1 - fx) + lattice(cx + 1, cy, seed) * fx
    bottom = lattice(cx, cy + 1, seed) * (1 - fx) + lattice(cx + 1, cy + 1, seed) * fx
    return 0.7 * (top * (1 - fy) + bottom * fy) + 0.3 * lattice(x, y, seed + 17)


def tone(palette, index):
    return palette[max(0, min(len(palette) - 1, int(round(index))))]


class Canvas:
    def __init__(self, seed):
        self.image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        self.seed = seed

    def put(self, x, y, colour):
        if 0 <= x < 16 and 0 <= y < 16:
            self.image.putpixel((x, y), colour)

    def fill(self, x0, y0, x1, y1, palette, base, spread=1.0):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, tone(palette, base + noise(x, y, self.seed) * spread))

    def bevel(self, x0, y0, x1, y1, palette, light, dark):
        """Raised edge: lit from the top left."""
        for x in range(x0, x1 + 1):
            self.put(x, y0, tone(palette, light + (noise(x, y0, self.seed + 3) > 0.2)))
            self.put(x, y1, tone(palette, dark - (noise(x, y1, self.seed + 5) > 0.4)))
        for y in range(y0, y1 + 1):
            self.put(x0, y, tone(palette, light - 1 + (noise(x0, y, self.seed + 7) > 0.2)))
            self.put(x1, y, tone(palette, dark))
        self.put(x1, y0, tone(palette, (light + dark) // 2))
        self.put(x0, y1, tone(palette, (light + dark) // 2))

    def plate(self, x0, y0, x1, y1, palette, base, spread=1.0, rise=2, drop=3):
        self.fill(x0, y0, x1, y1, palette, base, spread)
        self.bevel(x0, y0, x1, y1, palette, base + rise, base - drop)

    def recess(self, x0, y0, x1, y1, palette, base):
        """Sunken edge: shadow under the top left rim, light caught on the bottom right."""
        for x in range(x0, x1 + 1):
            self.put(x, y0, tone(palette, base - 3))
            self.put(x, y1, tone(palette, base + 2))
        for y in range(y0, y1 + 1):
            self.put(x0, y, tone(palette, base - 3))
            self.put(x1, y, tone(palette, base + 2))

    def planks(self, x0, y0, x1, y1):
        """Upright boards like the panel of an Andesite Casing."""
        boards = [1, 3, 5, 0, 4, 3, 5, 5, 0, 6, 3, 1, 4, 5, 0, 3]
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                board = boards[x]
                grain = noise(x, y // 2, self.seed + 11)
                index = board if board <= 1 else board + (0.8 if grain > 0.35 else -0.8 if grain < -0.5 else 0)
                if y in (y0, y1) and board > 1:
                    index = min(index, 2) - (y == y0)
                self.put(x, y, tone(WOOD, index))

    def winding(self, x0, y0, x1, y1):
        """Copper wire wrapped around an upright bobbin, seen from the side."""
        width = x1 - x0
        for y in range(y0, y1 + 1):
            turn = (y - y0) % 3
            for x in range(x0, x1 + 1):
                along = (x - x0) / max(1, width)
                index = [7.2, 5.0, 1.6][turn]
                index += 0.9 if along < 0.15 else -1.6 if along > 0.85 else -0.7 if along > 0.65 else 0
                if turn < 2:
                    index += 0.6 * noise(x, y, self.seed + 13)
                self.put(x, y, tone(COPPER, index))

    def bolt(self, x, y, palette, light, dark):
        self.put(x, y, tone(palette, light))
        self.put(x, y + 1, tone(palette, dark))

    def letter(self, rows, x0, y0, palette, dark, light):
        cells = {(x, y) for y, row in enumerate(rows) for x, cell in enumerate(row) if cell == "#"}
        for (x, y) in cells:
            self.put(x0 + x, y0 + y, tone(palette, dark))
            if (x + 1, y + 1) not in cells and (x + 1, y) not in cells:
                self.put(x0 + x + 1, y0 + y, tone(palette, light))


# ---- induction coil: a bobbin on a base plate, drawn as an elevation; the model's parts sample their own area

def coil_side():
    c = Canvas(21)
    c.winding(3, 4, 12, 12)
    for (x0, x1, lit) in ((1, 2, True), (13, 14, False)):  # corner posts
        for y in range(4, 13):
            c.put(x0, y, tone(ANDESITE, 8 if lit else 6))
            c.put(x1, y, tone(ANDESITE, 5 if lit else 3))
    c.plate(1, 2, 14, 3, ANDESITE, 6, 0.6, rise=3, drop=3)  # front flange
    c.fill(5, 0, 10, 1, IRON, 7, 0.6)  # pole piece
    for x in range(5, 11):
        c.put(x, 0, tone(IRON, 9 if x < 9 else 8))
    c.put(10, 0, tone(IRON, 6))
    c.put(10, 1, tone(IRON, 4))
    c.put(5, 1, tone(IRON, 8))
    c.plate(0, 13, 15, 15, ANDESITE, 6, 0.8, rise=3, drop=4)  # base plate
    c.bolt(2, 14, ANDESITE, 9, 2)
    c.bolt(13, 14, ANDESITE, 9, 2)
    return c.image


def coil_front():
    c = Canvas(22)
    c.fill(0, 0, 15, 15, ANDESITE, 4, 0.8)  # base plate seen past the flange
    c.plate(1, 1, 14, 14, ANDESITE, 6, 0.9, rise=3, drop=3)
    for (x, y) in ((2, 2), (13, 2), (2, 12), (13, 12)):
        c.bolt(x, y, ANDESITE, 9, 2)
    c.recess(4, 4, 11, 11, ANDESITE, 5)
    c.plate(5, 5, 10, 10, IRON, 7, 0.7, rise=2, drop=3)
    c.put(6, 6, tone(IRON, 9))
    c.put(7, 6, tone(IRON, 9))
    c.put(6, 7, tone(IRON, 9))
    return c.image


def coil_back():
    c = Canvas(23)
    c.plate(0, 0, 15, 15, ANDESITE, 6, 0.8, rise=3, drop=4)
    c.planks(2, 2, 13, 13)
    c.bevel(1, 1, 14, 14, ANDESITE, 4, 7)  # inner lip of the frame, lit the other way round
    for x0 in (3, 9):  # two contact pads
        c.plate(x0, 6, x0 + 3, 9, COPPER, 5, 0.5, rise=3, drop=4)
        c.put(x0 + 1, 7, tone(IRON, 2))
        c.put(x0 + 2, 7, tone(IRON, 2))
        c.put(x0 + 1, 8, tone(IRON, 1))
        c.put(x0 + 2, 8, tone(IRON, 3))
    return c.image


# ---- permanent magnet: red north half, blue south half, an iron collar and a pole shoe on each end

def magnet_side():
    c = Canvas(31)
    c.fill(1, 1, 14, 5, RED, 3, 0.9)
    c.fill(1, 10, 14, 14, BLUE, 3, 0.9)
    for y in range(1, 15):
        if 6 <= y <= 9:
            continue
        palette = RED if y < 6 else BLUE
        c.put(1, y, tone(palette, 5))
        c.put(2, y, tone(palette, 4))
        c.put(13, y, tone(palette, 1))
        c.put(14, y, tone(palette, 0))
    for x in range(1, 15):
        c.put(x, 1, tone(RED, 5 if x < 12 else 3))
        c.put(x, 5, tone(RED, 0))  # shadow under... the collar's upper edge
        c.put(x, 10, tone(BLUE, 0))
        c.put(x, 14, tone(BLUE, 0 if x > 2 else 1))
    c.plate(0, 6, 15, 9, IRON, 6, 0.7, rise=3, drop=4)  # collar
    for x in (2, 7, 12):
        c.bolt(x, 7, IRON, 9, 1)
    for (y, lit) in ((0, True), (15, False)):  # pole shoes
        for x in range(3, 13):
            c.put(x, y, tone(IRON, (8 if lit else 5) - (x > 10) * 2 + (x < 5)))
    return c.image


def magnet_pole(palette, rows, seed):
    c = Canvas(seed)
    c.fill(0, 0, 15, 15, IRON, 5, 0.6)  # the collar, seen past the body
    c.plate(1, 1, 14, 14, palette, 3, 0.9, rise=2, drop=3)
    c.recess(2, 2, 13, 13, palette, 3)
    c.plate(3, 3, 12, 12, IRON, 7, 0.7, rise=2, drop=3)
    c.letter(rows, 5, 5, IRON, 2, 9)
    return c.image


LETTER_N = ["#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#"]
LETTER_S = [".####", "#....", ".###.", "....#", "....#", "####."]


def magnet_collar():
    c = Canvas(34)
    c.plate(0, 0, 15, 15, IRON, 6, 0.7, rise=3, drop=4)
    return c.image


# ---- dynamo bearing: casing body, a narrow neck, and the turntable that leaves with the rotor

def bearing_side():
    c = Canvas(41)
    c.plate(0, 0, 15, 3, ANDESITE, 6, 0.8, rise=3, drop=4)  # turntable rim
    for x in (2, 7, 8, 13):
        c.put(x, 1, tone(ANDESITE, 3))
        c.put(x, 2, tone(ANDESITE, 9))
    c.fill(4, 4, 11, 5, IRON, 3, 0.6)  # neck
    for x in range(4, 12):
        c.put(x, 4, tone(IRON, 0))
        c.put(x, 5, tone(COPPER, 7 - (x > 8) * 3 - (x == 4)))
    c.put(4, 5, tone(COPPER, 8))
    c.plate(0, 6, 15, 15, ANDESITE, 6, 0.8, rise=3, drop=4)  # casing
    c.planks(2, 8, 13, 13)
    c.bevel(1, 7, 14, 14, ANDESITE, 4, 7)
    c.plate(5, 8, 10, 13, ANDESITE, 5, 0.6, rise=3, drop=3)  # boss, as on a Mechanical Bearing
    c.recess(6, 9, 9, 12, ANDESITE, 5)
    c.fill(7, 10, 8, 11, COPPER, 5, 0.8)
    c.put(7, 10, tone(COPPER, 8))
    c.put(8, 11, tone(COPPER, 2))
    return c.image


def bearing_top():
    c = Canvas(42)
    c.plate(0, 0, 15, 15, ANDESITE, 6, 0.8, rise=3, drop=4)
    c.recess(2, 2, 13, 13, ANDESITE, 5)
    for y in range(3, 13):
        for x in range(3, 13):
            d = max(abs(x - 7.5), abs(y - 7.5))
            lit = ((7.5 - x) + (7.5 - y)) / 9  # towards the top left
            if d > 4:
                c.put(x, y, tone(COPPER, [6.8, 4.6, 1.8][int(d - 0.5) % 3] + lit * 1.6))  # windings, ring on ring
            elif d > 3:
                c.put(x, y, tone(IRON, 1 + (lit < 0) * 5))
            else:
                c.put(x, y, tone(IRON, 6.5 + lit * 2 + 0.6 * noise(x, y, 43)))
    for x in range(3, 13):
        c.put(x, 3, tone(COPPER, 8 if x < 10 else 6))
        c.put(x, 12, tone(COPPER, 1))
    for y in range(4, 12):
        c.put(3, y, tone(COPPER, 7))
        c.put(12, y, tone(COPPER, 1))
    c.put(7, 7, tone(IRON, 9))
    c.put(8, 8, tone(IRON, 3))
    for (x, y) in ((1, 1), (14, 1), (1, 13), (14, 13)):
        c.bolt(x, y, ANDESITE, 9, 2)
    return c.image


def bearing_inner():
    """Top of the body under the turntable, and the turntable's underside."""
    c = Canvas(44)
    c.plate(0, 0, 15, 15, ANDESITE, 4, 0.8, rise=2, drop=2)
    c.recess(3, 3, 12, 12, ANDESITE, 4)
    c.fill(4, 4, 11, 11, IRON, 2, 0.8)
    for y in range(5, 11):
        for x in range(5, 11):
            d = max(abs(x - 7.5), abs(y - 7.5))
            c.put(x, y, tone(COPPER, 5 + ((7.5 - x) + (7.5 - y)) / 3) if d > 2 else tone(IRON, 1))
    return c.image


def bearing_bottom():
    c = Canvas(45)
    c.plate(0, 0, 15, 15, ANDESITE, 6, 0.8, rise=3, drop=4)
    c.planks(2, 2, 13, 13)
    c.bevel(1, 1, 14, 14, ANDESITE, 4, 7)
    c.plate(4, 4, 11, 11, ANDESITE, 4, 0.6, rise=2, drop=2)
    for y in range(5, 11):
        for x in range(5, 11):
            if abs(x - 7.5) + abs(y - 7.5) < 4.5:
                c.put(x, y, BLACK)
    return c.image


# ---- slip ring: casing body, a stack of copper rings and a carbon contact face

def slip_side():
    c = Canvas(51)
    c.plate(0, 8, 15, 15, ANDESITE, 6, 0.8, rise=3, drop=4)  # body
    c.planks(2, 10, 13, 13)
    c.bevel(1, 9, 14, 14, ANDESITE, 4, 7)
    c.plate(6, 10, 9, 13, COPPER, 5, 0.5, rise=3, drop=4)  # wire terminal
    c.put(7, 11, tone(IRON, 1))
    c.put(8, 11, tone(IRON, 2))
    c.put(7, 12, tone(IRON, 2))
    c.put(8, 12, tone(IRON, 4))
    for y in range(3, 8):  # rings, with dark insulation between them
        for x in range(3, 13):
            along = (x - 3) / 9
            shade = 1.2 if along < 0.15 else -2.2 if along > 0.85 else -1.0 if along > 0.65 else 0
            c.put(x, y, tone(IRON, 1 + (shade > 0)) if y in (4, 6) else tone(COPPER, 6.4 + shade + 0.5 * noise(x, y, 52)))
    for x in range(4, 12):  # contact
        for y in range(0, 3):
            c.put(x, y, tone(IRON, (4 if y == 0 else 2) + (x < 6) - (x > 9) * 1.5))
    return c.image


def commutator_side():
    c = Canvas(57)
    c.image.paste(slip_side())
    for y in range(3, 8):  # copper bars along the axis, with dark mica between them
        for x in range(3, 13):
            along = (x - 3) / 9
            shade = 1.2 if along < 0.15 else -2.2 if along > 0.85 else -1.0 if along > 0.65 else 0
            c.put(x, y, tone(IRON, 1) if x in (5, 8, 11) else tone(COPPER, 6.4 + shade + 0.5 * noise(x, y, 58) - (y == 7)))
    return c.image


def slip_face():
    c = Canvas(53)
    c.plate(0, 0, 15, 15, ANDESITE, 6, 0.8, rise=3, drop=4)  # shoulder of the body
    for (x, y) in ((1, 1), (14, 1), (1, 13), (14, 13)):
        c.bolt(x, y, ANDESITE, 9, 2)
    c.plate(3, 3, 12, 12, COPPER, 5, 0.8, rise=3, drop=4)  # top of the ring stack
    c.plate(4, 4, 11, 11, IRON, 3, 0.8, rise=2, drop=2)  # carbon face
    for y in range(5, 11):
        for x in range(5, 11):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if 1 < d < 2:
                c.put(x, y, tone(COPPER, 5 + ((7.5 - x) + (7.5 - y)) / 2.5))
    return c.image


# ---- models: parts sample the elevation drawings through vanilla's default position-based UVs

def element(start, end, **faces):
    names = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}
    out = {}
    for keys, texture in faces.items():
        for key in keys:
            out[names[key]] = {"texture": "#" + texture}
    return {"from": start, "to": end, "faces": out}


def model(textures, elements):
    particle = next(iter(textures.values()))
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": particle, **textures},
        "elements": elements,
    }


def texture(name):
    return f"create_induction:block/{name}"


COIL = model(
    {"side": texture("induction_coil_side"), "front": texture("induction_coil_front"), "back": texture("induction_coil_back")},
    [
        element([0, 0, 0], [16, 3, 16], nsew="side", u="front", d="back"),
        element([3, 3, 3], [13, 12, 13], nsew="side"),
        element([1, 3, 1], [3, 12, 3], nsew="side"),
        element([13, 3, 1], [15, 12, 3], nsew="side"),
        element([1, 3, 13], [3, 12, 15], nsew="side"),
        element([13, 3, 13], [15, 12, 15], nsew="side"),
        element([1, 12, 1], [15, 14, 15], nsew="side", u="front", d="front"),
        element([5, 14, 5], [11, 16, 11], nsew="side", u="front"),
    ])

MAGNET = model(
    {"side": texture("permanent_magnet_side"), "north": texture("permanent_magnet_north"),
     "south": texture("permanent_magnet_south"), "collar": texture("permanent_magnet_collar")},
    [
        element([1, 1, 1], [15, 15, 15], nsew="side", u="north", d="south"),
        element([0, 6, 0], [16, 10, 16], nsew="side", ud="collar"),
        element([3, 15, 3], [13, 16, 13], nsew="side", u="north"),
        element([3, 0, 3], [13, 1, 13], nsew="side", d="south"),
    ])

SLIP_RING = model(
    {"side": texture("slip_ring_side"), "face": texture("slip_ring_face"), "back": texture("dynamo_bearing_bottom")},
    [
        element([0, 0, 0], [16, 8, 16], nsew="side", u="face", d="back"),
        element([3, 8, 3], [13, 13, 13], nsew="side", u="face"),
        element([4, 13, 4], [12, 16, 12], nsew="side", u="face"),
    ])

COMMUTATOR = model(
    {"side": texture("commutator_side"), "face": texture("slip_ring_face"), "back": texture("dynamo_bearing_bottom")},
    [
        element([0, 0, 0], [16, 8, 16], nsew="side", u="face", d="back"),
        element([3, 8, 3], [13, 13, 13], nsew="side", u="face"),
        element([4, 13, 4], [12, 16, 12], nsew="side", u="face"),
    ])

BEARING_TEXTURES = {"side": texture("dynamo_bearing_side"), "top": texture("dynamo_bearing_top"),
                    "inner": texture("dynamo_bearing_inner"), "bottom": texture("dynamo_bearing_bottom")}
BEARING_BODY = [
    element([0, 0, 0], [16, 10, 16], nsew="side", u="inner", d="bottom"),
    element([4, 10, 4], [12, 12, 12], nsew="side"),
]
BEARING_PLATE = [element([0, 12, 0], [16, 16, 16], nsew="side", u="top", d="inner")]


def main():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    MODELS.mkdir(parents=True, exist_ok=True)
    for stale in TEXTURES.glob("*.png"):
        stale.unlink()
    textures = {
        "induction_coil_side": coil_side(),
        "induction_coil_front": coil_front(),
        "induction_coil_back": coil_back(),
        "permanent_magnet_side": magnet_side(),
        "permanent_magnet_north": magnet_pole(RED, LETTER_N, 32),
        "permanent_magnet_south": magnet_pole(BLUE, LETTER_S, 33),
        "permanent_magnet_collar": magnet_collar(),
        "dynamo_bearing_side": bearing_side(),
        "dynamo_bearing_top": bearing_top(),
        "dynamo_bearing_inner": bearing_inner(),
        "dynamo_bearing_bottom": bearing_bottom(),
        "slip_ring_side": slip_side(),
        "slip_ring_face": slip_face(),
        "commutator_side": commutator_side(),
    }
    for name, image in textures.items():
        image.save(TEXTURES / f"{name}.png")
    models = {
        "induction_coil": COIL,
        "permanent_magnet": MAGNET,
        "slip_ring": SLIP_RING,
        "commutator": COMMUTATOR,
        "dynamo_bearing": model(BEARING_TEXTURES, BEARING_BODY + BEARING_PLATE),
        "dynamo_bearing_base": model(BEARING_TEXTURES, BEARING_BODY),
        "dynamo_bearing_plate": model(BEARING_TEXTURES, BEARING_PLATE),
    }
    for name, data in models.items():
        (MODELS / f"{name}.json").write_text(json.dumps(data, indent=2) + "\n")
    print(f"wrote {len(textures)} textures and {len(models)} models")


if __name__ == "__main__":
    main()
