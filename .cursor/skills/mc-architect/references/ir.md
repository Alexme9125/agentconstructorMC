# IR 速查

三类源文件进 git；`.schem` 是编译产物。

## terrain

`examples/plot-pad/terrain.ir.json`

- `origin` 是世界坐标，`layers[].offsetY` 相对 origin.y。
- `block` 可以是 palette 键（`structure`）或完整 ID（`minecraft:smooth_stone`）。
- 编译结果以 `terrain` 步骤写入 print_plan，适合地块垫层。

## structure

`examples/house-10/structure.ir.json`

- 局部坐标从 `(0,0,0)` 到 `size-1`。
- `ops`：`box`、`hollow_box`、`column`、`clear`。
- 带 `facing=` 的方块在 `paste_schem` 旋转 90/180/270 时由引擎改朝向。
- 编译结果是单个 `.schem` + `paste_schem` 步骤。

## city

`examples/city-module-128/city.ir.json`

- `roads`：`axis` x 或 z，`center` 为垂直于轴向的局部坐标，`roadWidth`/`sidewalk` 按主城规范。
- `plots`：`size` 只能是 10、20、50。
- `anchors`：灯或 NPC 占位。
- 编译器输出有序 fill/terrain/sparse，预览 schem 仅用于哈希。

## 不要做

- 不要把 128×128 城市写成上千条 sparse。
- 不要手写 gzip NBT。改 IR 再 compile。
