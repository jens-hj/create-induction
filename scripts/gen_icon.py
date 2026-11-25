#!/usr/bin/env python3
"""Draws the mod icon: a flat-shaded coil and bar magnet as a white-edged sticker on a round badge."""
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
ICON = ROOT / "src/main/resources/assets/create_induction/icon.png"
DOCS_ICON = ROOT / "docs/icon.png"
SIZE = 2048  # drawn large, scaled down for smooth edges

# (top, left, right) shades per material
ANDESITE = ((168, 179, 171), (112, 117, 112), (80, 83, 81))
IRON = ((214, 214, 214), (141, 139, 139), (89, 88, 88))
COPPER = ((240, 150, 118), (194, 107, 76), (144, 73, 49))
COPPER_DARK = ((206, 116, 86), (154, 80, 56), (110, 53, 36))
RED = ((226, 98, 95), (184, 56, 62), (128, 34, 46))
BLUE = ((95, 139, 208), (55, 87, 160), (38, 50, 100))


def project(x, y, z, unit):
    return ((x - z) * unit * 0.866, (x + z) * unit * 0.5 - y * unit)


def box(draw, origin, unit, start, end, shades):
    (x0, y0, z0), (x1, y1, z1) = start, end

    def point(x, y, z):
        px, py = project(x, y, z, unit)
        return (origin[0] + px, origin[1] + py)

    draw.polygon([point(x0, y1, z0), point(x1, y1, z0), point(x1, y1, z1), point(x0, y1, z1)], fill=shades[0])
    draw.polygon([point(x0, y0, z1), point(x1, y0, z1), point(x1, y1, z1), point(x0, y1, z1)], fill=shades[1])
    draw.polygon([point(x1, y0, z0), point(x1, y0, z1), point(x1, y1, z1), point(x1, y1, z0)], fill=shades[2])


def artwork():
    art = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(art)
    unit = SIZE / 66
    origin = (SIZE * 0.5, SIZE * 0.69)
    # Coil, centred on the origin: base plate, winding in bands, flange, pole piece.
    box(draw, origin, unit, (-8, 0, -8), (8, 3, 8), ANDESITE)
    for i in range(5):
        shades = COPPER if i % 2 == 0 else COPPER_DARK
        box(draw, origin, unit, (-5.5, 3 + i * 1.8, -5.5), (5.5, 3 + (i + 1) * 1.8, 5.5), shades)
    box(draw, origin, unit, (-7, 12, -7), (7, 14, 7), ANDESITE)
    box(draw, origin, unit, (-3, 14, -3), (3, 16, 3), IRON)
    # Bar magnet floating above it, north up.
    box(draw, origin, unit, (-3.5, 19, -3.5), (3.5, 24, 3.5), BLUE)
    box(draw, origin, unit, (-4.5, 24, -4.5), (4.5, 26, 4.5), IRON)
    box(draw, origin, unit, (-3.5, 26, -3.5), (3.5, 31, 3.5), RED)
    return art.rotate(-14, resample=Image.BICUBIC, center=(SIZE / 2, SIZE / 2))


def grow(mask, radius):
    return mask.filter(ImageFilter.GaussianBlur(radius / 2)).point(lambda v: 255 if v > 24 else 0).filter(ImageFilter.GaussianBlur(3))


def badge():
    centre = SIZE / 2
    radius = SIZE * 0.47
    ring = SIZE * 0.035
    # Warm vertical gradient with a faint grid, like a blueprint in copper.
    top, bottom = (255, 190, 112), (206, 96, 58)
    gradient = Image.new("RGBA", (SIZE, SIZE))
    pixels = ImageDraw.Draw(gradient)
    for y in range(SIZE):
        t = y / SIZE
        pixels.line([(0, y), (SIZE, y)], fill=tuple(int(a + (b - a) * t) for a, b in zip(top, bottom)) + (255,))
    grid = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    lines = ImageDraw.Draw(grid)
    step = SIZE / 6.5
    for i in range(-2, 9):
        offset = i * step + step * 0.3
        lines.line([(offset, 0), (offset + SIZE * 0.12, SIZE)], fill=(255, 255, 255, 46), width=int(SIZE * 0.008))
        lines.line([(0, offset), (SIZE, offset - SIZE * 0.12)], fill=(255, 255, 255, 46), width=int(SIZE * 0.008))
    gradient.alpha_composite(grid)
    # Soft light from the top left.
    glow = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(glow).ellipse([SIZE * 0.05, SIZE * 0.02, SIZE * 0.7, SIZE * 0.6], fill=70)
    glow = glow.filter(ImageFilter.GaussianBlur(SIZE * 0.1))
    gradient.alpha_composite(Image.merge("RGBA", [Image.new("L", (SIZE, SIZE), 255)] * 3 + [glow]))

    disc = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(disc).ellipse([centre - radius + ring, centre - radius + ring, centre + radius - ring, centre + radius - ring], fill=255)
    out = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    ImageDraw.Draw(out).ellipse([centre - radius, centre - radius, centre + radius, centre + radius], fill=(255, 255, 255, 255))
    out.paste(gradient, (0, 0), disc)

    art = artwork()
    alpha = art.getchannel("A")
    sticker = grow(alpha, SIZE * 0.035)
    shadow = ImageChops.offset(grow(alpha, SIZE * 0.04), int(SIZE * 0.012), int(SIZE * 0.022)).filter(ImageFilter.GaussianBlur(SIZE * 0.012))
    out.alpha_composite(Image.merge("RGBA", [Image.new("L", (SIZE, SIZE), v) for v in (92, 40, 24)] + [shadow.point(lambda v: v * 0.45)]))
    out.alpha_composite(Image.merge("RGBA", [Image.new("L", (SIZE, SIZE), 255)] * 3 + [sticker]))
    out.alpha_composite(art)
    return out


def main():
    image = badge()
    image.resize((256, 256), Image.LANCZOS).save(ICON)
    image.resize((512, 512), Image.LANCZOS).save(DOCS_ICON)
    print("wrote the icon")


if __name__ == "__main__":
    main()
