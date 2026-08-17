# Palette

源文件：[palette/palette.json](../../../../palette/palette.json)

| 键 | 一期原版替代 | 规划中的模组方块 |
|---|---|---|
| `structure` | `minecraft:smooth_stone` | 结构底层 |
| `bearing` | `minecraft:white_stained_glass` | SweetMagic 糖玻璃承重 |
| `surface` | `minecraft:white_stained_glass` | 可替换糖玻璃表层 |
| `border` | `minecraft:smooth_stone_slab` | 地块边界 |
| `road` | `minecraft:black_concrete` | KubeJS 柏油 |
| `sidewalk` | `minecraft:smooth_stone` | 人行 |
| `walkway` | `minecraft:terracotta` | 地块间步道 |

规则：

- IR 优先写 palette 键，不要散落硬编码 ID，除非是一次性装饰。
- 测试服装上 SweetMagic/KubeJS 后，只改 `palette.json` 并 `--check-registry`，不要改几何。
- 未知 ID：live 用 `printcore_registry`；离线拒绝编译/dry-run，不猜测替代方块。
