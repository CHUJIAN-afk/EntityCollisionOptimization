# 1.20.1 Forge 移植验收

日期：2026-10-15。分支：`1.20.1-forge`。

环境：Minecraft 1.20.1、Forge 47.4.22、Mixin 0.8.5、Parchment 2023.09.03-1.20.1、Librarian 1.2.0、Temurin JDK 17.0.19、Gradle 8.8。

## 编译与打包

`gradlew build` 成功，产物为 `build/libs/ecopt-1.20.1.1.jar`。

已检查发布 jar：

- 没有 MixinExtras 类、嵌套 jar 或 JarJar 元数据。
- 没有 `src/smokeTest` 的测试代码。
- 包含 9 个 mixin、唯一一份 `ecopt.refmap.json` 和 `MixinConfigs` 清单项。
- `LevelMixin` 新增覆盖方法在 reobf 后分别为 `m_183134_`、`m_45756_`。
- 类过滤反射通过 Forge 的运行时映射解析 SRG 方法名。

## 世界内测试

`gradlew runGameTestServer` 成功，最终日志为 `All 7 required tests passed :)`，ERROR/FATAL 为 0：

1. Parchment 环境下的硬实体、可推挤实体及自定义碰撞规则分类。
2. Level 碰撞接管、惰性形状列表、实体移除、船对猪的碰撞回退。
3. 推挤候选与原版查询结果比较，并通过实体正常 tick 执行推挤路径。
4. 方块缓存同 tick 写入失效、跨 tick 读取、流体缓存及方块计数重算失效。
5. 300 个固定种子查询框对比原版 `noCollision`，覆盖整方块、栅栏、上下半砖、空气和水。
6. 段内线性/子格索引结果一致、去重、增量候选表与删除后立即失效。
7. 支撑方块阻止竖直下落，以及干燥/含水区段的流体扫描。

日志：`run/gametest-1.20.1/logs/latest.log`。

## 客户端

`gradlew runClientSmoke` 成功。测试在真实客户端加载完成、无加载遮罩、`TitleScreen` 持续渲染 10 秒后，通过游戏帧缓冲保存截图，随后正常退出。全程未使用界面操作工具。

- 结果：`PASS TitleScreen rendered for >=10 seconds; frames=598`。
- ERROR/FATAL：0。
- 截图：`run/client-verification-1.20.1/smoke-results/title-screen.png`。
- 结果文件：`run/client-verification-1.20.1/smoke-results/client-pass.txt`。
- 日志：`run/client-verification-1.20.1/logs/latest.log`。

日志仍包含 Forge 开发环境的资源/音效/着色器 WARN，以及离线开发账号的 Realms 授权 INFO；没有模组加载、Mixin 或游戏运行错误。

本次验证使用 ForgeGradle 开发客户端和 GameTest；发布 jar 进行了结构与 SRG 字节码检查。其它优化模组组合及性能倍率未在 1.20.1 上重新测量。
