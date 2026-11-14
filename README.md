# EntityCollisionOptimization（ecopt）

Minecraft **1.20.1 / Forge 47.4.22** 的实体碰撞与实体段查询优化模组。已迁移 1.21.1 分支的全部优化路径，默认生效，没有配置项。
结构划分见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)，与同类模组的对比与组合实测见 [docs/TECHNICAL.md](docs/TECHNICAL.md)。

> 当前为 `1.20.1-forge` 分支，使用 Parchment `2023.09.03-1.20.1` 映射、Mixin 0.8.5 与内嵌的 MixinExtras 0.5.3。

## 优化清单

| 优化 | 接入点 | 说明 |
| --- | --- | --- |
| 段级增量表 | `EntitySection.add/remove` | 每段自带"可碰撞 / 可推挤"两张表，随实体进出段维护，查询按框遍历段直接取表；不做每 tick 全量重建，无突发毛刺 |
| 碰撞查询接管 | `Level.getEntityCollisions` | 类上合并具体实现，只遍历候选表并返回惰性形状列表；自定义碰撞规则回退到原版等价查询 |
| 推挤查询接管 | `LivingEntity.pushEntities` | MixinExtras `@WrapOperation`，只遍历可推挤候选表 |
| noCollision 快速实现 | `Level.noCollision` | 类上合并具体实现，三重循环扫方块 + 空气/空形状早退 + 整方块只做 AABB 相交 + 命中即返回 |
| 方块状态缓存 | `LevelChunk.getBlockState` | 每区块定长开放寻址表，校验段引用与修改版本，未变更的状态跨 tick 复用 |
| 段修改版本号 | `LevelChunkSection.setBlockState` | 供方块缓存精确失效 |
| 段内子格索引 | `EntitySection.getEntities(AABB, consumer)` | 小查询框且同 tick 查 ≥2 次时走 2³ 子格索引，实体多格归档 + 时间戳去重，add/remove 立即失效 |
| 直接遍历内部列表 | `ClassInstanceMultiMap.iterator/find` | 去掉 Guava / Collections 包装，避免每个元素一次额外虚调用 |
| 支撑方块捷径 | `Entity.collide` | 纯竖直下落时按足迹两层探脚下方块，先探中心列；被完全挡住直接返回零位移 |
| 干燥区段流体捷径 | `Entity.updateFluidHeightAndDoFluidPushing(Predicate)` | 按 tick 与段修改版本缓存流体存在性，调色板无流体时跳过逐格扫描 |

## 兼容性

* 使用 `@Inject`、`@Accessor`、`@Invoker` 与 MixinExtras `@WrapOperation`，不使用 `@Redirect`；MixinExtras 随发布 jar 内嵌，无需另装；
* 推挤包装使用优先级 500，让常规优先级的其它包装在外层执行；回退通过 `Operation.call` 保留调用链，调用点消失时允许跳过注入；
* `getEntityCollisions` / `noCollision` 在 `Level` 上实现，避免向接口默认方法注入；其它模组对这两个方法的覆盖需要单独验证；
* 船、矿车及其它重写 `canCollideWith` 的实体使用原版等价查询，保留它们对可推挤实体的碰撞行为；
* [docs/TECHNICAL.md](docs/TECHNICAL.md) 中的组合性能数据来自 1.21.1，不能作为当前 Forge 分支的兼容性结论。

## 已知取舍

* 方块缓存检查当前段引用及修改版本；`setBlockState`、`read`、`recalcBlockCounts` 会推进版本，段整体替换也会使缓存失效；
* 段内子格索引对同一 tick 内的实体移动留 1 格（2 格方块）余量，超出余量的瞬移理论上可能漏检一次；
* 索引路径返回实体的顺序与线性扫描不同（碰撞、推挤、拾取都不依赖顺序）；
* 快速路径不做异常兜底：出现意外情况会直接暴露而不是静默降级。

## 构建

需要 JDK 17，`gradlew build`，安装 `build/libs/ecopt-1.20.1.2.jar`。带 `-slim` 后缀的 jar 不含 MixinExtras，不用于独立安装。

## 1.20.1 Forge 移植

构建：ForgeGradle 6 + MixinGradle + Librarian 1.2.0，Parchment `2023.09.03-1.20.1`，Forge `47.4.22`，Java 17，元数据用 `src/main/templates/META-INF/mods.toml`。

原先停用的实体碰撞查询和 `noCollision` 已恢复。9 个 mixin 保留全部优化路径：原本两个接口 mixin 的行为合并到 `LevelMixin`。

关键适配：

* **事件系统**：改用 `net.minecraftforge.event.TickEvent.ServerTickEvent` / `ClientTickEvent`（判断 `Phase.END`）、`net.minecraftforge.eventbus.api.SubscribeEvent`、`net.minecraftforge.fml.common.Mod.EventBusSubscriber`，主类 `@Mod` 来自 `net.minecraftforge.fml.common.Mod`；
* **接口限制**：`LevelMixin implements LevelAccessor` 并合并具体覆盖方法，ForgeGradle 根据接口继承关系将新增方法重映射为 SRG 名称；不使用接口注入器，也不压制对应编译诊断；
* **原版回退**：不从 mixin 调用 `接口.super`。Mixin 0.8.5 会将该调用错误改写成类方法引用，运行时触发 `IncompatibleClassChangeError`；回退由普通逻辑类执行原版等价查询；
* **流体扫描**：注入 Forge 新增的 `Predicate<FluidState>` 重载，覆盖原版入口、乘船入口及模组直接调用，使用 `remap = false`；
* **反射分类**：通过 Forge 的 SRG 名称映射获取 `canBeCollidedWith`、`isPushable`、`canCollideWith` 的运行时名称，开发和生产环境都保留候选筛选；
* **访问器与 refmap**：字段和内部方法通过 `@Accessor` / `@Invoker` 访问，生成的 refmap 同时提供给开发运行与发布 jar；
* **运行目录**：客户端、服务端、GameTest 和主界面验收各自使用独立的 `run/*-1.20.1` 目录，避免加载旧 1.21.1 模组。

## 验证

* `gradlew runGameTestServer`：7 项世界内集成测试，覆盖分类、碰撞接管与船回退、推挤及同点包装过滤、缓存/流体失效、子格索引、支撑方块及流体扫描；
* `gradlew runClientSmoke`：启动真实客户端，等待加载完成且主界面渲染至少 10 秒、60 帧，保存 `run/client-verification-1.20.1/smoke-results/title-screen.png` 与 `client-pass.txt`，然后正常退出；
* 测试模组在 `src/smokeTest`，不包含在发布 jar 内。上述验收不会操作鼠标键盘。

## 许可证

[MIT](LICENSE) © 2026 FirstSight
