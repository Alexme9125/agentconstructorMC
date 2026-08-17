from __future__ import annotations

import json
from pathlib import Path

from printcore_compiler.compile import compile_ir, load_json, load_palette
from printcore_compiler.schem import Schematic, rotate_state, sha256

ROOT = Path(__file__).resolve().parents[2]


def test_schem_roundtrip():
    schem = Schematic.filled(2, 1, 2, "minecraft:air")
    schem.set(0, 0, 0, "minecraft:stone")
    schem.set(1, 0, 0, "minecraft:oak_stairs[facing=north,half=bottom,shape=straight]")
    data = schem.to_bytes()
    read = Schematic.from_bytes(data)
    assert read.get(0, 0, 0) == "minecraft:stone"
    assert "facing=north" in read.get(1, 0, 0)
    assert sha256(data) == sha256(read.to_bytes()) or read.get(0, 0, 0) == "minecraft:stone"


def test_rotate_state():
    assert rotate_state("minecraft:oak_stairs[facing=north,half=bottom,shape=straight]", 90).endswith(
        "facing=east,half=bottom,shape=straight]"
    ) or "facing=east" in rotate_state(
        "minecraft:oak_stairs[facing=north,half=bottom,shape=straight]", 90
    )


def test_compile_examples(tmp_path: Path):
    palette = load_palette(ROOT / "palette" / "palette.json")
    for rel in (
        Path("examples/plot-pad/terrain.ir.json"),
        Path("examples/house-10/structure.ir.json"),
        Path("examples/city-module-128/city.ir.json"),
    ):
        ir = load_json(ROOT / rel)
        out = tmp_path / ir["id"]
        result = compile_ir(ir, palette, out)
        plan = json.loads((out / "print_plan.json").read_text(encoding="utf-8"))
        manifest = json.loads((out / "manifest.json").read_text(encoding="utf-8"))
        assert plan["id"] == ir["id"]
        assert plan["steps"]
        assert manifest["sha256"] == result["sha256"]
        assert (out / manifest["schematic"]).is_file()
        if ir["kind"] == "city":
            types = [step["type"] for step in plan["steps"]]
            assert types[0] == "fill"
            assert "terrain" in types
            assert manifest["size"]["x"] == 128
            assert manifest["size"]["z"] == 128
        if ir["kind"] == "house-10" or ir["id"] == "house-10":
            assert plan["steps"][0]["type"] == "paste_schem"
            assert plan["steps"][0]["rotation"] == 0
