# 结构：接入点与逻辑

## mixin 接入点（只做转发，逻辑不写在里面）

| 接入点 | 目的方法 | 转发到 |
| --- | --- | --- |
| `ClassInstanceMultiMapMixin` | `iterator()` / `find(Class)` | 直接返回底层 ArrayList（去掉 Guava / Collections 包装） |
| `EntitySectionMixin` | `getEntities(AABB, consumer)` | 段内子格索引（自持逻辑，见下） |
| `EntitySectionStorageMixin` | `createSection(long)` | 把段坐标写进 `EntitySection`（`SectionPosHolder`） |
| `EntityGetterMixin` | `getEntityCollisions` | `EcoptCollisions.query` |
| `LivingEntityMixin` | `pushEntities` 里的 `getEntities` | `EcoptPush.query` |
| `CollisionGetterMixin` | `noCollision` | `EcoptNoCollision.test` |
| `LevelChunkMixin` | `getBlockState(BlockPos)` | `EcoptBlockCache.getBlockState` |
| `LevelChunkSectionMixin` | `setBlockState` / `read` / `recalcBlockCounts` | 推进段版本号；缓存段调色板的流体存在性 |
| `LevelEntityGetterAdapterMixin` | 无（存储访问） | 暴露 `EntitySectionStorage` |
| `LevelMixin` | 无（逻辑宿主） | 按查询框遍历可访问实体段（`EcoptLevelIndex`），取段内增量候选表 |
| `EntityMixin` | `collide` / `updateFluidHeightAndDoFluidPushing()V` | 支撑方块捷径与干燥区段流体扫描捷径（自持逻辑） |

## 逻辑运行类

| 类 | 作用 |
| --- | --- |
| `EcoptTicks` | 全局 tick 序号 / 查询 id |
| `EcoptClassFilters` | 按类判断"是否重写了 canBeCollidedWith / isPushable"（`ClassValue` 缓存） |
| `EcoptBlockCache` | 区块级方块状态缓存（当前段引用 + 段版本号校验，跨 tick 复用） |
| `EcoptCollisions` | 碰撞查询候选表 → 惰性形状列表 |
| `EcoptPush` | 推挤查询候选表 |
| `EcoptNoCollision` | 三重循环方块扫描版 `noCollision` |
| `EcoptShapeList` | 惰性 `VoxelShape` 列表（只判空时不建 shape） |
| `event/CommonEvents` / `event/ClientEvents` | 推进 tick 序号 |
| `event/TestEvent` | 造堆积负载 → spark 采样 → 写结果 → 关游戏 |
| `event/ClientShutdown` | 客户端侧关闭游戏 |

## 运行逻辑宿主（逻辑与接入点同文件）

* `EntitySectionMixin`：每格边长 2，段内共 8³ 格，按 `x | y << 3 | z << 6` 编码；实体按包围盒覆盖的全部子格归档，
  查询按实体时间戳去重，`add`/`remove` 立即失效，同 tick 查 ≥2 次才建索引。
* `EntityMixin`：纯竖直下落时先探足迹中心列，被完全挡住就直接返回零位移，原版完整立方体直接判 AABB 边界；
  流体更新前遍历收缩包围盒覆盖的段，只有所有段的调色板均无流体时才取消扫描。
* `LevelChunkSectionMixin`：流体存在性按 tick 与段版本缓存；段写入、网络数据读取、重算方块计数时推进版本。
