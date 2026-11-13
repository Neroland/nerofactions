import argparse
import gzip
import json
import struct
from pathlib import Path


DATA = Path(__file__).resolve().parents[1] / "common/src/main/resources/data/nerofactions"
SIZE = (15, 8, 13)
FACTIONS = {
    "free_colonists": ("oak_planks", "stripped_oak_log", "mossy_stone_bricks", "brown",
                       ("plains", "meadow", "sunflower_plains"), ()),
    "miner_union": ("polished_andesite", "deepslate_bricks", "cut_copper", "orange",
                    ("stony_peaks", "windswept_hills", "badlands"), ("mars_canyons",)),
    "nero_corporation": ("smooth_quartz", "polished_blackstone_bricks", "yellow_terracotta", "black",
                         ("savanna", "savanna_plateau"), ("mars_dunes",)),
    "salvagers": ("oxidized_cut_copper", "mud_bricks", "red_terracotta", "gray",
                  ("desert", "wooded_badlands"), ("cindara_ember_plains",)),
    "space_guild": ("smooth_quartz", "polished_deepslate", "blue_terracotta", "blue",
                    ("snowy_plains", "snowy_slopes"), ("luna_highlands", "luna_maria")),
    "terraforming_authority": ("smooth_sandstone", "stripped_birch_log", "green_terracotta", "green",
                               ("birch_forest", "old_growth_birch_forest"), ("greenxertz_spore_marsh",)),
    "void_cult": ("blackstone", "polished_blackstone_bricks", "purple_terracotta", "purple",
                  ("dark_forest", "pale_garden"), ("nyxara_twilight_band",)),
}


def nbt_string(value):
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def tag(kind, name, value):
    return bytes([kind]) + nbt_string(name) + value


def integer(value):
    return struct.pack(">i", value)


def double(value):
    return struct.pack(">d", value)


def compound(*fields):
    return b"".join(fields) + b"\0"


def nbt_list(kind, values):
    return bytes([kind]) + integer(len(values)) + b"".join(values)


def point(values):
    return nbt_list(3, [integer(value) for value in values])


def template(faction, floor, wall, accent, dye):
    blocks = {}

    def put(x, y, z, block, properties=(), data=None):
        if ":" not in block:
            block = "minecraft:" + block
        blocks[x, y, z] = (block, properties, data)

    def fill(x_range, y_range, z_range, block):
        for x in x_range:
            for y in y_range:
                for z in z_range:
                    put(x, y, z, block)

    fill(range(15), range(2), range(13), wall)
    fill(range(15), range(2, 3), range(13), floor)
    fill(range(1, 14), range(3, 7), range(2, 12), "air")
    fill(range(1, 14), range(3, 7), range(11, 12), wall)
    for x in (1, 13):
        fill(range(x, x + 1), range(3, 7), range(3, 12), wall)
        for z in (5, 8):
            put(x, 5, z, "glass")
    for x in (1, 4, 10, 13):
        fill(range(x, x + 1), range(3, 7), range(3, 4), wall)
    fill(range(1, 14), range(6, 7), range(3, 12), accent)
    fill(range(2, 13), range(7, 8), range(4, 11), floor)
    for x in (3, 11):
        put(x, 5, 4, "lantern", (("hanging", "true"),))
        put(x, 5, 10, "lantern", (("hanging", "true"),))
    for x in (6, 7, 8):
        put(x, 2, 0, "stone_brick_stairs", (("facing", "south"),))
        put(x, 2, 1, accent)
    put(4, 3, 9, "nerofactions:faction_registry")
    put(7, 3, 9, "nerofactions:contract_board")
    put(10, 3, 9, "nerofactions:trade_terminal", (("faction", faction),))
    banner = compound(
        tag(8, "id", nbt_string("minecraft:banner")),
        tag(9, "patterns", nbt_list(10, [compound(
            tag(8, "pattern", nbt_string("nerofactions:" + faction)),
            tag(8, "color", nbt_string("white")),
        )])),
    )
    put(7, 5, 11, dye + "_wall_banner", (("facing", "north"),), banner)

    states = {}
    palette = []
    entries = []
    for pos, (block, properties, data) in sorted(blocks.items()):
        key = (block, properties)
        if key not in states:
            states[key] = len(palette)
            fields = [tag(8, "Name", nbt_string(block))]
            if properties:
                fields.append(tag(10, "Properties", compound(*[
                    tag(8, name, nbt_string(value)) for name, value in properties
                ])))
            palette.append(compound(*fields))
        entry = [tag(9, "pos", point(pos)), tag(3, "state", integer(states[key]))]
        if data is not None:
            entry.append(tag(10, "nbt", data))
        entries.append(compound(*entry))

    root = compound(
        tag(3, "DataVersion", integer(3953)),
        tag(9, "size", point(SIZE)),
        tag(9, "palette", nbt_list(10, palette)),
        tag(9, "blocks", nbt_list(10, entries)),
        tag(9, "entities", nbt_list(10, [
            inhabitant("faction_envoy", faction, (7, 3, 7)),
            inhabitant("faction_guard", faction, (3, 3, 4)),
            inhabitant("faction_guard", faction, (11, 3, 4)),
        ])),
    )
    return gzip.compress(tag(10, "", root), mtime=0)


def inhabitant(kind, faction, pos):
    x, y, z = pos
    return compound(
        tag(9, "pos", nbt_list(6, [double(x + 0.5), double(y), double(z + 0.5)])),
        tag(9, "blockPos", point(pos)),
        tag(10, "nbt", compound(
            tag(8, "id", nbt_string("nerofactions:" + kind)),
            tag(8, "OutpostFaction", nbt_string(faction)),
            tag(1, "PersistenceRequired", b"\x01"),
        )),
    )


def json_bytes(value):
    return (json.dumps(value, indent=2) + "\n").encode("utf-8")


def assets(faction, spec, salt):
    floor, wall, accent, dye, overworld, planets = spec
    path = "outpost/" + faction
    identifier = "nerofactions:" + path
    settings = {
        "biomes": "#nerofactions:has_outpost/" + faction,
        "step": "surface_structures",
        "spawn_overrides": {},
        "terrain_adaptation": "beard_thin",
    }
    yield "tags/worldgen/biome/has_outpost/" + faction + ".json", json_bytes({
        "replace": False,
        "values": [
            {"id": "minecraft:" + biome, "required": False} if biome == "pale_garden" else "minecraft:" + biome
            for biome in overworld
        ] + [
            {"id": "nerospace:" + biome, "required": False} for biome in planets
        ],
    })
    yield "worldgen/structure/" + path + ".json", json_bytes({
        "type": "nerofactions:faction_outpost", "faction": faction, **settings,
        "layout": {
            # A zero depth discards the starting piece on 26.3. No connectors means exactly one piece.
            **settings, "start_pool": identifier, "size": 1, "start_height": {"absolute": -2},
            "project_start_to_heightmap": "WORLD_SURFACE_WG", "max_distance_from_center": 32,
            "use_expansion_hack": False,
        },
    })
    yield "worldgen/structure_set/" + path + ".json", json_bytes({
        "structures": [{"structure": identifier, "weight": 1}],
        "placement": {
            "type": "minecraft:random_spread", "spacing": 40, "separation": 18, "salt": salt,
        },
    })
    yield "worldgen/template_pool/" + path + ".json", json_bytes({
        "fallback": "minecraft:empty",
        "elements": [{"weight": 1, "element": {
            "element_type": "minecraft:single_pool_element", "location": identifier,
            "processors": "minecraft:empty", "projection": "rigid",
        }}],
    })
    yield "structure/" + path + ".nbt", template(faction, floor, wall, accent, dye)


def outputs():
    for salt, (faction, spec) in enumerate(FACTIONS.items(), start=74820621):
        yield from assets(faction, spec, salt)
    yield "tags/worldgen/structure/outposts.json", json_bytes({
        "replace": False,
        "values": ["nerofactions:outpost/" + faction for faction in FACTIONS],
    })


def main():
    parser = argparse.ArgumentParser(description="Generate NeroFactions outpost assets")
    parser.add_argument("--check", action="store_true", help="Verify checked-in assets")
    check = parser.parse_args().check
    stale = []
    for name, content in outputs():
        path = DATA / name
        if check:
            if not path.exists() or path.read_bytes() != content:
                stale.append(name)
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    if stale:
        parser.exit(1, "Regenerate outposts:\n" + "\n".join(stale) + "\n")


if __name__ == "__main__":
    main()
