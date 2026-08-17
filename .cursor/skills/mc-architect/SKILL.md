---
name: mc-architect
description: Design Minecraft structures, terrain pads, and city modules with PrintCore IR, then compile and print them into a world. Use when the user asks to build, design, layout, or print Minecraft maps, houses, roads, plots, or terrain.
---

# MC Architect (PrintCore)

Agent 用结构化 IR 设计 Minecraft 结构/地图，经编译器生成 Sponge `.schem` 与 `print_plan.json`，再交给 PrintCore 作业 API 打印。不要对大体积逐块 `setblock`。

## 工作流（必须按顺序）

1. 读 [schemas/](../../../schemas/)、[palette/palette.json](../../../palette/palette.json)、目标 IR（若已有）。
2. 调用 `printcore_status`。不可达（Cloud Agent 常见）：只提交 IR/`dist/` 产物，并明确说明**尚未打印进世界**。
3. 写或改 `terrain` / `structure` / `city` IR。需要时再读 `references/ir.md`。
4. `compile_ir`。失败则改 IR，不要手写 `.schem` 二进制。
5. 世界可达时：`validate_job`（dry-run）通过后再 `commit_job`。
6. `job_status` 等到 `COMPLETED`。抽查 `printcore_get_blocks` / `printcore_heightmap`。失败则 `rollback_job`，禁止在失败体积上叠打。

CLI 等价命令（无 MCP 时）：

```bash
PYTHONPATH=compiler:mcp python3 -m printcore_mcp compile examples/plot-pad/terrain.ir.json
PYTHONPATH=compiler:mcp python3 -m printcore_mcp print examples/plot-pad/terrain.ir.json
```

## 硬约束

- 坐标：+X 东、+Z 南、+Y 上。主城地面 **Y=96**；地块垫层 Y=94 结构 / Y=95 承重 / Y=96 表层。
- 单模块水平尺寸 ≤ **128×128**。建设高度默认 Y=97–223。
- 禁止发明未注册方块 ID。未知 ID 先 `printcore_registry`。离线只用 `palette/palette.json`。
- 禁止把主城主路径做成 sparse 体素列表。sparse 仅用于灯、锚点、告示牌。
- 风格：约 60% 日式可爱魔法 / 40% 工业藏在外壳后。一期 palette 用原版替代糖玻璃/柏油。
- 主干：9 格路面 + 两侧各 3 格人行。支路：5 + 两侧各 2。
- 不改承重层、道路、地块边界（CityCore 保护规则预演）。
- MCP 没有 raw 命令。不要拼 WorldEdit `//set` 字符串。

## 打印顺序（城市模块）

道路 → 地块基础 → 核心 → 商业街 → 装饰 → NPC 锚点（一期只打 glowstone 标记，不刷 NPC）。

## Cloud vs 本地

- 本地：Skill → MCP/CLI → `http://127.0.0.1:9471` → 世界立刻变化。
- Cloud：只产 IR 与 `dist/<id>/`。把打印留给运营者的测试服。

细节见 `references/`。
