# P5 验证报告：端口统一 48790 + 旧插件退役 + 文档 + 最终回归

> 日期：2026-09-22
> 被测产物：`build/libs/rosetta_remote_debug_bridge-1.0.0.jar`
> SHA256：`2299BC4A6620323961BF28921E5F0A55195DA8D729B74AFA852C1A4494F71185`（构建产物与部署到 `run-fast/mods` 的 jar 哈希一致）
> 代码提交：`b4d3013`（"P5: unify bridge default port to 48790 (old RosettaRemote plugin port)"）
> 环境：`projects/mohist/run-fast`（Mohist 1.20.1-ecf8d5ad / Forge 47.4.13 / JDK 17）
> 桥：`127.0.0.1:48790`（token 文件 `run-fast/RosettaRemoteDebugBridge/remote-token.txt`）
> 结论：**端口统一 / 旧插件退役 / 全量回归 / 无 Bukkit 审查 / 动态 Mixin 降级确认 全部通过**；纯 Forge 服务端为审查+独立探针，明确标记“未实测”。

---

## 1. 本次改动

| 文件 | 改动 |
|---|---|
| `src/main/java/com/rosetta/remotedebugbridge/net/RemoteBridge.java` | `DEFAULT_PORT` 48791 → **48790**（保留 `-Drosetta.remote.port` 覆盖），类注释同步 |
| `docs/USAGE.md`（新增） | 中文使用手册：安装/升级、命令手册（14 个 cmd + Python 客户端）、Bukkit 适配语义、安全、已知坑、验收索引与回滚 |
| `run-fast/plugins/RosettaRemote.jar` | 移入 `run-fast/plugins-disabled/RosettaRemote.jar`（备份，未删除；SHA256 `ED3E9A6A2528EBA503AE3BC34C8BE9D1ECA047E7098721CDBEE165893B6B3148`） |

构建（约定命令）：

```
$ $env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot'
$ .\gradlew.bat build --console=plain
...
BUILD SUCCESSFUL in 36s
12 actionable tasks: 8 executed, 4 up-to-date
```

产物内常量核验（reobf 后 jar）：

```
$ javap -p -constants -classpath build/libs/rosetta_remote_debug_bridge-1.0.0.jar com.rosetta.remotedebugbridge.net.RemoteBridge
public static final int DEFAULT_PORT = 48790;
```

---

## 2. 端口统一（48790 = mod 桥，48791 退役）

### 2.1 变更前（旧状）

```
$ netstat -ano | findstr 4879
TCP    0.0.0.0:48790     0.0.0.0:0    LISTENING  21204   <- 旧 RosettaRemote 插件（全接口）
TCP    127.0.0.1:48791   0.0.0.0:0    LISTENING  21204   <- mod 桥

$ python legacy/tools/rosetta_remote.py --port 48790 --token D8k... ping
{"server":"Mohist 1.20.1-ecf8d5ad (MC: 1.20.1)","java":"17.0.18","players":0,"plugins":4,"remote":"127.0.0.1"}

$ python ... --port 48791 --token <mod token> ping
{"server":"127.0.0.1:48791","bridge":"rosetta-nexus rosetta_remote_debug_bridge", ...}
```

### 2.2 变更后（现状）

```
$ netstat -ano | findstr LISTENING | findstr 4879
TCP    127.0.0.1:48790    0.0.0.0:0    LISTENING   25368   <- mod 桥，仅回环
（无 48791，无 0.0.0.0）

$ python legacy/tools/rosetta_remote.py --port 48790 --token <mod token> ping
{
  "server": "127.0.0.1:48790",
  "bridge": "rosetta-nexus rosetta_remote_debug_bridge",
  "java": "17.0.18",
  "bukkit": true,
  "adapter": "present=true tracked=1 selfCheck=true selfCheckInstances=1 selfCheckCancels=0 owner=com.mohistmc.plugins.Main",
  "players": 0,
  "plugins": 3,
  "running": true
}
```

启动日志（console2.log，GBK 编码）：

```
L1031: [18:29:38 WARN]: Remote bridge listening on 127.0.0.1:48790 - token auth enforced, loopback-only by default. ...
L1032: [18:29:38 INFO]: Bukkit runtime detected - adapter active (owner plugin: Mohist)
L1068: [18:29:38 INFO]: Bukkit adapter: active owner=Mohist
```

判定：`server` 字段指向 mod 桥标识（`bridge=rosetta-nexus ...`），48791 不再监听；监听地址只余回环。

---

## 3. 旧插件退役

```
$ Get-ChildItem run-fast\plugins
bStats  Coder  RosettaRemote(dir)  Coder-2.5.0-mohist-java17.jar  ForgeKit.jar
$ Get-ChildItem run-fast\plugins-disabled
RosettaRemote.jar   (SHA256 ED3E9A6A...)
```

回归可见性（走 48790 mod 桥）：

```
$ python ... console "plugins"
{"dispatched": true}
[18:30:11] [Server thread/INFO] [Console]: Plugins (3): Coder, ForgeKit, Mohist

$ python ... plugins
[{"name":"Coder","version":"2.5.0-mohist-java17","enabled":true},
 {"name":"ForgeKit","version":"1.0.1","enabled":true},
 {"name":"Mohist","version":"1.20.1","enabled":true}]
```

列表无 `RosettaRemote`，含 Coder/ForgeKit/Mohist。

---

## 4. 全量回归（全部走 48790）

| # | 命令 | 结果 | 原始输出 |
|---|---|---|---|
| 1 | `exec`（版本） | 通过 | `{"result":"1.20.1-ecf8d5ad (MC: 1.20.1)  [1ms]","class":"rosetta.remote.generated.RosettaTask5690632955000"}` |
| 2 | `console "plugins"` | 通过 | `Plugins (3): Coder, ForgeKit, Mohist`（无 RosettaRemote） |
| 3 | `coder {"action":"list"}` | 通过 | `{"plugin":"Coder","version":"2.5.0-mohist-java17","apiClass":"dev.codestuff.coder.api.CoderAPI","loader":"org.bukkit.plugin.java.PluginClassLoader@10c33","methods":133,"instance":true}` |
| 4 | `plugins` | 通过 | 见 §3（3 个插件，全 enabled） |
| 5 | `update ForgeKit` | 通过 | `{"unload":"disabled, listeners, commands:2, manager, loader-closed","loaded":"ForgeKit 1.0.1","enabled":true}` |
| 6 | `listener {"action":"status"}` | 通过 | `{"status":"present=true tracked=1 selfCheck=true selfCheckInstances=1 selfCheckCancels=0 owner=com.mohistmc.plugins.Main"}` |

补充演示（同端口，用于文档示例与额外覆盖）：

```
$ reflect org.bukkit.Bukkit getVersion server
{"result":"1.20.1-ecf8d5ad (MC: 1.20.1)"}
$ disable ForgeKit -> {"name":"ForgeKit","enabled":false}
$ enable  ForgeKit -> {"name":"ForgeKit","enabled":true}
$ coder {"action":"api","method":"getMinecraftVersion"}
{"method":"getMinecraftVersion","returns":"java.lang.String","result":"1.20.1-ecf8d5ad (MC: 1.20.1)","ms":0}
$ upload / read / ls / tail   全部返回正常（示例见 docs/USAGE.md §3.5-3.7）
```

热更新服务端日志（console2.log）：

```
L1107: [18:30:34 INFO]: connection from 127.0.0.1
L1108: [18:30:34 INFO]: 127.0.0.1 -> update
L1109: [18:30:34 INFO]: [ForgeKit] ... ForgeKit v1.0.1 ...卸载完成
L1110: [18:30:34 INFO]: [ForgeKit] [ForgeKit] Forge hooks unregistered (hot unload clean)
L1111: [18:30:35 INFO]: ... ForgeKit v1.0.1 已启用
L1112: [18:30:35 INFO]: [ForgeKit] [ForgeKit] Forge runtime bus hooked: EntityJoinLevelEvent (guard=off) [build v1.0.1 hot-update proof]
```

审计行 `connection from` / `127.0.0.1 -> <cmd>` / `disconnected` 正常出现（一请求一连接）。

---

## 5. 纯 Forge（无 Bukkit）路径审查 + 独立探针

审查范围：`com.rosetta.remotedebugbridge.net.*` 与启动链路。

- 除 `NexusTask` 接口签名含 `org.bukkit.plugin.Plugin` 外，全部 Bukkit 访问均为 `Reflect.load` 反射；无硬 import。
- `BukkitAdapter.present()` 捕获 `Throwable`；`init()` 在无 Bukkit 时返回 `absent`。
- `exec` 仅在 Bukkit 存在时生成 `implements NexusTask` 源码；`console` 回退原版分发器、`plugins` 回退 `ModList`。
- `update`/`enable`/`disable` 明确抛 "Bukkit is not present on this server"，不崩溃。

无 Bukkit classpath 的独立探针（mod jar + log4j + gson，真实运行）：

```
[probe] no org.bukkit on classpath: true
[probe] present=false
[probe] describe=present=false tracked=0 selfCheck=false selfCheckInstances=0 selfCheckCancels=0 owner=none
[probe] init=absent
[probe] NexusTask loaded=com.rosetta.remotedebugbridge.net.NexusTask iface=true
[probe] NexusTask getMethods failed (expected, lazy resolution): java.lang.NoClassDefFoundError: org/bukkit/plugin/Plugin
```

说明：`NexusTask` 仅在 Bukkit 存在时被生成代码使用；懒解析失败不影响任何纯 Forge 路径。
**未在真实纯 Forge 服务端实测（未实测）；搭服成本超出本轮 20 分钟预算，结论以审查+探针为准。**

---

## 6. 动态 Mixin 降级路径确认

启动日志仍存在降级警告（console2.log L77）：

```
[18:29:10 WARN]: [STARTUP] Dynamic Mixin pipeline unavailable: the relocated Mixin service is not active in this environment (rosetta_remote_debug_bridge-core agent missing or disabled). Scripts and events are unaffected.
```

代码路径：`JavaScriptLoader`（运行时探测失败即警告并禁用动态 Mixin）、`DynamicMixinLoader.loadDynamicMixins()`（`MixinProcessorHolder.getInstance()==null` 时警告并跳过）、`ClassReplacementManager`（提示需要 agent/class-transformer）。
静态 Mixin 不受影响（本次启动 L40 `Mixing ChickenAiStepProbeMixin ... into ...Chicken`）。
**标注：动态 Mixin 依赖外部 agent，当前降级。**

---

## 7. 文档产物

`docs/USAGE.md`（中文）覆盖：安装/升级（mods 放置、端口/token/-D 覆盖、token 文件）、端口与线协议、Python 客户端用法、14 个命令（ping/console/exec/reflect/upload/read/tail/ls/plugins/enable/disable/update/listener/coder）的 args 与返回示例、Bukkit 适配（ServerAPI 监听、commandMap 命令、热更新语义、cleanup/restore、Coder 协作）、纯 Forge 说明、安全（token/回环/SSH 隧道/调试后卸载）、已知坑（module-info 排除、Mixin AP 默认关、脚本清理、predicates 复数+BOM、Mohist `/reload` 不重载数据包、脚本命令 reload 清理等）、验收索引与回滚步骤。

---

## 8. 遗留 / 未实测项

- 纯 Forge 真实服务端未实测（§5，审查+探针通过）。
- 动态 Mixin 依赖外部 agent，当前降级（§6）。
- `update` 后插件顺序可能变化（`plugins` 数组顺序按管理器内部顺序），不影响功能。
- 旧插件数据目录 `run-fast/plugins/RosettaRemote/` 保留未动（仅 jar 退役）；回滚方式见 `docs/USAGE.md` §7。
- 客户端 `legacy/tools/rosetta_remote.py` 没有 `coder`/`listener` 子命令，手册给出 `rr.call(...)` 原始调用方式（未改客户端）。

## 9. 服务器最终状态

- `run-fast` 保持运行；`127.0.0.1:48790` 由 mod 桥监听（PID 25368），回环限定。
- `mods/rosetta_remote_debug_bridge-1.0.0.jar` = 本次构建产物（哈希一致，§1）。
- 测试文件 `run-fast/RosettaRemoteDebugBridge/server/p5_upload_probe.txt` 为上传探针产物，留在测试服运行目录（不进入仓库）。
