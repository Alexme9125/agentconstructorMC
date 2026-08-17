from pathlib import Path

from printcore_mcp.cli import compile_ir_file

ROOT = Path(__file__).resolve().parents[2]


def test_compile_ir_tool(tmp_path):
    result = compile_ir_file(
        str(ROOT / "examples/plot-pad/terrain.ir.json"),
        out_dir=str(tmp_path / "plot-pad"),
        palette=str(ROOT / "palette/palette.json"),
    )
    assert result["ok"] is True
    assert (tmp_path / "plot-pad" / "print_plan.json").exists()
