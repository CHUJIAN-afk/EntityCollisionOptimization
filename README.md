# EntityCollisionOptimization（ecopt）

Minecraft **1.21.1 / NeoForge 21.1.238** 的实体碰撞与实体段查询优化模组。全部优化默认生效，没有配置项。
结构划分见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)，与同类模组的对比与组合实测见 [docs/TECHNICAL.md](docs/TECHNICAL.md)。

> 本仓库的 `1.20.1-forge` 分支移植到 **Minecraft 1.20.1 / Forge 47.x**，差异见 [1.20.1 Forge 分支](#1201-forge-分支的差异与降级项)。

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

需要 JDK 17，`gradlew build`，产物在 `build/libs/`。

## 1.20.1 Forge 分支的差异与降级项

构建：ForgeGradle 6 + MixinGradle，`official` + 1.20.1 映射，Forge `47.4.22`，Java 17，元数据用 `src/main/templates/META-INF/mods.toml`。

**优化清单与 1.21.1/NeoForge 版本完全一致，没有降级或停用项**：11 个 mixin 的注入点、候选表、子格索引、方块缓存、支撑方块捷径在 1.20.1 上都是等价生效的（相关方法签名逐一核对过，`EntitySection#add/remove/getEntities`、`EntitySectionStorage#createSection`、`LevelEntityGetterAdapter#sectionStorage`、`LevelChunkSection#setBlockState/read/recalcBlockCounts/maybeHas`、`LevelChunk#getBlockState`、`ClassInstanceMultiMap#iterator/find`、`Level#getEntities()`、`LivingEntity#pushEntities`、`EntitySelector#CAN_BE_COLLIDED_WITH`、`EntitySelector#NO_SPECTATORS` 均未变）。

移植时需要处理的差异：

* **事件系统**：改用 `net.minecraftforge.event.TickEvent.ServerTickEvent` / `ClientTickEvent`（判断 `Phase.END`）、`net.minecraftforge.eventbus.api.SubscribeEvent`、`net.minecraftforge.fml.common.Mod.EventBusSubscriber`，主类 `@Mod` 来自 `net.minecraftforge.fml.common.Mod`；
* **MixinExtras**：Forge 1.20.1 不自带，改为 `compileOnly(annotationProcessor(...mixinextras-common))` + `implementation(jarJar(...mixinextras-forge))`，运行时随 jar 打包。`@WrapMethod` 需要 ≥ 0.4.0，这里用 0.5.3；
* **`Entity#updateFluidHeightAndDoFluidPushing()V`**：1.20.1 原版只有 `(TagKey<Fluid>, double)` 版本，无参重载是 Forge 补丁新增的（生产环境不会被重命名），因此该注入标 `remap = false`；
* **接口 mixin**：`EntityGetter#getEntityCollisions` 与 `CollisionGetter#noCollision` 的注入点只能是接口默认方法，Mixin 注解处理器会报 `INJECTOR_IN_INTERFACE`（refmap 仍正常生成），构建里把这条诊断降级掉；
* **`@Shadow` 改成 `@Accessor` / `@Invoker`**：Forge 1.20.1 生产环境按 SRG 名字运行，成员引用必须能被 refmap 重映射；注解处理器只会为 `@Accessor`/`@Invoker` 写映射（`@Shadow` 不写），所以 `ClassInstanceMultiMap`、`EntitySection`、`LevelEntityGetterAdapter` 的字段与 `LevelChunkSection#maybeHas` 都改成访问器/调用器，语义不变；
* **refmap 与构建缓存**：MixinGradle 的 refmap 生成在 `build/tmp` 下，不算 `compileJava` 的缓存输出，构建里关掉了该任务的构建缓存，否则命中缓存后 jar 会缺 `ecopt.refmap.json`；
* 包名、模组 id、mixin 接入点位置不变，逻辑类与 duck 接口未改动。

产物：`build/libs/ecopt-<version>-all.jar`（已用 JarJar 打入 MixinExtras 运行时，装这一个即可）。不带 `-all` 的那个 jar 不含 MixinExtras，单独安装会缺少运行期依赖。

## 许可证

[MIT](LICENSE) © 2026 FirstSight
