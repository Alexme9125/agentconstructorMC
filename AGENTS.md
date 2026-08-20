# AGENTS.md

## Cursor Cloud specific instructions

PrintCore has four development units (see `README.md` for the product overview): the
`logic` Java engine (Gradle), the `compiler` Python package, the `mcp` Python
package/CLI, and the `mods/printcore` Forge 1.20.1 mod (Gradle).

### Toolchain / environment notes

- Java 17 is required and lives at `/usr/lib/jvm/java-17-openjdk-amd64`. The startup
  update script does not install it; it is baked into the environment snapshot. The
  base image also has Java 21, so always export
  `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` before any Gradle command.
- `logic/settings.gradle` has no foojay toolchain resolver, so Gradle cannot
  auto-download JDK 17 for the engine — a real JDK 17 must be present (it is, via the
  snapshot). `mods/printcore/settings.gradle` does have the resolver.
- Python is pure standard library at runtime (no third-party deps). The only test
  dependency is `pytest`, refreshed by the update script. Both Python packages are run
  via `PYTHONPATH=compiler:mcp`, not installed — see the commands in `README.md` and
  `scripts/test.sh`.
- There is no linter/formatter configured in this repo (no ruff/flake8/eslint). "Lint"
  is not an available check.

### Test / build / run

- Everything (Java engine tests + Python tests): `./scripts/test.sh` (it defaults
  `JAVA_HOME` to the Java 17 path above).
- Java engine only: `cd logic && ./gradlew test --no-daemon`.
- Python only: `PYTHONPATH=compiler:mcp python3 -m pytest compiler/tests mcp/tests -q`.
- Compile an IR (core offline workflow): commands are in `README.md` (compiler
  `python3 -m printcore_compiler ...` and MCP `python3 -m printcore_mcp compile ...`).
  Output goes to `dist/<id>/` (`modules/<id>.schem`, `print_plan.json`,
  `manifest.json`), which is gitignored.
- Forge mod jar: `cd mods/printcore && ./gradlew build --no-daemon` → produces
  `build/libs/printcore-0.1.0.jar`. The first build downloads Minecraft/Forge mappings
  (needs egress to `maven.minecraftforge.net` and `plugins.gradle.org`); it takes a
  couple of minutes and is cached afterward.

### Live-print caveats (important)

- The MCP `print`/`status`/`job` flows talk to a running PrintCore HTTP server (the
  Forge mod inside a live Minecraft server) at `http://127.0.0.1:9471`. That server is
  not runnable in Cloud; only offline compilation works here. Do not claim a build was
  printed into a world.
- `python3 -m printcore_mcp status` prints a raw `ConnectionRefused` traceback when no
  server is reachable — this is expected offline. The MCP stdio server tool
  `printcore_status` (via `python3 -m printcore_mcp serve`) instead returns
  `{"reachable": false, ...}` gracefully.
