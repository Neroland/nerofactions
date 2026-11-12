"""Regenerate NeroFactions textures deterministically."""

import argparse
import io
from pathlib import Path

from PIL import Image, ImageDraw


def registry_texture(top=False):
    image = Image.new("RGB", (16, 16), "#283c43")
    draw = ImageDraw.Draw(image)
    for y in range(16):
        for x in range(16):
            value = 45 + (x * 7 + y * 3) % 8
            image.putpixel((x, y), (value, value + 17, value + 21))
    draw.rectangle((0, 0, 15, 15), outline="#142329")
    draw.line((1, 1, 14, 1), fill="#9bb5ad")
    draw.line((1, 1, 1, 14), fill="#77978f")
    draw.line((2, 14, 14, 14), fill="#1b2b31")
    if top:
        draw.rectangle((3, 3, 12, 12), fill="#d2c8a6", outline="#f1e7c6")
        draw.rectangle((5, 5, 10, 6), fill="#506d68")
        for y in (8, 10):
            draw.line((5, y, 10, y), fill="#8b866e")
        draw.rectangle((10, 11, 12, 13), fill="#bf964d")
        draw.point((11, 12), fill="#f5d187")
    else:
        draw.rectangle((3, 4, 12, 10), fill="#10252b", outline="#6c9c92")
        draw.line((5, 6, 10, 6), fill="#cbe9ce")
        draw.line((5, 8, 8, 8), fill="#83bfa5")
        for x in (4, 7, 10):
            draw.rectangle((x, 12, x + 1, 12), fill="#c9a45a")
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        draw.point((x, y), fill="#adc0b0")
    return image


FACTION_MARKS = {
    "free_colonists": ("#89b98c", (
        "....#....", "...###...", "..##.##..", ".##...##.", "##..#..##",
        ".#..#..#.", ".#.....#.", ".###.###.", ".###.###.",
    )),
    "miner_union": ("#d5a44c", (
        ".######..", "##.....#.", "....#...#", "...##....", "..##.....",
        ".##....#.", "##....###", "#....###.", "....###..",
    )),
    "nero_corporation": ("#85a8d6", (
        ".#######.", "##.....##", "##.#...##", "##.##..##", "##.#.#.##",
        "##...#.##", "##.....##", ".#######.", "....#....",
    )),
    "salvagers": ("#c18862", (
        "##.......", ".#.....##", "..#...##.", "...#.##..", "....##...",
        "...##.#..", "..##...#.", "###.....#", ".#......#",
    )),
    "space_guild": ("#77bed2", (
        "....#....", "....#....", "...###...", "...#.#...", "..##.##..",
        ".###.###.", "##.....##", "....#....", "...###...",
    )),
    "terraforming_authority": ("#76b8a2", (
        ".......##", ".##..###.", ".####.#..", "..####...", "....#....",
        "....#....", ".#######.", "..#####..", "...###...",
    )),
    "void_cult": ("#b08cd1", (
        "....#....", "...#.#...", "..#...#..", ".#..#..#.", "#..###..#",
        ".#..#..#.", "..#...#..", "...#.#...", "....#....",
    )),
}


def motif(rows, color):
    image = Image.new("RGBA", (9, 9))
    for y, row in enumerate(rows):
        for x, point in enumerate(row):
            if point == "#":
                image.putpixel((x, y), color)
    return image


def banner_assets(faction, accent, rows):
    white = motif(rows, (255, 255, 255, 255))
    mark = white.resize((18, 18), Image.Resampling.NEAREST)
    banner = Image.new("RGBA", (64, 64))
    banner.paste(mark, (2, 12))
    banner.paste(mark.transpose(Image.Transpose.FLIP_LEFT_RIGHT), (23, 12))
    shield = Image.new("RGBA", (64, 64))
    shield.paste(white, (3, 8))

    card = Image.new("RGBA", (16, 16))
    draw = ImageDraw.Draw(card)
    draw.polygon([(2, 1), (11, 1), (14, 4), (14, 14), (2, 14)], fill="#171d23")
    draw.polygon([(3, 2), (11, 2), (13, 4), (13, 13), (3, 13)], fill="#3f484d")
    draw.line((3, 2, 10, 2), fill="#a5aeaa")
    draw.line((4, 13, 13, 13), fill="#293139")
    draw.line((11, 3, 11, 5, 13, 5), fill="#798481")
    ink = motif(rows, tuple(bytes.fromhex(accent[1:])) + (255,))
    card.alpha_composite(ink, (4, 4))
    yield f"entity/banner/{faction}", banner
    yield f"entity/shield/{faction}", shield
    yield f"item/{faction}_banner_pattern", card


def shade(accent, factor):
    return tuple(min(255, round(channel * factor)) for channel in accent)


def trim_assets(faction, accent, rows):
    color = tuple(bytes.fromhex(accent[1:]))
    palette = Image.new("RGB", (8, 1))
    for x, scale in enumerate((1.55, 1.29, 1.12, 0.96, 0.78, 0.61, 0.43, 0.28)):
        palette.putpixel((x, 0), shade(color, scale))
    yield f"trims/color_palettes/{faction}", palette
    yield f"palettes/trim/{faction}", palette

    primary = Image.new("RGBA", (64, 32))
    leggings = Image.new("RGBA", (64, 32))
    trim_ink = (192, 192, 192, 255)
    mark = motif(rows, trim_ink)
    primary.paste(mark.resize((7, 7), Image.Resampling.NEAREST), (20, 21))
    for im, lines in (
        (primary, [(8, 9, 15, 9), (4, 28, 7, 28), (44, 22, 47, 22), (32, 24, 39, 24)]),
        (leggings, [(0, 22, 15, 22), (5, 24, 6, 28), (13, 24, 14, 28), (20, 23, 27, 23)]),
    ):
        draw = ImageDraw.Draw(im)
        for line in lines:
            draw.line(line, fill=trim_ink)
        for x, y in [(lines[0][0], lines[0][1]), (lines[0][2], lines[0][3])]:
            draw.point((x, y), (128, 128, 128, 255))
    yield f"trims/models/armor/{faction}", primary
    yield f"trims/models/armor/{faction}_leggings", leggings
    yield f"trims/entity/humanoid/{faction}", primary
    yield f"trims/entity/humanoid_leggings/{faction}", leggings

    template = Image.new("RGBA", (16, 16))
    draw = ImageDraw.Draw(template)
    draw.polygon([(2, 3), (5, 1), (11, 1), (14, 4), (14, 12), (11, 14),
                  (4, 14), (2, 11)], fill="#20252e")
    draw.polygon([(3, 4), (5, 2), (11, 2), (13, 4), (13, 11), (11, 13),
                  (4, 13), (3, 11)], fill="#66747a")
    draw.line((6, 2, 10, 2), fill="#c2ced2")
    draw.line((5, 12, 11, 12), fill="#37444c")
    template.alpha_composite(motif(rows, shade(color, 1.4) + (255,)), (4, 3))
    yield f"item/{faction}_armor_trim_smithing_template", template

    material = Image.new("RGBA", (16, 16))
    draw = ImageDraw.Draw(material)
    draw.polygon([(2, 9), (5, 4), (11, 3), (14, 7), (13, 11), (8, 13), (3, 12)],
                 fill=shade(color, 0.35))
    draw.polygon([(3, 9), (5, 5), (10, 4), (13, 7), (12, 10), (7, 12), (4, 11)],
                 fill=shade(color, 0.8))
    draw.polygon([(4, 8), (6, 5), (10, 4), (12, 7), (7, 9)], fill=shade(color, 1.25))
    draw.line((6, 5, 10, 4), fill=shade(color, 1.6))
    draw.line((7, 10, 11, 8), fill=shade(color, 0.54))
    yield f"item/{faction}_trim_material", material


def rift_blade():
    image = Image.new("RGBA", (16, 16))
    draw = ImageDraw.Draw(image)
    draw.polygon([(13, 1), (15, 1), (15, 4), (13, 8), (9, 12), (7, 10),
                  (10, 6)], fill="#251b36")
    draw.polygon([(14, 2), (14, 4), (12, 8), (9, 10), (8, 9), (11, 6)],
                 fill="#7954a1")
    draw.line([(14, 2), (12, 5), (10, 7), (9, 9)], fill="#c7a9e7")
    draw.line([(13, 6), (11, 9), (10, 9)], fill="#593c7b")
    draw.polygon([(5, 7), (7, 7), (11, 11), (11, 13), (9, 13), (5, 9)], fill="#251b36")
    draw.line((6, 8, 10, 12), fill="#ab82ca")
    draw.line((3, 13, 6, 10), fill="#3a3145", width=3)
    draw.line((3, 13, 6, 10), fill="#8d6da0")
    draw.rectangle((1, 13, 3, 15), fill="#261a33", outline="#4b3c5a")
    draw.point((2, 14), fill="#c7a9e7")
    return image


def textures():
    yield "item/rift_blade", rift_blade()
    yield "block/faction_registry_side", registry_texture()
    yield "block/faction_registry_top", registry_texture(top=True)
    for faction, (accent, rows) in FACTION_MARKS.items():
        yield from banner_assets(faction, accent, rows)
        yield from trim_assets(faction, accent, rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    directory = Path(__file__).resolve().parents[1] / "common/src/main/resources/assets/nerofactions/textures"
    mismatches = []
    for name, texture in textures():
        output = io.BytesIO()
        texture.save(output, format="PNG", optimize=False)
        path = directory / f"{name}.png"
        data = output.getvalue()
        if args.check:
            if not path.exists() or path.read_bytes() != data:
                mismatches.append(name)
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    if mismatches:
        parser.exit(1, "Texture drift: " + ", ".join(mismatches) + "\n")


if __name__ == "__main__":
    main()
