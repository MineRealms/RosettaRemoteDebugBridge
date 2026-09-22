# RosettaNexus 实施计划（融合 RosettaRemoteDebugBridge × RosettaRemote）

> 配套分析：`FUSION-ANALYSIS.md`
> 目标：**单一 Forge mod 产物**，在 Mohist 上自动挂载 Bukkit 内容，保留远程 TCP 控制面与插件热更新，
> 同时获得构造期能力（静态 Mixin / 注册表 / Capability）与 ECJ 无 JDK 脚本执行。
> 环境：MC 1.20.1 / Forge 47.4.13 / Java 17 / Mohist。

---

## 0. 原则

1. **先验证、后重构**：P0 用现成产物验证跨层可行性，不写一行新架构代码。
2. **协议不变**：`tools/rosetta_remote.py` 与 JSON 协议保持兼容，客户端零改动。
3. **分层隔离**：`net` / `core` 不依赖 Bukkit；`bukkit` 适配层仅在检测到 Mohist 时激活。
4. **可降级**：动态 Mixin / agent 失败不崩服（沿用 DebugBridge 的降级策略）。
5. **每阶段可交付、可冒烟**：每步都有明确通过标准，跑在 run-fast 测试服上。

---

## 1. 阶段总览

| 阶段 | 目标 | 产物 | 验收 |
|---|---|---|---|
| P0 | 跨层可行性验证（mod → Mohist/Bukkit） | 验证报告 + 探针脚本 | 4 项全绿（见 §2） |
| P1 | Nexus 骨架：core+net 合并为 mod | `RosettaNexus-1.20.1.jar` | 远程 `ping` / `exec`（ECJ）可用 |
| P2 | Bukkit 适配层（Mohist） | bukkit 模块 | mod 注册监听/命令 + 插件热更新 `update` |
| P3 | 脚本引擎整合 + CoderAdapter | script 桥接 | 三阶段脚本、热重载、调 CoderAPI |
| P4 | 静态 Mixin 接入 + 资源/数据包注入 | mixin 模块 | Tenet/Rosetta mixin 生成物在 mod 内生效 |
| P5 | 动态 Mixin（agent，可选）+ plugin-only profile + 测试/文档 | 双产物 | 全自动冒烟 + 纯 Bukkit 精简版 |

---

## 2. P0：跨层可行性验证（当前阶段）

### 验证项

| # | 验证 | 通过标准 |
|---|---|---|
| 1 | DebugBridge 生产 jar 能在 Mohist 加载 | 启动日志出现 mod 加载，无崩溃 |
| 2 | mod 可见 Mohist/Bukkit 类 | 脚本成功调用 `ServerAPI.getNMSServer()` / `Bukkit.getPluginManager().getPlugin("Mohist")` |
| 3 | mod 注册 Bukkit 监听器 | `ServerAPI.putBukkitEvents` 成功后，`/summon creeper` 被监听器取消 |
| 4 | mod 注册 Bukkit 命令 | `MohistPlugin.registerCommands` 注册的 `/nprobe` 可执行并回显 |

### 执行步骤（run-fast 测试服）

1. 构建/取用 `RosettaRemoteDebugBridge` 的 reobf 产物 jar；
2. 放入 `projects/mohist/run-fast/mods/`；
3. 在 `run-fast/RosettaRemoteDebugBridge/server/Probe.java` 放置探针脚本（调用上表 2/3/4）；
4. 重启 run-fast（可见窗口），检查：
   - `console2.log`：mod 加载 + 无 ERROR；
   - `logs/Rosetta/server.log`：`[NexusProbe] ...` 输出；
   - 用 RosettaRemote 桥：`console "summon minecraft:creeper 0 100 0"`（验证 #3）；
     `console "nprobe"`（验证 #4）。
5. 结果写入 `P0-VERIFICATION.md`（放本目录），任何一项失败都要记录失败模式与后续对策。

### P0 失败分支

- **jar 加载失败**：检查 reobf/映射/依赖（JFrog 打包），必要时先用 dev 模式的 `runServer` 产物；
- **类不可见**：改"mod 反射调用（Class.forName 走 Mohist 类路径）"或"mod 释放插件到 plugins/ 再由桥 loadPlugin"方案；
- **监听/命令注册失败**：检查 owner Plugin（`MohistPlugin.plugin`）状态与注册时机（改到 `MohistStartDoneEvent` 之后）。

---

## 3. P1：Nexus 骨架

- 工程：ForgeGradle 6（沿用 DebugBridge 的构建配置），modId `rosetta_nexus`；
- 合并：
  - `core`：ECJ 编译器 + mappings.tsrg + MinecraftHelper + ScriptErrorCollector（来自 DebugBridge）；
  - `net`：TCP JSON 服务 + 命令分发（来自 RosettaRemote），`exec` 改用 ECJ，去掉 JDK javac 依赖；
- 交付命令面：`ping / console / exec / reflect / upload / read / tail / ls`；
- 验收：
  1. `python tools/rosetta_remote.py ping` 通；
  2. `exec` 脚本写官方名（如 `ItemStack.EMPTY`），生产 SRG 环境自动转换并执行成功；
  3. 不带 JDK 的 JRE 也能跑（ECJ 自包含）。

## 4. P2：Bukkit 适配层（Mohist）

- `BukkitAdapter`：环境探测（`Class.forName("com.mohistmc.MohistMC")`）；
- 监听注册封装：`ServerAPI.putBukkitEvents(listener, MohistPlugin.plugin)`；
- 命令注册封装：`MohistPlugin.registerCommands(Map)`；
- 移植 `PluginReloader`（disable→反注册→命令/权限清理→管理器摘除→关类加载器→清 JarFile 缓存→写 jar→重载）；
- 新增命令：`update <name> <file> <base64>`（同 RosettaRemote，已实测有效）、`plugins`、`enable/disable`；
- 验收：ForgeKit/Coder 原地热更新在 mod 层复现通过。

## 5. P3：脚本引擎整合 + CoderAdapter

- 三阶段脚本（startup/server/client）+ `/nexus script reload|errors`；
- `CoderAdapter`：跨类加载器调用 `CoderAPI`（已验证 124 方法可达），提供：
  - `coder run <script>` / `coder api <method> [args]`（桥命令）；
- 验收：桥命令驱动 Coder 跑 Java/Python 脚本成功；`/nexus script reload` 后监听器数量稳定。

## 6. P4：静态 Mixin 接入 + 资源注入

- 把 Tenet/Rosetta 的 mixin 生成物（SRG 注解 + MCP 方法体）打包进 mod 的 mixin 配置；
- 资源/数据包注入（assets/data，实时读盘）保留自 DebugBridge；
- 验收：`rosetta.mixins.json` 在 Mohist 生产模式全部应用（对齐 w1-pilot 的 16/16 指标）。

## 7. P5：增强与双产物

- 动态 Mixin（agent 可选，失败降级）；`plugin-only` 构建 profile（纯 Bukkit 服务器）；
- 自动化冒烟：复用 `autotest/` 思路 + RosettaRemote 协议驱动；
- 文档：使用说明、命令手册、部署与安全（token/防火墙）。

---

## 8. 里程碑与验收口径

- P0 通过 = 融合方案成立（最大技术风险清除）；
- P1 通过 = 远程面在 mod 形态复活，且摆脱 JDK 依赖；
- P2 通过 = "mod 挂 Bukkit 内容" 全链路成立（监听/命令/插件管理）；
- P3 通过 = Coder 与脚本引擎双轨协同；
- P4/P5 通过 = 构造期能力（Mixin/注册表）与发布形态完善。

## 9. 安全与合规

- TCP 桥 = 任意代码执行：token + 防火墙/SSH 隧道；调试后卸载；
- 许可：DebugBridge（MIT，保留 Rain 署名）与 Rosetta（GPL-3.0）兼容合并；
- 上游命名空间 `net.rain.repack.*` / `org.spongepowered.rain.asm` 保持不动。
