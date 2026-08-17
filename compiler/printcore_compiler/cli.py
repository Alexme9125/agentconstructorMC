from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .compile import CompileError, compile_ir, load_json, load_palette


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="printcore-compile", description="Compile PrintCore IR to schem + print_plan")
    parser.add_argument("ir", type=Path, help="terrain/structure/city IR JSON")
    parser.add_argument("-o", "--out", type=Path, default=None, help="output directory (default dist/<id>)")
    parser.add_argument("--palette", type=Path, default=None, help="palette JSON")
    parser.add_argument("--check-registry", action="store_true", help="reserved: compare blocks with a live PrintCore")
    args = parser.parse_args(argv)

    try:
        ir = load_json(args.ir)
        palette_path = args.palette
        if palette_path is None:
            guessed = Path("palette/palette.json")
            palette_path = guessed if guessed.exists() else None
        palette = load_palette(palette_path)
        out = args.out or Path("dist") / ir["id"]
        result = compile_ir(ir, palette, out)
        print(json.dumps({"ok": True, "out": str(out), "sha256": result["sha256"], "schematic": result["schem_path"]}, indent=2))
        return 0
    except (OSError, json.JSONDecodeError, CompileError, KeyError) as exc:
        print(json.dumps({"ok": False, "error": str(exc)}), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
