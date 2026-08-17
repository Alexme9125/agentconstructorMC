from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMPILER = ROOT / "compiler"
if str(COMPILER) not in sys.path:
    sys.path.insert(0, str(COMPILER))

from printcore_compiler.compile import compile_ir, load_json, load_palette  # noqa: E402

from .client import PrintCoreClient, PrintCoreError


def compile_ir_file(ir_path: str, out_dir: str | None = None, palette: str | None = None) -> dict:
    ir = load_json(Path(ir_path))
    palette_path = Path(palette) if palette else ROOT / "palette" / "palette.json"
    loaded = load_palette(palette_path if palette_path.exists() else None)
    out = Path(out_dir) if out_dir else ROOT / "dist" / ir["id"]
    result = compile_ir(ir, loaded, out)
    return {"ok": True, "out": str(out), **{k: result[k] for k in ("sha256", "schem_path") if k in result}, "plan": str(out / "print_plan.json")}


def submit_compiled(out_dir: str, client: PrintCoreClient | None = None) -> dict:
    client = client or PrintCoreClient()
    out = Path(out_dir)
    plan = json.loads((out / "print_plan.json").read_text(encoding="utf-8"))
    modules = out / "modules"
    if modules.exists():
        for schem in modules.glob("*.schem"):
            uploaded = client.upload_schematic(str(schem), schem.name)
            for step in plan["steps"]:
                if step.get("type") == "paste_schem" and step.get("schematic") == schem.name:
                    step["schematicId"] = uploaded["id"]
                    step["sha256"] = uploaded["sha256"]
    return client.submit_job(plan)


def wait_job(client: PrintCoreClient, job_id: str, timeout: float = 120.0) -> dict:
    deadline = time.time() + timeout
    while time.time() < deadline:
        job = client.job(job_id)
        if job.get("status") in {"COMPLETED", "FAILED", "ROLLED_BACK", "CANCELLED"}:
            return job
        time.sleep(0.25)
    raise TimeoutError(f"job {job_id} did not finish")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="printcore")
    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("status")
    compile_p = sub.add_parser("compile")
    compile_p.add_argument("ir")
    compile_p.add_argument("-o", "--out")
    compile_p.add_argument("--palette")
    print_p = sub.add_parser("print")
    print_p.add_argument("ir")
    print_p.add_argument("-o", "--out")
    print_p.add_argument("--palette")
    print_p.add_argument("--no-wait", action="store_true")
    job_p = sub.add_parser("job")
    job_p.add_argument("id")
    dry_p = sub.add_parser("dry-run")
    dry_p.add_argument("id")
    commit_p = sub.add_parser("commit")
    commit_p.add_argument("id")
    rb_p = sub.add_parser("rollback")
    rb_p.add_argument("id")
    sub.add_parser("serve")
    args = parser.parse_args(argv)

    if args.cmd == "compile":
        print(json.dumps(compile_ir_file(args.ir, args.out, args.palette), indent=2))
        return 0
    if args.cmd == "serve":
        from .server import serve_stdio

        serve_stdio()
        return 0

    client = PrintCoreClient()
    try:
        if args.cmd == "status":
            print(json.dumps(client.status(), indent=2))
        elif args.cmd == "print":
            compiled = compile_ir_file(args.ir, args.out, args.palette)
            submitted = submit_compiled(compiled["out"], client)
            dry = client.dry_run(submitted["id"])
            if dry.get("status") == "FAILED":
                print(json.dumps(dry, indent=2))
                return 2
            committed = client.commit(submitted["id"])
            if not args.no_wait:
                committed = wait_job(client, submitted["id"])
            print(json.dumps({"compiled": compiled, "job": committed}, indent=2))
        elif args.cmd == "job":
            print(json.dumps(client.job(args.id), indent=2))
        elif args.cmd == "dry-run":
            print(json.dumps(client.dry_run(args.id), indent=2))
        elif args.cmd == "commit":
            print(json.dumps(client.commit(args.id), indent=2))
        elif args.cmd == "rollback":
            print(json.dumps(client.rollback(args.id), indent=2))
        return 0
    except PrintCoreError as exc:
        print(json.dumps({"ok": False, "error": exc.message, "status": exc.status}), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
