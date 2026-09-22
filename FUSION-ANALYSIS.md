# RosettaRemoteDebugBridge × RosettaRemote 融合分析报告

> 结论先行：**可行，且是天作之合**。两份代码互补性极强——一个有"构造期能力 + ECJ 内存编译器 + 映射转换 + 脚本引擎"，
> 一个有"生产验证过的远程控制面 + 插件热更新 + 跨类加载器调用"。融合目标定位：
> **一个 Forge mod 打底、在 Mohist 上挂载 Bukkit 内容的"混合超级调试桥"**。
> 目标环境完全对齐：两边都是 MC 1.20.1 / Java 17（RosettaRemoteDebugBridge 是 Forge 47.x，Mohist 是 47.4.13）。

---

## 1. 两个项目的现状对比

| 维度 | RosettaRemoteDebugBridge（Forge mod） | RosettaRemote（Bukkit 插件） |
|---|---|---|
| 形态 | Forge mod（ForgeGradle 6 / 8.7MB 自包含） | Bukkit 插件 + Python 客户端 |
| 执行能力 | **ECJ 内存编译**（无需系统 JDK）+ 启动/开服/客户端三阶段脚本 | **JDK javac** 运行时编译 exec（需要 JDK） |
| 命名适配 | **内置 mappings.tsrg + MCP→SRG 源码转换**（生产 SRG 服务器可直接用官方名写脚本） | 无（反射调用要记 `m_xxxxx_`） |
| 生命周期 | 构造期（静态块）→ common/client setup → server starting | 服务器运行中（onEnable 之后） |
| 事件 | 脚本事件总线 + Forge 事件桥（MOD 6 + FORGE 191） | Bukkit 事件（探针用）+ Forge lambda（验证可行） |
| 热重载 | 脚本热重载（`/java reload`，监听器按类加载器反注册） | **插件级原地热更新**（PluginReloader：清 HandlerList/命令表/管理器/类加载器/JarFile 缓存） |
| 远程面 | 无网络控制面（只有游戏内 `/java` 命令） | **TCP JSON + Token + Python 客户端**（console/exec/reflect/upload/read/tail/update） |
| Mixin | 动态 Mixin 管线（需 agent，可降级） | 无 |
| 生产验证 | 报告自述"生产 reobf jar 尚未实测" | 已在本机 Mohist 1.20.1 实测（Coder 冒烟、ForgeKit 热更新） |

**互补点**：
- RosettaRemote 缺的（无 JDK、SRG 命名、构造期能力、Mixin）→ RosettaRemoteDebugBridge 全有；
- RosettaRemoteDebugBridge 缺的（远程控制面、插件管理、Bukkit 内容）→ RosettaRemote 全有。

---

## 2. 关键技术可行性：Mohist 允许 Forge mod 挂 Bukkit 内容吗？

**允许，Mohist 有明确的混合 API 面**（以下为对 `mohist-1.20.1-47.4.13-universal.jar` 的反汇编证据）：

| API | 签名 | 用途 |
|---|---|---|
| `com.mohistmc.api.ServerAPI` | `putBukkitEvents(Listener, Plugin)` | **注册 Bukkit 事件监听**（mod 的 Listener 实例 + owner Plugin） |
| | `getNMSServer()` / `hasMod(String)` / `hasPlugin(String)` / `getModSize()` | 运行期信息 |
| `com.mohistmc.plugins.MohistPlugin` | `public static Plugin plugin;` | 可直接作为 owner Plugin（Mohist 内置插件实例，`/plugins` 里名为 `Mohist`） |
| | `registerCommands(Map<String, Command>)` | **注册 Bukkit 命令** |
| `com.mohistmc.api.event.*` | `MohistStartDoneEvent`、`BukkitHookForgeEvent`、`MohistServerListPingEvent` 等 | 开服完成钩子 / 双向事件桥 |
| `com.mohistmc.api` | `PlayerAPI/ItemAPI/WorldAPI/InventoryAPI/EntityAPI/...` | 混合域 API |

此外，Forge mod 天然拥有构造期能力（这些恰恰是 Bukkit 插件永远做不到的）：
- 静态 Mixin（`rosetta.mixins.json`，Mohist 自身就用 Mixin 装载，我们 Tenet 项目已有完整 Mixin 子系统经验）；
- `RegisterCommandsEvent`（Brigadier 命令）、`RegisterEvent/RegistryEvent`、Capability 注册、配置加载、数据包/世界生成注入。

**类加载器事实**（本会话实测结论）：
- Bukkit 插件调用 Forge API：✅ 可用（`MinecraftForge.EVENT_BUS` 直接 import；但 `@SubscribeEvent` 对象注册不可用，必须 EventBus 6 lambda）；
- 插件 → 其他插件 API：❌ 默认不可见，需经 `对方插件.getClassLoader().loadClass()` 反射（已实测调通 CoderAPI 124 个方法）；
- mod → Bukkit/Mohist API：预期 ✅（`com.mohistmc.api.*` 与 `org.bukkit.*` 同在游戏层类路径），**需一次启动验证**（见 §5 验证计划第 1 条）。

---

## 3. 融合架构：RosettaNexus（暂名）

```
RosettaNexus-1.20.1.jar   ← 单一 Forge mod 产物（Mohist 上自动启用 Bukkit 适配层）
│
├─ core/   【来自 RosettaRemoteDebugBridge】
│   ├─ ECJ 内存编译器（无 JDK）+ CustomFileManager 内存源修正
│   ├─ MCP→SRG 源码转换（mappings.tsrg 内置）+ MinecraftHelper 映射反射
│   ├─ 脚本引擎：startup/server/client 三阶段 + 热重载 + ScriptErrorCollector
│   ├─ 脚本事件总线（@RosettaSubscribeEvent）+ ForgeEventBridge（191 事件）
│   ├─ 资源/数据包注入（rosetta_assets / rosetta_data）
│   └─ 动态 Mixin 管线（agent 可选，降级安全）
│
├─ net/    【来自 RosettaRemote，协议保持兼容】
│   ├─ TCP JSON + Token（Python 客户端 tools/rosetta_remote.py 不用改）
│   └─ 命令面：ping/console/exec/reflect/upload/read/tail/ls
│       （exec 由 JDK javac 换成 ECJ + 映射转换 = 生产服无 JDK 也能跑）
│
├─ bukkit/  【Mohist 适配层，仅当检测到 com.mohistmc.MohistMC 时激活】
│   ├─ ServerAPI.putBukkitEvents(...)   → mod 内 Bukkit 监听器
│   ├─ MohistPlugin.registerCommands(...) → /nexus 系列 Bukkit 命令
│   ├─ PluginReloader（插件原地热更新：本轮已在 RosettaRemote 验证）
│   ├─ PluginManager 操作（load/enable/disable/update）
│   └─ CoderAdapter（跨类加载器调用 CoderAPI：已实测 124 方法可达）
│
└─ forge/  【原生 mod 能力，构造期】
    ├─ 静态 Mixin（与 Tenet/Rosetta 的 mixin 子系统对接）
    ├─ /nexus 命令（RegisterCommandsEvent, Brigadier）
    └─ 注册表/Capability/配置/数据包
```

**启动时序**：
```
mod 构造（静态 Mixin 生效、注册表窗口）
 → FMLCommonSetupEvent
 → ServerStartingEvent（执行 server/ 脚本）
 → MohistStartDoneEvent（检测到 Mohist）
     → 初始化 bukkit 适配层（注册监听/命令）
     → 启动 TCP 桥（端口与协议沿用 RosettaRemote，默认 48790）
```

**单一产物策略**：一个 jar 内同时含 `META-INF/mods.toml` 与 Bukkit 适配代码；非 Mohist 的纯 Forge 服自动跳过 bukkit 层；纯 Bukkit 服（Paper/Spigot）不能跑 mod，可保留一个从同一代码库精简出的 plugin-only 变体（构建 profile 切换）。

---

## 4. 融合后的能力矩阵（1+1+1 > 3）

| 场景 | 现在 | 融合后 |
|---|---|---|
| 远程执行 Java 脚本 | 需服务器装 JDK（javac） | ✅ ECJ 内存编译，无 JDK |
| 生产 SRG 服务器写脚本 | 要用 `m_xxxxx_` 或反射 | ✅ 写官方名，源码级自动转 SRG |
| 插件热更新（同文件） | ✅ RosettaRemote 已有 | ✅ 保留，且可在 mod 层做更彻底的清理 |
| 调用 Coder/其他插件 | 跨类加载器反射 | ✅ CoderAdapter 封装 |
| 注册 Bukkit 监听/命令（来自 mod） | ❌ 做不到 | ✅ ServerAPI.putBukkitEvents + registerCommands |
| 静态 Mixin / 注册表 / Capability | ❌ 插件做不到（构造期） | ✅ mod 天然支持 |
| 脚本三阶段 + 资源/数据包注入 | ❌ | ✅ 来自 RosettaRemoteDebugBridge |
| 动态 Mixin | ❌ | ✅ 管线保留，agent 可选增强 |
| 远程控制面 | ✅ | ✅ 协议不变，客户端零改动 |

---

## 5. 验证计划（在跑着的 run-fast 测试服上，零风险）

1. **跨层可见性验证**（最关键，先做）：写 20 行探针 mod，`onServerStarting` 里调用
   `Class.forName("com.mohistmc.api.ServerAPI")` + `Bukkit.getPluginManager().getPlugin("Mohist")`，
   输出到日志 → 确认 mod 类加载器能看到 Mohist/Bukkit。
2. **Bukkit 监听注册**：`ServerAPI.putBukkitEvents(listener, MohistPlugin.plugin)` 注册一个
   `EntityJoinLevelEvent`（Bukkit 侧）→ `/summon creeper` 验证拦截生效。
3. **Bukkit 命令注册**：`MohistPlugin.registerCommands(Map.of("nexus", cmd))` → 控制台 `/nexus ping`。
4. **ECJ exec**：桥的 `exec` 命令走 ECJ + SRG 转换，跑一段 `import org.bukkit.Bukkit; ...` 脚本。
5. **CoderAdapter**：复用本会话已验证的跨类加载器调用（`CoderAPI.getInstance()`）。
6. **PluginReloader 移植**：在 mod 层重复 ForgeKit 的原地更新测试（`update` 命令）。
7. **打包/生产形态**：reobf 后 jar 放入 run-fast 的 `mods/`，全流程从零启动复测一遍（含无 JDK 场景）。

每项都有明确的通过标准，失败点也能独立定位（mod 层 / bukkit 层 / 桥层分离）。

---

## 6. 风险与对策

| 风险 | 等级 | 对策 |
|---|---|---|
| mod 类加载器看不到 Bukkit/Mohist 类 | 中 | §5.1 先验证；不行则把 bukkit 适配层改为"mod 启动时用反射 `Class.forName`（走 Mohist 的类路径）"或分包成独立 Bukkit 插件由 mod 自动释放（mod 能在运行期写 plugins/ 并触发 loadPlugin——RosettaRemote 已验证该路径） |
| `ServerAPI.putBukkitEvents` owner 必须是启用的 Plugin | 低 | 用 `MohistPlugin.plugin`（内置 Mohist 插件）；失败则退化到动态注册一个内置插件壳 |
| RosettaRemoteDebugBridge 生产 reobf 未实测 | 中 | §5.7 专门验证；这正是融合的第一道关卡 |
| 动态 Mixin 需 agent | 低（可降级） | 静态 Mixin 走 mod；动态 Mixin 作为增强项，与 Tenet/Rosetta mixin 子系统对接 |
| 两边许可（MIT vs GPL-3.0/Tenet） | 低 | MIT 兼容 GPL；保留 Rain 作者署名与 MIT 声明 |
| 维护复杂度（两个世界的事件/线程模型） | 中 | 分层隔离：bukkit 层只在 Mohist 环境激活；net/core 不依赖 Bukkit；用接口做 Adapter |
| 与 Coder 功能重叠 | 低 | Coder 面向服主（游戏内脚本 + Web 编辑器 + Python/Lua）；Nexus 面向远程运维与开发（Java 脚本 + 混合能力），二者通过 CoderAdapter 协作而非合并 |

---

## 7. 分阶段路线图

| 阶段 | 内容 | 预估 |
|---|---|---|
| P0 | §5 全部验证项（探针 mod + 现有 run-fast） | 0.5-1 天 |
| P1 | Nexus 骨架：打包 RosettaRemoteDebugBridge 的 core + RosettaRemote 的 net，形成 mod 产物，`/nexus ping/exec` 跑通 | 2-3 天 |
| P2 | bukkit 适配层：监听/命令注册 + PluginReloader 移植 + `update` 命令 | 2-3 天 |
| P3 | 脚本引擎整合：三阶段 + 热重载接桥（`script reload` / `errors`）+ CoderAdapter | 2-3 天 |
| P4 | 静态 Mixin 接入 Tenet/Rosetta 生成物；资源/数据包注入 | 3-5 天 |
| P5 | 动态 Mixin agent（可选）、plugin-only 精简 profile、文档与自动化测试 | 1 周+ |

**建议**：先做 P0（今天就能在 run-fast 上看到结果），它以最小代价消除最大的未知数（跨层可见性）。

---

## 8. 与 RosettaRemote 现有资产的合并策略

- **保留**：`tools/rosetta_remote.py`（协议不变，客户端零改动）；`PluginReloader`；探针插件（PortalProbe）等独立诊断工具；
- **迁移**：`JavaExecutor`（javac）→ ECJ + 映射转换（core 模块）；TCP 服务端 → net 模块；
- **弃用**：RosettaRemote 的单独插件形态在 Nexus 稳定后归档（保留给纯 Bukkit 服务器）；
- **命名**：建议 modId `rosetta_nexus`，显示名 `Rosetta Nexus`，与 Tenet/Rosetta 主线命名一致；保留 `net.rain.repack.*` 等上游命名空间不动（依赖机制需要）。
