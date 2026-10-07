#!/usr/bin/env python3
"""Generates every resource of the mod that is mechanical rather than creative.

Run from the repository root:

    python3 tools/gen_assets.py

It writes, deterministically:

  * block textures (16x16 PNG) for every MachineKind and every material block
  * item textures (16x16 PNG) for the tools and materials
  * block state files (axis / facing / lit variants)
  * block and item models
  * the language file (all 118 element names included)
  * crafting and smelting recipes for every block and item
  * block loot tables so everything is obtainable in survival
  * the ore generation placement entries are hand written (data/worldgen)

Only the pixel art is procedural here; the physics, the layout and the gameplay are not
generated.
"""
from __future__ import annotations

import json
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/particleaccelerator"
DATA = ROOT / "src/main/resources/data/particleaccelerator"
NS = "particleaccelerator"

# --------------------------------------------------------------------------------------------
# Minimal PNG writer (no third party dependencies)
# --------------------------------------------------------------------------------------------


def write_png(path: Path, pixels: list[list[tuple[int, int, int, int]]]) -> None:
    height = len(pixels)
    width = len(pixels[0])
    raw = bytearray()
    for row in pixels:
        raw.append(0)  # filter type 0
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def blank(size: int = 16) -> list[list[tuple[int, int, int, int]]]:
    return [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]


def rgb(colour: int) -> tuple[int, int, int, int]:
    return ((colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF, 255)


def shade(colour: int, factor: float) -> int:
    r = min(255, max(0, int(((colour >> 16) & 0xFF) * factor)))
    g = min(255, max(0, int(((colour >> 8) & 0xFF) * factor)))
    b = min(255, max(0, int((colour & 0xFF) * factor)))
    return (r << 16) | (g << 8) | b


def metal_base(canvas, base: int, seed: int) -> None:
    """A brushed metal plate with a few rivets: the shared look of every machine block."""
    for y in range(16):
        for x in range(16):
            noise = ((x * 7 + y * 13 + seed * 31) % 5 - 2) * 0.02
            canvas[y][x] = rgb(shade(base, 1.0 + noise))
    # rivets
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        canvas[y][x] = rgb(shade(base, 1.35))
    # frame
    for i in range(16):
        canvas[0][i] = rgb(shade(base, 0.7))
        canvas[15][i] = rgb(shade(base, 0.7))
        canvas[i][0] = rgb(shade(base, 0.75))
        canvas[i][15] = rgb(shade(base, 0.75))


def glyph(canvas, points, colour, factor=1.5):
    for x, y in points:
        if 0 <= x < 16 and 0 <= y < 16:
            canvas[y][x] = rgb(shade(colour, factor))


def pattern(canvas, name: str, colour: int) -> None:
    """A recognisable pattern per component family, drawn on top of the metal base."""
    if name == "pipe":
        for x in range(2, 14):
            canvas[7][x] = rgb(shade(colour, 1.2))
            canvas[8][x] = rgb(shade(colour, 1.2))
        for x in range(4, 12, 2):
            canvas[6][x] = rgb(shade(colour, 0.8))
            canvas[9][x] = rgb(shade(colour, 0.8))
    elif name == "rings":
        for r in (2.5, 4.5):
            for angle in range(0, 360, 6):
                x = int(8 + r * math.cos(math.radians(angle)))
                y = int(8 + r * math.sin(math.radians(angle)))
                if 0 <= x < 16 and 0 <= y < 16:
                    canvas[y][x] = rgb(shade(colour, 1.4))
    elif name == "coil":
        for x in range(2, 14):
            for y in range(3, 13):
                if (x + y) % 4 == 0 or (x - y) % 4 == 0:
                    canvas[y][x] = rgb(shade(colour, 1.3))
    elif name == "quad":
        for cx, cy in ((4, 4), (11, 4), (4, 11), (11, 11)):
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    canvas[cy + dy][cx + dx] = rgb(shade(colour, 1.35))
    elif name == "dipole":
        for y in range(3, 13):
            canvas[y][4] = rgb(shade(colour, 1.4))
            canvas[y][5] = rgb(shade(colour, 1.4))
            canvas[y][10] = rgb(shade(colour, 1.4))
            canvas[y][11] = rgb(shade(colour, 1.4))
    elif name == "detector":
        for i in range(4, 12):
            canvas[i][i] = rgb(shade(colour, 1.5))
            canvas[i][15 - i] = rgb(shade(colour, 1.5))
        for i in range(6, 10):
            canvas[8][i] = rgb(shade(colour, 1.6))
            canvas[i][8] = rgb(shade(colour, 1.6))
    elif name == "screen":
        for y in range(4, 12):
            for x in range(3, 13):
                canvas[y][x] = rgb(shade(0x101820, 1.0))
        for x in range(5, 11, 2):
            canvas[6][x] = rgb(shade(colour, 1.6))
            canvas[9][x] = rgb(shade(colour, 1.3))
    elif name == "cable":
        for x in range(1, 15):
            y = 8 + int(2 * math.sin(x / 2.0))
            canvas[max(0, min(15, y))][x] = rgb(shade(colour, 1.4))
            canvas[max(0, min(15, y + 1))][x] = rgb(shade(colour, 0.8))
    elif name == "cool":
        for x in range(2, 14):
            canvas[4][x] = rgb(shade(colour, 1.3))
            canvas[11][x] = rgb(shade(colour, 1.3))
        for y in range(4, 12):
            for x in range(3, 13, 3):
                canvas[y][x] = rgb(shade(colour, 1.1))
    elif name == "shield":
        for y in range(2, 14):
            for x in range(2, 14):
                canvas[y][x] = rgb(shade(colour, 1.0 + (((x + y) // 2) % 2) * 0.15))
    elif name == "source":
        for angle in range(0, 360, 20):
            for r in (1, 2, 3):
                x = int(8 + r * math.cos(math.radians(angle)))
                y = int(8 + r * math.sin(math.radians(angle)))
                if 0 <= x < 16 and 0 <= y < 16:
                    canvas[y][x] = rgb(shade(colour, 1.6))
    elif name == "light":
        for y in range(3, 13):
            for x in range(3, 13):
                canvas[y][x] = rgb(shade(colour, 1.2))
        for y in range(5, 11):
            for x in range(5, 11):
                canvas[y][x] = rgb(shade(colour, 1.7))


def isometric_item(colour: int, glyph_kind: str) -> list[list[tuple[int, int, int, int]]]:
    """A simple but readable item sprite (16x16): a body plus an emblem."""
    canvas = blank()
    for y in range(3, 14):
        for x in range(2, 14):
            edge = x in (2, 13) or y in (3, 13)
            canvas[y][x] = rgb(shade(colour, 0.75 if edge else 1.0))
    pattern(canvas, glyph_kind, colour)
    return canvas


# --------------------------------------------------------------------------------------------
# Machine catalogue (must match MachineKind)
# --------------------------------------------------------------------------------------------

SYSTEM_COLOUR = {
    "BEAMLINE": 0x8FD3FF,
    "VACUUM": 0x9BE8AE,
    "MAGNET": 0xFF8A65,
    "RF": 0xFFD166,
    "DETECTOR": 0xC792EA,
    "CONTROL": 0xB0BEC5,
    "POWER": 0xFFF176,
    "COOLING": 0x81D4FA,
    "CRYO": 0xB39DDB,
    "SHIELDING": 0x9E9E9E,
}

# id: (system, orientation NONE/FACING/AXIS, pattern, extra lit property)
MACHINES = {
    "beam_pipe": ("BEAMLINE", "AXIS", "pipe", False),
    "vacuum_chamber": ("VACUUM", "AXIS", "pipe", False),
    "vacuum_pump": ("VACUUM", "NONE", "cool", False),
    "vacuum_gauge": ("VACUUM", "NONE", "screen", False),
    "vacuum_valve": ("VACUUM", "AXIS", "pipe", False),
    "beam_monitor": ("BEAMLINE", "AXIS", "screen", False),
    "beam_dump": ("BEAMLINE", "AXIS", "shield", False),
    "dipole_magnet": ("MAGNET", "AXIS", "dipole", False),
    "quadrupole_magnet": ("MAGNET", "AXIS", "quad", False),
    "sextupole_magnet": ("MAGNET", "AXIS", "quad", False),
    "steering_magnet": ("MAGNET", "AXIS", "coil", False),
    "superconducting_dipole": ("MAGNET", "AXIS", "coil", False),
    "rf_cavity": ("RF", "AXIS", "rings", False),
    "particle_source": ("BEAMLINE", "NONE", "source", False),
    "injector": ("BEAMLINE", "AXIS", "pipe", False),
    "tracking_detector": ("DETECTOR", "AXIS", "detector", False),
    "calorimeter": ("DETECTOR", "AXIS", "detector", False),
    "scintillation_detector": ("DETECTOR", "AXIS", "detector", False),
    "cherenkov_detector": ("DETECTOR", "AXIS", "rings", False),
    "muon_detector": ("DETECTOR", "AXIS", "detector", False),
    "radiation_detector": ("DETECTOR", "NONE", "detector", False),
    "collision_chamber": ("DETECTOR", "AXIS", "detector", False),
    "control_computer": ("CONTROL", "FACING", "screen", False),
    "power_supply": ("POWER", "FACING", "cable", False),
    "power_cable": ("POWER", "AXIS", "cable", False),
    "cooling_unit": ("COOLING", "FACING", "cool", False),
    "cryogenic_unit": ("CRYO", "FACING", "cool", False),
    "target_station": ("BEAMLINE", "AXIS", "shield", False),
    "decay_chamber": ("BEAMLINE", "NONE", "source", False),
    "warning_light": ("CONTROL", "NONE", "light", True),
}

MATERIAL_BLOCKS = {
    "lead_block": (0x8A8FA3, "shield"),
    "lead_shielding": (0x7E8496, "shield"),
    "concrete_shielding": (0xBFBFB8, "shield"),
    "water_shielding": (0x4FA8E0, "shield"),
    "borated_polyethylene": (0xE8E8EA, "shield"),
    "machine_casing": (0x9AA3AE, "pipe"),
    "cryostat_wall": (0xC0C4D0, "cool"),
    "lead_ore": (0x8A8FA3, "ore"),
    "deepslate_lead_ore": (0x5A5F6E, "ore"),
}

ITEMS = {
    "blueprint": (0x7FC7FF, "screen"),
    "quest_book": (0xC7A96B, "detector"),
    "geiger_counter": (0x9BE8AE, "screen"),
    "lead_ingot": (0x9AA0B4, "dipole"),
    "boron_powder": (0xE0E0D0, "source"),
    "coil_wire": (0xE07A3C, "coil"),
    "superconducting_coil": (0x7C6BE0, "coil"),
    "circuit_board": (0x3C8C4C, "detector"),
    "photodetector": (0x6BD3E8, "detector"),
    "target_foil": (0xC0C4CC, "shield"),
    "vacuum_seal": (0x2C2C34, "rings"),
    "hydrogen_cell": (0xE8F4FF, "source"),
    "deuterium_cell": (0xC8E8FF, "source"),
    "tritium_cell": (0xA8F0D8, "source"),
    "helium_cell": (0xFFE8A8, "source"),
}


# --------------------------------------------------------------------------------------------
# Generators
# --------------------------------------------------------------------------------------------


def generate_textures() -> None:
    for kind, (system, orientation, pat, lit) in MACHINES.items():
        base = shade(SYSTEM_COLOUR[system], 0.45)
        canvas = blank()
        metal_base(canvas, base, hash(kind) % 7)
        pattern(canvas, pat, SYSTEM_COLOUR[system])
        write_png(ASSETS / "textures/block" / f"{kind}.png", canvas)
        if lit:
            lit_canvas = blank()
            metal_base(lit_canvas, base, hash(kind) % 7)
            pattern(lit_canvas, pat, 0xFFE060)
            write_png(ASSETS / "textures/block" / f"{kind}_lit.png", lit_canvas)

    for name, (colour, pat) in MATERIAL_BLOCKS.items():
        canvas = blank()
        if pat == "ore":
            metal_base(canvas, 0x6E6E76, hash(name) % 7)
            for x, y in ((3, 4), (9, 3), (5, 9), (11, 10), (7, 6), (12, 6)):
                canvas[y][x] = rgb(colour)
                canvas[y][x + 1] = rgb(shade(colour, 0.8))
        else:
            metal_base(canvas, colour, hash(name) % 7)
            pattern(canvas, pat, shade(colour, 1.3))
        texture = "block" if name not in ("lead_ingot",) else "item"
        write_png(ASSETS / f"textures/{texture}" / f"{name}.png", canvas)

    for name, (colour, pat) in ITEMS.items():
        write_png(ASSETS / "textures/item" / f"{name}.png", isometric_item(colour, pat))

    # icon: a ring collider seen from above on a dark background
    icon = blank(64)
    for y in range(64):
        for x in range(64):
            icon[y][x] = rgb(shade(0x101820, 1.0 + ((x + y) % 3 - 1) * 0.03))
    for angle in range(0, 360, 2):
        for radius, colour in ((22, 0x8FD3FF), (26, 0x3A7BD5)):
            x = int(32 + radius * math.cos(math.radians(angle)))
            y = int(32 + radius * math.sin(math.radians(angle)))
            for dx in (0, 1):
                for dy in (0, 1):
                    if 0 <= x + dx < 64 and 0 <= y + dy < 64:
                        icon[y + dy][x + dx] = rgb(colour)
    for x, y in ((32, 6), (32, 58), (6, 32), (58, 32)):
        for dx in range(-2, 3):
            for dy in range(-2, 3):
                if 0 <= x + dx < 64 and 0 <= y + dy < 64:
                    icon[y + dy][x + dx] = rgb(0xFFD166)
    write_png(ASSETS / "icon.png", icon)


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def generate_models_and_states() -> None:
    for kind, (system, orientation, pat, lit) in MACHINES.items():
        write_json(ASSETS / f"models/block/{kind}.json", {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": f"{NS}:block/{kind}"},
        })
        write_json(ASSETS / f"models/item/{kind}.json",
                   {"parent": f"{NS}:block/{kind}"})

        model = {"model": {"model": f"{NS}:block/{kind}"}}
        if orientation == "AXIS":
            write_json(ASSETS / f"blockstates/{kind}.json", {"variants": {
                "axis=y": {"model": f"{NS}:block/{kind}"},
                "axis=z": {"model": f"{NS}:block/{kind}", "x": 90},
                "axis=x": {"model": f"{NS}:block/{kind}", "x": 90, "y": 90},
            }})
        elif orientation == "FACING":
            write_json(ASSETS / f"blockstates/{kind}.json", {"variants": {
                "facing=north": {"model": f"{NS}:block/{kind}"},
                "facing=east": {"model": f"{NS}:block/{kind}", "y": 90},
                "facing=south": {"model": f"{NS}:block/{kind}", "y": 180},
                "facing=west": {"model": f"{NS}:block/{kind}", "y": 270},
            }})
        elif lit:
            write_json(ASSETS / f"blockstates/{kind}.json", {"variants": {
                "lit=false": {"model": f"{NS}:block/{kind}"},
                "lit=true": {"model": f"{NS}:block/{kind}_lit"},
            }})
            write_json(ASSETS / f"models/block/{kind}_lit.json", {
                "parent": "minecraft:block/cube_all",
                "textures": {"all": f"{NS}:block/{kind}_lit"},
            })
        else:
            write_json(ASSETS / f"blockstates/{kind}.json", {"variants": {"": model["model"]}})

    for name in list(MATERIAL_BLOCKS) + ["lead_ore", "deepslate_lead_ore"]:
        if name not in MATERIAL_BLOCKS:
            continue
        write_json(ASSETS / f"models/block/{name}.json", {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": f"{NS}:block/{name}"},
        })
        write_json(ASSETS / f"models/item/{name}.json", {"parent": f"{NS}:block/{name}"})
        write_json(ASSETS / f"blockstates/{name}.json",
                   {"variants": {"": {"model": f"{NS}:block/{name}"}}})

    for name in ITEMS:
        write_json(ASSETS / f"models/item/{name}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"{NS}:item/{name}"},
        })


def generate_lang() -> None:
    lang = {
        "itemGroup.particleaccelerator.main": "Particle Accelerator",
        "item.particleaccelerator.blueprint.tooltip":
            "Right click to open the construction guide; the ghost blocks show what to build",
        "item.particleaccelerator.quest_book.tooltip":
            "Right click to open the research journal and the element collection",
        "item.particleaccelerator.geiger_counter.tooltip":
            "Right click to measure the dose rate where you stand",
        "item.particleaccelerator.gas_cell.species": "Injecta: %s  (Z=%s, A=%s)",
    }
    for kind in MACHINES:
        label = " ".join(word.capitalize() for word in kind.split("_"))
        lang[f"block.particleaccelerator.{kind}"] = label
    for name in MATERIAL_BLOCKS:
        lang[f"block.particleaccelerator.{name}"] = " ".join(
            word.capitalize() for word in name.split("_"))
    for name in ITEMS:
        lang[f"item.particleaccelerator.{name}"] = " ".join(
            word.capitalize() for word in name.split("_"))
    write_json(ASSETS / "lang/en_us.json", lang)


# --------------------------------------------------------------------------------------------
# Recipes
# --------------------------------------------------------------------------------------------


def shapeless(result: str, count: int, *ingredients, group: str = None):
    recipe = {
        "type": "minecraft:crafting_shapeless",
        "ingredients": [tag_or_item(i) for i in ingredients],
        "result": {"id": result, "count": count},
    }
    if group:
        recipe["group"] = group
    return recipe


def tag_or_item(name):
    if isinstance(name, dict):
        return name
    if name.startswith("#"):
        return {"tag": name[1:]}
    if ":" in name:
        return {"item": name}
    return {"item": f"minecraft:{name}"}


def shaped(result: str, count: int, pattern: list[str], key: dict, group: str = None):
    recipe = {
        "type": "minecraft:crafting_shaped",
        "pattern": pattern,
        "key": {k: tag_or_item(v) for k, v in key.items()},
        "result": {"id": result, "count": count},
    }
    if group:
        recipe["group"] = group
    return recipe


def generate_recipes() -> None:
    l = lambda n: f"{NS}:{n}"          # noqa: E741 - short local helper
    v = lambda n: f"minecraft:{n}"     # noqa: E741

    recipes = {
        # --- tools -------------------------------------------------------------------------
        "blueprint": shapeless(l("blueprint"), 1, l("lead_ingot"), v("paper"), v("paper")),
        "quest_book": shapeless(l("quest_book"), 1, v("book"), v("ink_sac"), l("boron_powder")),
        "geiger_counter": shapeless(l("geiger_counter"), 1, l("circuit_board"),
                                    l("photodetector"), l("machine_casing")),
        # --- materials ---------------------------------------------------------------------
        "machine_casing": shaped(l("machine_casing"), 4, ["LL", "LL"],
                                 {"L": l("lead_ingot")}),
        "cryostat_wall": shaped(l("cryostat_wall"), 4, ["CC", "CC"],
                                {"C": l("machine_casing")}),
        "lead_block": shaped(l("lead_block"), 1, ["LLL", "LLL", "LLL"],
                             {"L": l("lead_ingot")}),
        "lead_ingot_from_block": shapeless(l("lead_ingot"), 9, l("lead_block")),
        "lead_shielding": shaped(l("lead_shielding"), 4, ["LL", "LL"],
                                 {"L": l("lead_block")}),
        "concrete_shielding": shaped(l("concrete_shielding"), 4, ["CC", "CC"],
                                     {"C": v("white_concrete")}),
        "water_shielding": shaped(l("water_shielding"), 4, ["WW", "WW"],
                                  {"W": v("water_bucket")}),
        "borated_polyethylene": shaped(l("borated_polyethylene"), 4, ["PP", "PP"],
                                       {"P": l("boron_powder")}),
        "boron_powder": shapeless(l("boron_powder"), 2, v("bone_meal"), v("coal")),
        "coil_wire": shaped(l("coil_wire"), 4, ["CC"], {"C": v("copper_ingot")}),
        "superconducting_coil": shaped(l("superconducting_coil"), 1, ["CNC", "NIN", "CNC"],
                                       {"C": l("coil_wire"), "N": v("netherite_scrap"),
                                        "I": v("iron_ingot")}),
        "circuit_board": shaped(l("circuit_board"), 2, ["CGC"],
                                {"C": v("copper_ingot"), "G": v("gold_ingot")}),
        "photodetector": shaped(l("photodetector"), 2, ["SC", "CS"],
                                {"S": v("glass"), "C": l("coil_wire")}),
        "target_foil": shaped(l("target_foil"), 4, ["II"], {"I": v("iron_ingot")}),
        "vacuum_seal": shaped(l("vacuum_seal"), 4, ["SS"],
                              {"S": {"item": v("slime_ball")}}),
        "hydrogen_cell": shapeless(l("hydrogen_cell"), 1, v("glass_bottle"), v("coal")),
        "deuterium_cell": shapeless(l("deuterium_cell"), 1, v("glass_bottle"),
                                    v("packed_ice"), v("coal")),
        "tritium_cell": shapeless(l("tritium_cell"), 1, v("glass_bottle"),
                                  v("glowstone_dust"), v("coal")),
        "helium_cell": shapeless(l("helium_cell"), 1, v("glass_bottle"), v("glowstone_dust")),
        # --- vacuum ------------------------------------------------------------------------
        "beam_pipe": shaped(l("beam_pipe"), 4, ["II", "II"], {"I": v("iron_ingot")}),
        "vacuum_chamber": shaped(l("vacuum_chamber"), 1, ["CIC", "I I", "CIC"],
                                 {"C": l("machine_casing"), "I": v("iron_ingot")}),
        "vacuum_pump": shaped(l("vacuum_pump"), 1, ["CPC", "PMP", "CPC"],
                              {"C": l("machine_casing"), "P": v("piston"),
                               "M": l("coil_wire")}),
        "vacuum_gauge": shapeless(l("vacuum_gauge"), 1, l("machine_casing"),
                                  v("glass"), l("circuit_board")),
        "vacuum_valve": shaped(l("vacuum_valve"), 1, ["CPC"],
                               {"C": l("machine_casing"), "P": l("beam_pipe")}),
        # --- magnets -----------------------------------------------------------------------
        "dipole_magnet": shaped(l("dipole_magnet"), 1, ["CWC", "III", "CWC"],
                                {"C": l("coil_wire"), "W": l("machine_casing"),
                                 "I": v("iron_block")}),
        "quadrupole_magnet": shaped(l("quadrupole_magnet"), 1, ["CIC", "IWI", "CIC"],
                                    {"C": l("coil_wire"), "I": v("iron_block"),
                                     "W": l("machine_casing")}),
        "sextupole_magnet": shaped(l("sextupole_magnet"), 1, ["CIC", "IWI", "CIC"],
                                   {"C": l("coil_wire"), "I": l("superconducting_coil"),
                                    "W": l("machine_casing")}),
        "steering_magnet": shaped(l("steering_magnet"), 1, ["CIC"],
                                  {"C": l("coil_wire"), "I": v("iron_block")}),
        "superconducting_dipole": shaped(l("superconducting_dipole"), 1,
                                         ["CSC", "IWI", "CSC"],
                                         {"C": l("superconducting_coil"), "S": l("machine_casing"),
                                          "I": v("netherite_block"), "W": l("cryostat_wall")}),
        # --- radio frequency ---------------------------------------------------------------
        "rf_cavity": shaped(l("rf_cavity"), 1, ["CSC", "SWS", "CSC"],
                            {"C": v("copper_block"), "S": l("superconducting_coil"),
                             "W": l("cryostat_wall")}),
        # --- sources -----------------------------------------------------------------------
        "particle_source": shaped(l("particle_source"), 1, ["CGC", "WMW", "CGC"],
                                  {"C": l("machine_casing"), "G": v("glass"),
                                   "W": l("coil_wire"), "M": v("redstone_block")}),
        "injector": shaped(l("injector"), 1, ["CCC", "BSB", "CCC"],
                           {"C": l("machine_casing"), "B": l("beam_pipe"),
                            "S": l("circuit_board")}),
        "target_station": shaped(l("target_station"), 1, ["CBC", "BTB", "CBC"],
                                 {"C": l("machine_casing"), "B": l("beam_pipe"),
                                  "T": l("target_foil")}),
        "decay_chamber": shaped(l("decay_chamber"), 1, ["CBC", "BGB", "CBC"],
                                {"C": l("machine_casing"), "B": l("beam_pipe"),
                                 "G": v("glass")}),
        "beam_dump": shaped(l("beam_dump"), 1, ["LLL", "LCL", "LLL"],
                            {"L": l("lead_block"), "C": l("machine_casing")}),
        "beam_monitor": shaped(l("beam_monitor"), 1, ["CBC", "BSB", "CBC"],
                               {"C": l("machine_casing"), "B": l("beam_pipe"),
                                "S": l("circuit_board")}),
        # --- detectors ---------------------------------------------------------------------
        "tracking_detector": shaped(l("tracking_detector"), 1, ["CBC", "BSB", "CBC"],
                                    {"C": l("machine_casing"), "B": l("beam_pipe"),
                                     "S": l("photodetector")}),
        "calorimeter": shaped(l("calorimeter"), 1, ["CLC", "LSL", "CLC"],
                              {"C": l("machine_casing"), "L": l("lead_block"),
                               "S": l("photodetector")}),
        "scintillation_detector": shaped(l("scintillation_detector"), 1, ["CBC", "BGB", "CBC"],
                                         {"C": l("machine_casing"), "B": l("beam_pipe"),
                                          "G": v("glowstone")}),
        "cherenkov_detector": shaped(l("cherenkov_detector"), 1, ["CBC", "BDB", "CBC"],
                                     {"C": l("machine_casing"), "B": l("beam_pipe"),
                                      "D": v("diamond")}),
        "muon_detector": shaped(l("muon_detector"), 1, ["CBC", "BSB", "CBC"],
                                {"C": l("machine_casing"), "B": l("beam_pipe"),
                                 "S": v("iron_block")}),
        "radiation_detector": shaped(l("radiation_detector"), 1, ["CGC", "GDG", "CGC"],
                                     {"C": l("machine_casing"), "G": v("glass"),
                                      "D": l("photodetector")}),
        "collision_chamber": shaped(l("collision_chamber"), 1, ["CBC", "BSB", "CBC"],
                                    {"C": l("machine_casing"), "B": l("beam_pipe"),
                                     "S": l("tracking_detector")}),
        # --- infrastructure ----------------------------------------------------------------
        "control_computer": shaped(l("control_computer"), 1, ["CSC", "SMS", "CPC"],
                                   {"C": l("machine_casing"), "S": l("circuit_board"),
                                    "M": l("beam_monitor"), "P": l("power_cable")}),
        "power_supply": shaped(l("power_supply"), 1, ["CIC", "IRI", "CIC"],
                               {"C": l("machine_casing"), "I": v("iron_ingot"),
                                "R": v("redstone_block")}),
        "power_cable": shaped(l("power_cable"), 4, ["CC"], {"C": l("coil_wire")}),
        "cooling_unit": shaped(l("cooling_unit"), 1, ["CIC", "IPI", "CIC"],
                               {"C": l("machine_casing"), "I": v("ice"),
                                "P": v("piston")}),
        "cryogenic_unit": shaped(l("cryogenic_unit"), 1, ["CBC", "BWB", "CBC"],
                                 {"C": l("cryostat_wall"), "B": v("blue_ice"),
                                  "W": l("superconducting_coil")}),
        "warning_light": shapeless(l("warning_light"), 1, l("machine_casing"),
                                   v("redstone_lamp"), l("coil_wire")),
    }

    for name, recipe in recipes.items():
        write_json(DATA / f"recipe/{name}.json", recipe)

    # smelting: the ore becomes lead
    for ore, result in (("lead_ore", "lead_ingot"), ("deepslate_lead_ore", "lead_ingot")):
        write_json(DATA / f"recipe/{result}_from_{ore}.json", {
            "type": "minecraft:smelting",
            "ingredient": {"item": f"{NS}:{ore}"},
            "result": {"id": f"{NS}:{result}"},
            "experience": 0.7,
            "cookingtime": 200,
        })


def generate_loot_tables() -> None:
    for name in list(MACHINES) + list(MATERIAL_BLOCKS):
        write_json(DATA / f"loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })
    # ore drops raw material (same block, smelted into an ingot)
    for ore in ("lead_ore", "deepslate_lead_ore"):
        write_json(DATA / f"loot_table/blocks/{ore}.json", {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [{
                    "type": "minecraft:alternatives",
                    "children": [
                        {"type": "minecraft:item", "name": f"{NS}:{ore}",
                         "conditions": [{"condition": "minecraft:match_tool",
                                         "predicate": {"predicates": {
                                             "minecraft:enchantments": [{
                                                 "enchantments": "minecraft:silk_touch",
                                                 "levels": {"min": 1}}]}}}]},
                        {"type": "minecraft:item", "name": f"{NS}:lead_ore"},
                    ],
                }],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })


def generate_tags() -> None:
    blocks = list(MACHINES) + list(MATERIAL_BLOCKS)
    write_json(ROOT / "src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json",
               {"replace": False, "values": [f"{NS}:{b}" for b in blocks]})
    write_json(ROOT / "src/main/resources/data/minecraft/tags/block/needs_iron_tool.json",
               {"replace": False, "values": [f"{NS}:{b}" for b in blocks]})
    for tag in ("lead_ores", "machine_blocks"):
        values = ([f"{NS}:lead_ore", f"{NS}:deepslate_lead_ore"] if tag == "lead_ores"
                  else [f"{NS}:{b}" for b in MACHINES])
        write_json(DATA / f"tags/block/{tag}.json", {"replace": False, "values": values})


def generate_advancements() -> None:
    def advancement(icon: str, title: str, description: str, parent: str | None, criteria):
        data = {
            "display": {
                "icon": {"id": icon},
                "title": {"translate": title},
                "description": {"translate": description},
                "frame": "task",
                "show_toast": True,
                "announce_to_chat": True,
            },
            "criteria": criteria,
        }
        if parent:
            data["parent"] = parent
        return data

    write_json(DATA / "advancement/root.json", advancement(
        f"{NS}:blueprint", "Particle Accelerator", "Craft the accelerator blueprint",
        None, {"craft": {"trigger": "minecraft:recipe_crafted",
                         "conditions": {"recipe_id": f"{NS}:blueprint"}}}))
    write_json(DATA / "advancement/lead.json", advancement(
        f"{NS}:lead_ingot", "Heavy Metal", "Smelt lead, the shielding material",
        f"{NS}:root", {"ingot": {"trigger": "minecraft:inventory_changed",
                                 "conditions": {"items": [{"items": f"{NS}:lead_ingot"}]}}}))
    write_json(DATA / "advancement/magnet.json", advancement(
        f"{NS}:dipole_magnet", "Bend the Beam", "Craft a dipole magnet",
        f"{NS}:lead", {"magnet": {"trigger": "minecraft:recipe_crafted",
                                  "conditions": {"recipe_id": f"{NS}:dipole_magnet"}}}))
    write_json(DATA / "advancement/cavity.json", advancement(
        f"{NS}:rf_cavity", "Give it Energy", "Craft an RF cavity",
        f"{NS}:magnet", {"cavity": {"trigger": "minecraft:recipe_crafted",
                                    "conditions": {"recipe_id": f"{NS}:rf_cavity"}}}))
    write_json(DATA / "advancement/computer.json", advancement(
        f"{NS}:control_computer", "Control Room", "Craft the control computer",
        f"{NS}:cavity", {"computer": {"trigger": "minecraft:recipe_crafted",
                                      "conditions": {"recipe_id": f"{NS}:control_computer"}}}))
    write_json(DATA / "advancement/geiger.json", advancement(
        f"{NS}:geiger_counter", "Safety First", "Craft a radiation meter",
        f"{NS}:lead", {"geiger": {"trigger": "minecraft:recipe_crafted",
                                  "conditions": {"recipe_id": f"{NS}:geiger_counter"}}}))


def main() -> None:
    generate_textures()
    generate_models_and_states()
    generate_lang()
    generate_recipes()
    generate_loot_tables()
    generate_tags()
    generate_advancements()
    print("assets written to", ASSETS)
    print("data written to", DATA)


if __name__ == "__main__":
    main()
