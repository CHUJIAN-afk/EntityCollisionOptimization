# Project name

Entity Collision Optimization

中文名称：实体碰撞优化

简称：ECOpt

# Summary

Optimizes entity collision checks, pushing queries, and nearby entity lookups to reduce overhead in entity-heavy areas.

# Description

**Entity Collision Optimization (ECOpt)** reduces the cost of entity collision checks and nearby entity queries, helping with workloads involving crowded mobs and large numbers of dropped items.

It maintains collision and pushing candidate lists, uses spatial indexing for repeated small-area queries, and caches block states to reduce repeated lookups. Additional fast paths reduce work in collision checks and vertical movement.

All optimizations are enabled automatically. No configuration is required.

- **Platform:** Minecraft 1.21.1 / NeoForge
- **License:** MIT

Performance gains depend on entity counts, world conditions, and the other mods installed.

# 中文简介

**实体碰撞优化（ECOpt）** 专注于减少实体碰撞检测、推挤查询和附近实体检索的计算开销，适用于生物密集、掉落物堆积等场景。

模组通过维护碰撞与推挤候选列表、为空间查询建立索引，以及缓存方块状态，减少重复遍历和读取；同时优化部分碰撞检测和竖直移动路径。

安装后自动生效，无需配置。适用于 **Minecraft 1.21.1 / NeoForge**，采用 **MIT** 许可证。实际性能收益随实体数量、世界环境和模组组合而变化。

# Cover

`ecopt-cover-400.png` — 400 × 400 RGB PNG.

Original geometry drawn with Pillow, not AI image generation. Concept: two isometric entity volumes, spatial grid, and an amber acceleration bolt; dark blue, mint, and amber palette; ECOPT lettering.
