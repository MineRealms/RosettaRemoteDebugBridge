# P0 验证报告：Forge mod → Mohist/Bukkit 跨层可行性

> 日期：2026-09-22
> 环境：`run-fast` 测试服（Mohist 1.20.1-ecf8d5ad / Forge 47.4.13 / JDK 17，无其他 mod）
> 被测产物：`rosetta_remote_debug_bridge-1.0.0.jar`（本仓库构建，含本次两处修复）
> 结论：**4/4 验证项全部通过**。融合方案（mod 打底 + Mohist 挂载 Bukkit）成立。

---

## 1. 验证结果总览

| # | 验证项 | 结果 | 证据 |
|---|---|---|---|
| 1 | DebugBridge 生产 jar 能在 Mohist 加载 | ✅ | `[Rosetta/Network] Channel ... initialized`、`Using Eclipse JDT compiler`、`Compiled 1/1 file(s) successfully` |
| 2 | mod 可见 Mohist/Bukkit 类 | ✅ | `ServerAPI=OK nmsServer=true hasMod(forge)=true`；`ownerPlugin=Mohist` |
| 3 | mod 注册 Bukkit 监听器并实际生效 | ✅ | `putBukkitEvents=OK`；`/summon creeper` → `Bukkit EntitySpawnEvent CANCELLED a creeper spawn` |
| 4 | mod 注册 Bukkit 命令并执行 | ✅ | `commandMap.register=OK`；`/nprobe` → `NexusProbe command OK: 1.20.1 / java 17.0.18` |
| 附加 | 脚本热重载（`/java reload server`） | ✅ | 重载后探针自动重跑并输出新结果，无重启 |

探针最终输出：

```
[NexusProbe] ServerAPI=OK nmsServer=true hasMod(forge)=true;
             ownerPlugin=Mohist;
             putBukkitEvents=OK;
             commandMap.register=OK;
```

---

## 2. 本次修复（源码已改，待提交）

| 文件 | 问题 | 修复 |
|---|---|---|
| `build.gradle` | Mixin 分支 jar 自带的 `module-info.class` 随 `zipTree` 打进产物，其中 `provides ITransformationService with MixinTransformationService` 与 Forge 自带服务重名 → 启动即崩 `Duplicate key mixin` | `processResources { exclude 'module-info.class' }`（永久修复；修复后 jar 无 module-info） |
| `JavaSourceCompiler.java` | 编译选项硬编码 Mixin 注解处理器；普通服务端运行时 classpath 没有 `org.spongepowered.tools.*` → ECJ 内部错误 `ClassNotFoundException`，所有脚本编译失败 | 改为可选：默认 `-proc:none`；需要脚本级 Mixin AP 时用 `-Drosetta.mixin.annotationProcessors=true` 开启 |

## 3. 认知修正（重要）

**`MohistPlugin.registerCommands(Map)` 不是"注册命令"API**。字节码显示它是把 Mohist 内置命令
（`worlds/warps/tpa/...`）**写入传入的 Map**（收集器语义）；传入不可变 `Map.of(...)` 会抛
`UnsupportedOperationException`（被包装成 `InvocationTargetException`）。

mod 注册 Bukkit 命令的正确方式（已验证）：

```java
Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
field.setAccessible(true);
CommandMap commandMap = (CommandMap) field.get(Bukkit.getServer());
commandMap.register("rosetta", myBukkitCommand);   // 与 Bukkit 插件同款路径
```

## 4. 遗留事项（带入 P1/P2）

1. **脚本注册的 Bukkit 监听器不会随 `/java reload` 反注册**：探针重载后出现重复监听（creeper 被取消两次）。
   Nexus 的 bukkit 适配层必须按脚本类加载器跟踪 `Listener` 实例，并在 reload 时
   `HandlerList.unregisterAll(listener)`（RosettaRemote 的 PluginReloader 已有同款模式）。
2. `/nprobe` 走 `commandMap.register("rosetta", ...)`，命令全名为 `/rosetta:nprobe` 且 `/nprobe` 作为 fallback 可用；
   Nexus 正式命令应统一用命名空间前缀避免冲突。
3. 本次仅覆盖 SERVER 脚本阶段；STARTUP/CLIENT 与资源/数据包注入待 P1 一并验证。

## 5. 对实施计划的影响

- P0 最大风险（跨层可见性）已消除，P1（Nexus 骨架：core+net 合并）**可以开工**；
- §4.1 的监听器生命周期问题应作为 P2 的硬性验收项；
- `module-info` 与 AP 两处修复建议回灌到上游构建模板（今后所有含 Mixin 分支的 mod 构建都要 `exclude 'module-info.class'`）。
