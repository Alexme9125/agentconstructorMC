# PrintCore

Forge 1.20.1 服务端打印引擎 + Cursor Skill，让 AI Agent 用结构化 IR 设计 Minecraft 结构/地图，并打印进世界。

Agent 的工作单元是 **IR → 编译 → 打印作业**，不是百万次 `setblock`。

## 仓库结构

- `mods/printcore` — Forge 1.20.1 模组（HTTP 作业 API、主线程切片打印、快照回滚）
- `logic` — 无 Minecraft 依赖的作业引擎（可单测）
- `compiler` — Python：terrain/structure/city IR → Sponge `.schem` + `print_plan.json`
- `mcp/printcore_mcp` — MCP 与 CLI
- `.cursor/skills/mc-architect` — Agent 工作流与约束
- `examples` — `plot-pad`、`house-10`、`city-module-128`
- `schemas` / `palette` — 共用契约

WorldEdit 7.2.x 为可选伴侣：PrintCore **本地粘贴 Sponge Schematic v2**（与 WorldEdit 同一格式）。不要求 Agent 拼 `//set` 命令。

## 快速开始（离线，无需启动 Minecraft）

需要 Java 17 与 Python 3.11+。

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64   # 按本机路径调整
cd logic && ./gradlew test

PYTHONPATH=compiler python3 -m pytest compiler/tests -q
PYTHONPATH=compiler:mcp python3 -m pytest mcp/tests -q

PYTHONPATH=compiler python3 -m printcore_compiler examples/plot-pad/terrain.ir.json \
  --palette palette/palette.json -o dist/plot-pad
```

## 打印进世界

1. 用 Forge 1.20.1（或后续 Arclight 1.20.1）加载 `mods/printcore` 构建出的 jar。WorldEdit 可选。
2. 默认 HTTP：`127.0.0.1:9471`，token `printcore-dev-token`（见 `config/printcore-common.toml`）。
3. 游戏内 `/printcore status`。
4. 本地 Cursor 挂上 MCP（见 `.cursor/skills/mc-architect/references/print-api.md`），或：

```bash
export PYTHONPATH=compiler:mcp
export PRINTCORE_URL=http://127.0.0.1:9471
export PRINTCORE_TOKEN=printcore-dev-token
python3 -m printcore_mcp print examples/plot-pad/terrain.ir.json
```

Cloud Agent 通常碰不到你的本地服务器：只提交 IR 与 `dist/`，不要声称已经打印。

## 构建模组

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
cd mods/printcore
./gradlew build
# jar: build/libs/printcore-0.1.0.jar
```

首次构建会下载 Minecraft / Forge 映射，体积较大。

## 设计约束

- 模块 ≤ 128×128
- 主城地面 Y=96，垫层 Y=94/95/96
- 未知方块 ID 拒绝打印
- dry-run → 快照 → 切片提交 → 失败回滚

CityCore 地产/钱币/传送不在本仓库；后续作为 PrintCore 的调用方。
