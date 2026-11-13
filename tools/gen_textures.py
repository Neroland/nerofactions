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


def quota_board_texture(top):
    image = Image.new("RGB", (16, 16), "#454039")
    draw = ImageDraw.Draw(image)
    for y in range(16):
        for x in range(16):
            base = 51 + (x * 5 + y * 9) % 10
            image.putpixel((x, y), (base + 15, base + 10, base))
    draw.rectangle((0, 0, 15, 15), outline="#2c2827")
    draw.line((1, 1, 14, 1), fill="#978674")
    draw.line((1, 1, 1, 14), fill="#7c6c60")
    if top:
        draw.rectangle((3, 3, 12, 12), fill="#b89c70", outline="#e8c98c")
        draw.line((4, 5, 11, 5), fill="#71624d")
        draw.line((4, 7, 11, 7), fill="#71624d")
        draw.line((4, 9, 9, 9), fill="#71624d")
        draw.rectangle((9, 10, 12, 12), fill="#796952", outline="#eed49a")
    else:
        draw.rectangle((3, 3, 12, 11), fill="#242324", outline="#776b60")
        for index, height in enumerate((2, 4, 6)):
            x = 5 + index * 2
            draw.line((x, 9, x, 9 - height), fill="#dfa951")
        draw.line((4, 10, 11, 10), fill="#6b5a43")
        draw.line((4, 13, 11, 13), fill="#bc955c")
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        draw.point((x, y), fill="#c3aa81")
    return image


def union_breaker():
    image = Image.new("RGBA", (16, 16))
    draw = ImageDraw.Draw(image)
    draw.line((2, 13, 11, 4), fill="#292821", width=4)
    draw.line((2, 13, 11, 4), fill="#8b6843", width=2)
    draw.line((3, 13, 10, 6), fill="#c19765")
    draw.polygon([(4, 3), (7, 1), (11, 1), (14, 3), (15, 6), (13, 9),
                  (11, 6), (9, 4), (6, 4), (4, 5)], fill="#2a2726")
    draw.polygon([(5, 3), (8, 2), (11, 2), (13, 3), (14, 6), (13, 7),
                  (11, 5), (9, 3), (6, 4)], fill="#bd8a3d")
    draw.line((7, 2, 11, 2, 13, 4), fill="#f4c778")
    draw.point((12, 6), fill="#76624c")
    draw.line((9, 5, 11, 7), fill="#b7aaa0")
    draw.point((2, 13), fill="#6c4c32")
    return image


def union_exosuit_textures():
    armor = Image.new("RGBA", (64, 32))
    legs = Image.new("RGBA", (64, 32))

    def plate(image, bounds, stripe=False):
        x1, y1, x2, y2 = bounds
        draw = ImageDraw.Draw(image)
        draw.rectangle(bounds, fill="#514f47")
        draw.line((x1, y1, x2, y1), fill="#a49d89")
        draw.line((x1, y1, x1, y2), fill="#777367")
        draw.line((x1 + 1, y2, x2, y2), fill="#292927")
        if stripe:
            draw.line((x1 + 1, y1 + 2, x2, y1 + 2), fill="#d7a448")
            draw.line((x1 + 1, y1 + 3, x2, y1 + 3), fill="#84582a")

    for bounds in ((0, 8, 31, 15), (8, 0, 23, 7), (16, 20, 39, 28),
                   (20, 16, 35, 19), (40, 20, 55, 28), (44, 16, 51, 19),
                   (0, 26, 15, 31)):
        plate(armor, bounds, stripe=bounds[1] in (8, 20, 26))
    draw = ImageDraw.Draw(armor)
    draw.rectangle((8, 11, 15, 13), fill="#2c3030")
    draw.line((9, 11, 14, 11), fill="#f4c675")
    draw.rectangle((22, 23, 25, 26), fill="#323333", outline="#cda85b")
    plate(legs, (0, 20, 15, 31), stripe=True)
    plate(legs, (16, 20, 39, 25), stripe=True)
    plate(legs, (4, 16, 11, 19))
    yield "entity/equipment/humanoid/union_exosuit", armor
    yield "entity/equipment/humanoid_leggings/union_exosuit", legs
    yield "models/armor/union_exosuit_layer_1", armor
    yield "models/armor/union_exosuit_layer_2", legs

    shapes = {
        "helmet": [(3, 12), (3, 5), (6, 2), (11, 2), (14, 5), (14, 12), (11, 14), (4, 14)],
        "chestplate": [(1, 6), (4, 3), (6, 2), (6, 5), (10, 5), (10, 2), (12, 3),
                      (15, 6), (13, 10), (12, 9), (12, 14), (4, 14), (4, 9), (3, 10)],
        "leggings": [(3, 2), (13, 2), (13, 14), (9, 14), (9, 8), (7, 8), (7, 14), (3, 14)],
        "boots": [(3, 3), (7, 3), (7, 10), (9, 10), (9, 3), (13, 3), (13, 10),
                  (15, 11), (15, 14), (10, 14), (8, 12), (7, 14), (1, 14), (1, 11), (3, 10)],
    }
    for part, shape in shapes.items():
        image = Image.new("RGBA", (16, 16))
        draw = ImageDraw.Draw(image)
        draw.polygon(shape, fill="#655e52", outline="#2b2926", width=1)
        draw.line((4, 6, 12, 6), fill="#d5a44c")
        draw.line((5, 7, 11, 7), fill="#8a592d")
        draw.line((4, 4, 6, 3), fill="#c6ba9e")
        draw.line((5, 12, 6, 12), fill="#382f26")
        draw.line((10, 12, 11, 12), fill="#382f26")
        if part == "helmet":
            draw.rectangle((5, 8, 12, 10), fill="#303539")
            draw.line((6, 8, 11, 8), fill="#ffd88d")
        yield f"item/union_exosuit_{part}", image


def resident_skin(accent, rows, guard=False, legacy=False):
    image = Image.new("RGBA", (64, 64))
    draw = ImageDraw.Draw(image)
    fabric = "#242d36" if guard else "#3d4d56"
    shadow = "#171e27" if guard else "#29383c"
    skin = "#8e7774" if guard else "#b68769"

    def face(bounds, base, highlight):
        x1, y1, x2, y2 = bounds
        draw.rectangle(bounds, fill=base)
        draw.line((x1, y1, x2, y1), fill=highlight)
        draw.line((x1, y2, x2, y2), fill=shadow)

    for bounds in ((8, 0, 23, 7), (0, 8, 31, 17), (26, 2, 31, 5)):
        face(bounds, skin, "#d1a284")
    draw.line((8, 11, 15, 11), fill="#302626")
    draw.point((10, 12), fill="#dce8df")
    draw.point((13, 12), fill="#dce8df")
    draw.line((11, 16, 13, 16), fill="#5a3d39")
    face((0, 22, 15, 37), shadow, accent)
    face((16, 20, 43, 37), fabric, accent)
    face((0, 38, 27, 63), fabric, accent)
    face((44, 22, 59, 33), fabric, accent)
    face((40, 38, 63, 61), fabric, accent)
    if guard and not legacy:
        face((40, 0, 55, 7), shadow, accent)
        face((32, 8, 63, 19), fabric, accent)
        draw.rectangle((40, 12, 47, 15), fill="#181c22")
        draw.line((41, 13, 46, 13), fill=accent)
    elif not legacy:
        face((30, 47, 63, 63), shadow, accent)
        draw.line((0, 55, 27, 55), fill=accent)
    badge = motif(rows, (243, 237, 223, 255)).resize((5, 5), Image.Resampling.NEAREST)
    image.alpha_composite(badge, (22, 25))
    if not legacy:
        image.alpha_composite(badge, (7, 46))
    else:
        face((40, 16, 55, 31), fabric, accent)
        image.paste(image.crop((40, 16, 56, 32)), (32, 48))
        image.paste(image.crop((0, 16, 16, 32)), (16, 48))
    return image


def textures():
    yield from union_exosuit_textures()
    yield "item/union_breaker", union_breaker()
    yield "block/quota_board_side", quota_board_texture(False)
    yield "block/quota_board_top", quota_board_texture(True)
    yield "item/rift_blade", rift_blade()
    yield "item/guild_grapple", guild_grapple()
    yield "block/navigation_beacon_side", navigation_beacon(False)
    yield "block/navigation_beacon_top", navigation_beacon(True)
    yield "block/faction_registry_side", registry_texture()
    yield "block/faction_registry_top", registry_texture(top=True)
    for faction, (accent, rows) in FACTION_MARKS.items():
        yield from banner_assets(faction, accent, rows)
        yield from trim_assets(faction, accent, rows)
        yield f"entity/outpost/{faction}_envoy", resident_skin(accent, rows)
        yield f"entity/outpost/{faction}_guard", resident_skin(accent, rows, guard=True)
        yield f"entity/outpost/{faction}_guard_legacy", resident_skin(accent, rows, guard=True, legacy=True)


def guild_grapple():
    image = Image.new("RGBA", (16, 16))
    draw = ImageDraw.Draw(image)
    draw.polygon([(1, 13), (1, 10), (6, 6), (8, 6), (10, 4), (10, 2), (13, 1),
                  (15, 3), (15, 6), (12, 9), (9, 9), (6, 13), (4, 15), (2, 15)],
                 fill="#26303c", outline="#141c29")
    draw.line((2, 12, 7, 7, 10, 7, 13, 4, 13, 2), fill="#d0e0e7", width=2)
    draw.line((3, 13, 7, 9, 11, 9, 14, 6), fill="#6c8aab")
    draw.line((5, 10, 8, 10, 8, 13, 5, 13, 5, 11), fill="#3b6684")
    draw.point((7, 11), fill="#92e3ed")
    return image


def navigation_beacon(top):
    image = Image.new("RGB", (16, 16), "#233447")
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 1, 14, 14), outline="#748b9a")
    draw.line((1, 14, 14, 14), fill="#182631")
    draw.rectangle((3, 3, 12, 12), fill="#172b3c", outline="#4b687d")
    if top:
        draw.ellipse((3, 3, 12, 12), outline="#426981")
        draw.ellipse((5, 5, 10, 10), fill="#549db2")
        draw.line((4, 8, 11, 8), fill="#a2dfe8")
        draw.line((8, 4, 8, 11), fill="#dafffc")
    else:
        draw.line((4, 10, 8, 6, 12, 10), fill="#70c2d2", width=2)
        draw.line((6, 12, 10, 12), fill="#6881a0")
        draw.point((8, 5), fill="#dcf5f7")
    return image


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
