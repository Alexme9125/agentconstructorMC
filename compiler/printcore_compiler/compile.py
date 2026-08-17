from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from .schem import Schematic, sha256

MAX_MODULE = 128
BUILD_MIN_Y = 94
BUILD_MAX_Y = 223


class CompileError(ValueError):
    pass


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def load_palette(path: Path | None) -> dict[str, str]:
    if path is None:
        return {}
    data = load_json(path)
    blocks = data.get("blocks", data)
    return {str(k): str(v) for k, v in blocks.items()}


def resolve_block(name: str, palette: dict[str, str]) -> str:
    if ":" in name:
        return name
    if name in palette:
        return palette[name]
    raise CompileError(f"unknown palette key '{name}'")


def compile_ir(ir: dict[str, Any], palette: dict[str, str], out_dir: Path) -> dict[str, Any]:
    kind = ir.get("kind")
    if kind == "terrain":
        return compile_terrain(ir, palette, out_dir)
    if kind == "structure":
        return compile_structure(ir, palette, out_dir)
    if kind == "city":
        return compile_city(ir, palette, out_dir)
    raise CompileError(f"unsupported IR kind {kind}")


def compile_terrain(ir: dict[str, Any], palette: dict[str, str], out_dir: Path) -> dict[str, Any]:
    origin = ir["origin"]
    size = ir["size"]
    width, length = size["x"], size["z"]
    _check_module(width, length)
    layers = ir["layers"]
    height = max(layer["offsetY"] for layer in layers) + 1
    schem = Schematic.filled(width, height, length)
    for layer in layers:
        block = resolve_block(layer["block"], palette)
        y = layer["offsetY"]
        schem.fill(0, y, 0, width - 1, y, length - 1, block)
    return _write_module(ir, origin, schem, out_dir, extra_steps=_terrain_steps(ir, palette, origin, width, length))


def _terrain_steps(ir: dict[str, Any], palette: dict[str, str], origin: dict[str, int], width: int, length: int) -> list[dict[str, Any]]:
    ox, oy, oz = origin["x"], origin["y"], origin["z"]
    return [
        {
            "type": "terrain",
            "id": ir["id"],
            "from": {"x": ox, "z": oz},
            "to": {"x": ox + width - 1, "z": oz + length - 1},
            "layers": [
                {"y": oy + layer["offsetY"], "block": resolve_block(layer["block"], palette)}
                for layer in ir["layers"]
            ],
        }
    ]


def compile_structure(ir: dict[str, Any], palette: dict[str, str], out_dir: Path) -> dict[str, Any]:
    origin = ir.get("origin", {"x": 0, "y": 0, "z": 0})
    size = ir["size"]
    width, height, length = size["x"], size["y"], size["z"]
    _check_module(width, length)
    local_palette = dict(palette)
    local_palette.update(ir.get("palette") or {})
    schem = Schematic.filled(width, height, length, "minecraft:air")
    for op in ir["ops"]:
        block = resolve_block(op.get("block", "air"), local_palette) if op["op"] != "clear" else "minecraft:air"
        x1, y1, z1 = op["from"]
        x2, y2, z2 = op.get("to", op["from"])
        hollow = bool(op.get("hollow") or op["op"] == "hollow_box")
        if op["op"] == "column":
            x2, z2 = x1, z1
        schem.fill(x1, y1, z1, x2, y2, z2, block, hollow=hollow)
    return _write_module(ir, origin, schem, out_dir, extra_steps=None, paste=True)


def compile_city(ir: dict[str, Any], palette: dict[str, str], out_dir: Path) -> dict[str, Any]:
    origin = ir["origin"]
    size = ir["size"]
    width, length = size["x"], size["z"]
    _check_module(width, length)
    ground_y = ir.get("groundY", 96)
    structure = resolve_block("structure", palette) if "structure" in palette else "minecraft:smooth_stone"
    bearing = resolve_block("bearing", palette) if "bearing" in palette else "minecraft:white_stained_glass"
    surface = resolve_block("surface", palette) if "surface" in palette else "minecraft:white_stained_glass"
    road = resolve_block("road", palette) if "road" in palette else "minecraft:black_concrete"
    sidewalk = resolve_block("sidewalk", palette) if "sidewalk" in palette else "minecraft:smooth_stone"
    border = resolve_block("border", palette) if "border" in palette else "minecraft:smooth_stone_slab"
    light = resolve_block("light", palette) if "light" in palette else "minecraft:lantern"
    anchor = resolve_block("anchor", palette) if "anchor" in palette else "minecraft:glowstone"

    ox, oy, oz = origin["x"], origin["y"], origin["z"]
    steps: list[dict[str, Any]] = []
    # Base pad covering the module at Y origin..ground
    steps.append(
        {
            "type": "fill",
            "id": f"{ir['id']}-structure",
            "from": {"x": ox, "y": oy, "z": oz},
            "to": {"x": ox + width - 1, "y": oy, "z": oz + length - 1},
            "block": structure,
        }
    )

    road_steps = []
    for road_def in ir.get("roads", []):
        road_steps.extend(_road_steps(road_def, ox, oz, oy, ground_y, road, sidewalk, width, length))
    steps.extend(road_steps)

    for plot in ir.get("plots", []):
        steps.extend(_plot_steps(plot, ox, oz, oy, ground_y, bearing, surface, border))

    sparse = []
    for item in ir.get("anchors", []):
        sparse.append(
            {
                "x": ox + item["x"],
                "y": item.get("y", ground_y + 1),
                "z": oz + item["z"],
                "block": light if item.get("kind") == "light" else anchor,
            }
        )
    if sparse:
        steps.append({"type": "sparse", "id": f"{ir['id']}-anchors", "blocks": sparse})

    height = max(4, (ground_y - oy) + 3)
    schem = Schematic.filled(width, height, length, "minecraft:air")
    # Bake a preview schematic of the module for hash/manifest.
    for x in range(width):
        for z in range(length):
            schem.set(x, 0, z, structure)
    for step in steps:
        if step["type"] == "fill":
            _bake_fill(schem, step, ox, oy, oz)
        elif step["type"] == "terrain":
            _bake_terrain(schem, step, ox, oy, oz)
        elif step["type"] == "sparse":
            for block in step["blocks"]:
                _bake_block(schem, block["x"] - ox, block["y"] - oy, block["z"] - oz, block["block"])

    plan = {
        "version": 1,
        "id": ir["id"],
        "dimension": ir.get("dimension", "minecraft:overworld"),
        "steps": steps,
    }
    return _write_outputs(ir["id"], origin, schem, out_dir, plan, paste=False)


def _road_steps(road_def: dict[str, Any], ox: int, oz: int, oy: int, ground_y: int, road: str, sidewalk: str, width: int, length: int) -> list[dict[str, Any]]:
    axis = road_def["axis"]
    center = road_def["center"]
    start = road_def.get("start", 0)
    span = road_def["length"]
    road_w = road_def["roadWidth"]
    walk = road_def["sidewalk"]
    half = road_w // 2
    steps = []
    if axis == "x":
        z0 = oz + center - half
        z1 = z0 + road_w - 1
        x0 = ox + start
        x1 = x0 + span - 1
        steps.append(_fill(f"{road_def['id']}-road", x0, ground_y, z0, x1, ground_y, z1, road))
        if walk:
            steps.append(_fill(f"{road_def['id']}-walk-n", x0, ground_y, z0 - walk, x1, ground_y, z0 - 1, sidewalk))
            steps.append(_fill(f"{road_def['id']}-walk-s", x0, ground_y, z1 + 1, x1, ground_y, z1 + walk, sidewalk))
    else:
        x0 = ox + center - half
        x1 = x0 + road_w - 1
        z0 = oz + start
        z1 = z0 + span - 1
        steps.append(_fill(f"{road_def['id']}-road", x0, ground_y, z0, x1, ground_y, z1, road))
        if walk:
            steps.append(_fill(f"{road_def['id']}-walk-w", x0 - walk, ground_y, z0, x0 - 1, ground_y, z1, sidewalk))
            steps.append(_fill(f"{road_def['id']}-walk-e", x1 + 1, ground_y, z0, x1 + walk, ground_y, z1, sidewalk))
    return steps


def _plot_steps(plot: dict[str, Any], ox: int, oz: int, oy: int, ground_y: int, bearing: str, surface: str, border: str) -> list[dict[str, Any]]:
    size = plot["size"]
    x0 = ox + plot["x"]
    z0 = oz + plot["z"]
    x1 = x0 + size - 1
    z1 = z0 + size - 1
    pid = plot["id"]
    return [
        {
            "type": "terrain",
            "id": f"{pid}-pad",
            "from": {"x": x0, "z": z0},
            "to": {"x": x1, "z": z1},
            "layers": [
                {"y": ground_y - 2, "block": "minecraft:smooth_stone"},
                {"y": ground_y - 1, "block": bearing},
                {"y": ground_y, "block": surface},
            ],
        },
        _fill(f"{pid}-border-n", x0, ground_y + 1, z0, x1, ground_y + 1, z0, border),
        _fill(f"{pid}-border-s", x0, ground_y + 1, z1, x1, ground_y + 1, z1, border),
        _fill(f"{pid}-border-w", x0, ground_y + 1, z0, x0, ground_y + 1, z1, border),
        _fill(f"{pid}-border-e", x1, ground_y + 1, z0, x1, ground_y + 1, z1, border),
    ]


def _fill(step_id: str, x1: int, y1: int, z1: int, x2: int, y2: int, z2: int, block: str) -> dict[str, Any]:
    return {
        "type": "fill",
        "id": step_id,
        "from": {"x": x1, "y": y1, "z": z1},
        "to": {"x": x2, "y": y2, "z": z2},
        "block": block,
    }


def _bake_fill(schem: Schematic, step: dict[str, Any], ox: int, oy: int, oz: int) -> None:
    a, b = step["from"], step["to"]
    schem.fill(a["x"] - ox, a["y"] - oy, a["z"] - oz, b["x"] - ox, b["y"] - oy, b["z"] - oz, step["block"])


def _bake_terrain(schem: Schematic, step: dict[str, Any], ox: int, oy: int, oz: int) -> None:
    for x in range(step["from"]["x"], step["to"]["x"] + 1):
        for z in range(step["from"]["z"], step["to"]["z"] + 1):
            for layer in step["layers"]:
                _bake_block(schem, x - ox, layer["y"] - oy, z - oz, layer["block"])


def _bake_block(schem: Schematic, x: int, y: int, z: int, block: str) -> None:
    if 0 <= x < schem.width and 0 <= y < schem.height and 0 <= z < schem.length:
        schem.set(x, y, z, block)


def _write_module(
    ir: dict[str, Any],
    origin: dict[str, int],
    schem: Schematic,
    out_dir: Path,
    extra_steps: list[dict[str, Any]] | None,
    paste: bool = False,
) -> dict[str, Any]:
    if extra_steps is not None:
        plan = {
            "version": 1,
            "id": ir["id"],
            "dimension": ir.get("dimension", "minecraft:overworld"),
            "steps": extra_steps,
        }
        return _write_outputs(ir["id"], origin, schem, out_dir, plan, paste=False)
    plan = {
        "version": 1,
        "id": ir["id"],
        "dimension": ir.get("dimension", "minecraft:overworld"),
        "steps": [],
    }
    return _write_outputs(ir["id"], origin, schem, out_dir, plan, paste=True)


def _write_outputs(
    job_id: str,
    origin: dict[str, int],
    schem: Schematic,
    out_dir: Path,
    plan: dict[str, Any],
    paste: bool,
) -> dict[str, Any]:
    out_dir.mkdir(parents=True, exist_ok=True)
    modules = out_dir / "modules"
    modules.mkdir(exist_ok=True)
    filename = f"{job_id}.schem"
    data = schem.to_bytes()
    digest = sha256(data)
    (modules / filename).write_bytes(data)
    if paste:
        plan["steps"] = [
            {
                "type": "paste_schem",
                "id": job_id,
                "schematic": filename,
                "sha256": digest,
                "origin": origin,
                "rotation": 0,
                "ignoreAir": True,
            }
        ]
    (out_dir / "print_plan.json").write_text(json.dumps(plan, indent=2) + "\n", encoding="utf-8")
    manifest = {
        "id": job_id,
        "origin": origin,
        "size": {"x": schem.width, "y": schem.height, "z": schem.length},
        "sha256": digest,
        "schematic": f"modules/{filename}",
        "blocks": schem.unique_blocks(),
        "dataVersion": schem.data_version,
    }
    (out_dir / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return {"plan": plan, "manifest": manifest, "schem_path": str(modules / filename), "sha256": digest}


def _check_module(width: int, length: int) -> None:
    if width > MAX_MODULE or length > MAX_MODULE:
        raise CompileError(f"module {width}x{length} exceeds {MAX_MODULE}x{MAX_MODULE}")
    if width <= 0 or length <= 0:
        raise CompileError("module size must be positive")
