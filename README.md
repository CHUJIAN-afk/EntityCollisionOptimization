# EntityCollisionOptimization（ecopt）

Minecraft **1.21.1 / NeoForge 21.1.238** 的实体碰撞与实体段查询优化模组。全部优化默认生效，没有配置项。
结构划分见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)，与同类模组的对比与组合实测见 [docs/TECHNICAL.md](docs/TECHNICAL.md)。

## 优化清单

| 优化 | 接入点 | 说明 |
| --- | --- | --- |
| 段级增量表 | `EntitySection.add/remove` | 每段自带"可碰撞 / 可推挤"两张表，随实体进出段维护，查询按框遍历段直接取表；不做每 tick 全量重建，无突发毛刺 |
| 碰撞查询接管 | `EntityGetter.getEntityCollisions` | MixinExtras `@WrapMethod` 方法级接管，只遍历候选表并返回惰性形状列表 |
| 推挤查询接管 | `LivingEntity.pushEntities` | MixinExtras `@WrapOperation`，只遍历可推挤候选表 |
| noCollision 快速实现 | `CollisionGetter.noCollision` | 三重循环扫方块 + 空气/空形状早退 + 整方块只做 AABB 相交 + 命中即返回 |
| 方块状态缓存 | `LevelChunk.getBlockState` | 每区块定长开放寻址表，命中要求"同 tick + 段版本号未变"，密集堆积时同一格只读一次调色板 |
| 段修改版本号 | `LevelChunkSection.setBlockState` | 供方块缓存精确失效 |
| 段内子格索引 | `EntitySection.getEntities(AABB, consumer)` | 小查询框且同 tick 查 ≥2 次时走 2³ 子格索引，实体多格归档 + 时间戳去重，add/remove 立即失效 |
| 直接遍历内部列表 | `ClassInstanceMultiMap.iterator/find` | 去掉 Guava / Collections 包装，避免每个元素一次额外虚调用 |
| 支撑方块捷径 | `Entity.collide` | 纯竖直下落时按足迹两层探脚下方块，先探中心列；被完全挡住直接返回零位移 |

## 兼容性

* 注入全部使用 **MixinExtras**（`@WrapMethod` / `@WrapOperation`），可与其它模组在同一点叠加；
* 同一指令点被别的模组占用时用 `require = 0, expect = 0` 自动让位，不会让启动失败；
* "优先"放在方法级：`getEntityCollisions` 由本模组整体接管；
* 与 Lithium、加速碰撞（accelerated recoiling）的组合实测见 [docs/TECHNICAL.md](docs/TECHNICAL.md)。

## 已知取舍

* 方块缓存命中要求"同 tick + 所在段版本号未变"；段被改写（`setBlockState`）立刻失效，
  不走 `setBlockState` 的区块整体重载靠 tick 序号兜底；
* 段内子格索引对同一 tick 内的实体移动留 1 格（2 格方块）余量，超出余量的瞬移理论上可能漏检一次；
* 索引路径返回实体的顺序与线性扫描不同（碰撞、推挤、拾取都不依赖顺序）；
* 快速路径不做异常兜底：出现意外情况会直接暴露而不是静默降级。

## 构建

需要 JDK 21，`gradlew build`，产物在 `build/libs/`。

## 许可证

[MIT](LICENSE) © 2026 FirstSight
