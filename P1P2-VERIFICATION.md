# P1+P2 验证报告：mod 内远程控制面 + Mohist Bukkit 适配层

> 日期：2026-09-22
> 被测产物：`build/libs/rosetta_remote_debug_bridge-1.0.0.jar`（commit 7ecebef）
> 环境：`projects/mohist/run-fast`（Mohist 1.20.1-ecf8d5ad / Forge 47.4.13 / JDK 17）
> 插件：Coder 2.5.0-mohist-java17、RosettaRemote 1.0.0、ForgeKit 1.0.1、Mohist 1.20.1
> 新桥：`127.0.0.1:48791`（默认 loopback；token 首次启动生成并落盘）
> 结论：**10/10 验收项全部通过**（附加命令面与安全边界亦逐项验证）。

---

## 0. 交付内容（新增包 `com.rosetta.remotedebugbridge.net`）

| 文件 | 作用 |
|---|---|
| `RemoteBridge.java` | TCP JSON 服务（独立 daemon 线程）、token 校验、命令分发、`onServerThread` 主线程调度 |
| `NexusTask.java` | exec 契约 `Object run(org.bukkit.plugin.Plugin, Object[])` |
| `BukkitAdapter.java` | 纯反射 Bukkit/Mohist 适配：console/plugins/启停/热更/监听器注册+按类加载器清理/自检监听器 |
| `PluginReloader.java` | 反射版原地热更新（disable→反注册→命令/权限清理→管理器摘除→关类加载器→清 JarFile 缓存） |
| `Reflect.java` | 反射调用辅助（方法/字段/多加载器查找） |
| `src/bukkitStubs/.../Plugin.java` | 仅编译期 stub（独立 source set，**不进 jar**） |

挂载点：`ServerStartedEvent → RemoteBridge.start(...)`（失败只记日志）；`ServerStoppingEvent → stop()`；
`RosettaCore.reload()` 调用 `BukkitAdapter.cleanupClassLoader(旧脚本类加载器)`。

配置（系统属性）：
- `-Drosetta.remote.port`（默认 48791）、`-Drosetta.remote.bind`（默认 127.0.0.1）
- `-Drosetta.remote.token`（缺省读 `RosettaRemoteDebugBridge/remote-token.txt`，无则随机生成并落盘+打印）
- `-Drosetta.remote.allowPathEscape=true`（默认 false，文件命令锁定服务器根目录）

---

## 1. 验收结果总览

| # | 验收项 | 结果 |
|---|---|---|
| 1 | build 成功且 jar 无 module-info | ✅ |
| 2 | 服务器带新 mod 正常启动，无 ERROR/崩溃 | ✅ |
| 3 | 48791 `ping` 返回服务器信息 | ✅ |
| 4 | `exec: return org.bukkit.Bukkit.getVersion();` | ✅ |
| 5 | `console "plugins"` 派发成功且控制台可见 | ✅ |
| 6 | `plugins` 返回 Coder/RosettaRemote/ForgeKit/Mohist | ✅ |
| 7 | `update ForgeKit ...` 热更新 + 重新启用日志 | ✅ |
| 8 | exec 经 Coder 类加载器调用 CoderAPI | ✅ |
| 9 | 自检监听器取消 CREEPER + 清理 | ✅ |
| 10 | `/java reload server` 后无重复取消 | ✅ |

---

## 2. 原始证据

### 验收 1：build + jar 无 module-info

```
> Task :compileJava
> Task :jar
> Task :reobfJar
BUILD SUCCESSFUL in 29s

jar tf ... | findstr module-info   →  0 条
jar tf ... | findstr com/rosetta/remotedebugbridge/net/  →
  com/rosetta/remotedebugbridge/net/BukkitAdapter.class
  com/rosetta/remotedebugbridge/net/NexusTask.class
  com/rosetta/remotedebugbridge/net/PluginReloader.class
  com/rosetta/remotedebugbridge/net/Reflect.class
  com/rosetta/remotedebugbridge/net/RemoteBridge$Task.class
  com/rosetta/remotedebugbridge/net/RemoteBridge.class
（无 org/bukkit/*，stub 未打包）
```

### 验收 2：带新 mod 启动（console2.log）

```
[17:40:12 INFO]: Remote token loaded from ...\run-fast\RosettaRemoteDebugBridge\remote-token.txt
[17:40:12 WARN]: Remote bridge listening on 127.0.0.1:48791 - token auth enforced, ...
[17:40:12 INFO]: Mohist 启动成功 ... 用时 15.087s
[17:40:13 INFO]: Self-check listener ready: registered rosetta.remote.generated.NexusSelfCheck... owner=Mohist via=ServerAPI
[17:40:13 INFO]: Bukkit adapter: active owner=Mohist
```

- `Select-String console2.log -Pattern ' ERROR ]|SEVERE|FATAL|Exception in thread'` → **0 条**
- `crash-reports/` 为空。
- 干净停服：`Remote bridge stopped`（17:38:18）→ 端口 25567/48790/48791 全部释放；
  重启后 token 从文件加载（同一 token 可继续使用）。

### 验收 3：ping

```
$ python tools/rosetta_remote.py --host 127.0.0.1 --port 48791 --token <T> ping
{
  "server": "127.0.0.1:48791",
  "bridge": "rosetta-nexus rosetta_remote_debug_bridge",
  "java": "17.0.18",
  "bukkit": true,
  "adapter": "present=true tracked=1 selfCheck=true owner=com.mohistmc.plugins.Main",
  "players": 0, "plugins": 4, "running": true
}
```

### 验收 4：exec Buikkt 版本（ECJ，无系统 JDK 链路）

```
$ ... eval "return org.bukkit.Bukkit.getVersion();"
{ "result": "1.20.1-ecf8d5ad (MC: 1.20.1)  [2ms]",
  "class": "rosetta.remote.generated.RosettaTask..." }
```

编译失败路径也验证（返回 ECJ 诊断，不崩服）：
`eval "this is not java !!"` → `ERROR: Compilation failed ... Compiler: Eclipse JDT ... Line 11: Syntax error ...`

### 验收 5：console 派发

```
$ ... console "plugins"
{"dispatched": true}
[17:36:27] [Server thread/INFO] [Console]: Plugins (4): Coder, RosettaRemote, ForgeKit, Mohist
```
（output 字段取自 `${gamedir}/logs/latest.log` 增量读取；跨线程用 `MinecraftServer.execute` 回主线程派发）

### 验收 6：plugins

```
[{Coder, 2.5.0-mohist-java17, enabled:true}, {RosettaRemote, 1.0.0, true},
 {ForgeKit, 1.0.1, true}, {Mohist, 1.20.1, true}]
```

### 验收 7：update ForgeKit（原地热更新）

```
$ ... update ForgeKit plugins/ForgeKit.jar G:\Projects\Software\Tenet\forge-kit\build\libs\ForgeKit.jar
{ "unload": "disabled, listeners, commands:2, manager, loader-closed",
  "loaded": "ForgeKit 1.0.1", "enabled": true }
```
console2.log：
```
[17:37:04 INFO]: [ForgeKit] 卸载完成（hot unload）...
[17:37:04 INFO]: [ForgeKit] Forge hooks unregistered (hot unload clean)
[17:37:05 INFO]: 加载完成 ForgeKit v1.0.1
[17:37:05 INFO]: [ForgeKit] Forge runtime bus hooked: EntityJoinLevelEvent (guard=off) [build v1.0.1 hot-update proof]
```
更新前后 ForgeKit.jar 与本地构建 SHA256 一致（`94877CB3...8F9F78`）。

### 验收 8：Coder 跨类加载器

exec 代码：
```java
ClassLoader cl = org.bukkit.Bukkit.getPluginManager().getPlugin("Coder").getClass().getClassLoader();
Class<?> api = Class.forName("dev.codestuff.coder.api.CoderAPI", true, cl);
Object instance = api.getMethod("getInstance").invoke(null);
return "CoderAPI loader=" + api.getClassLoader() + " methods=" + api.getMethods().length + " instance=" + (instance != null);
```
```
{ "result": "CoderAPI loader=org.bukkit.plugin.java.PluginClassLoader@bfe221d methods=133 instance=true  [9ms]" }
```

### 验收 9：自检监听器

```
$ ... console "summon minecraft:creeper 0 100 0"
{"dispatched": true}
console2.log:
[17:36:34 INFO]: [RosettaNexus] self-check listener cancelled creeper spawn at
  Location{world=CraftWorld{name=fastworld},x=0.5,y=100.0,z=0.5,...}
$ ... console "kill @e[type=minecraft:creeper]"   → {"dispatched": true}（实体已被取消，未找到）
```
命令清理验证：
```
listener status  → {"status":"present=true tracked=1 selfCheck=true owner=...Main"}
listener cleanup → {"removed":1,"status":"...tracked=0 selfCheck=false..."}
再次 summon → self-check 取消计数 before=2 after=2（不再触发）
```

### 验收 10：脚本监听器不重复

```
$ ... console "java reload server"
server.log: Unregistered 1 Bukkit listener(s) from previous server script classes
summon 前后 NexusProbe 取消计数：before=1 after=2 delta=1   （修复前为 delta=2）
```
> 说明：P0 遗留的“reload 后重复取消”已修复。清理逻辑 = 适配层按类加载器跟踪的实例
> + `HandlerList.getRegisteredListeners(ownerPlugin)` 扫描类加载器匹配的监听器并 `unregisterAll`，
> 因此脚本里直接 `ServerAPI.putBukkitEvents(...)` 注册的监听器（Probe.java）也能被回收。

### 安全边界（附加证据）

```
bad token      → {"ok":false,"error":"bad token"}（python 端 ERROR: bad token）
ls ..          → ERROR: path escapes server root: ..
netstat        → TCP 127.0.0.1:48791 LISTENING（未绑定 0.0.0.0）
upload/read    → 写读 65B 文件成功（RosettaRemoteDebugBridge/net-upload-test.txt，随后清理）
```

---

## 3. 遗留 / 未验证事项

1. **纯 Forge（无 Bukkit）路径未实测**：适配层检测 `org.bukkit.Bukkit` 失败即跳过；TCP 仍可工作；
   `console` 走 `MinecraftServer` 命令分发器的回退分支只有编译保证，未在纯 Forge 服实测。
2. `-Drosetta.remote.port/-bind/-token` 的覆盖分支未逐项实测（默认值路径全程验证）。
3. Probe.java 每次 reload 会重复注册 `/nprobe` 命令（命令清理只覆盖旧插件命令，不覆盖脚本自注册命令）——遗留，不影响验收。
4. `listener cleanup` 后自检监听器不会自动重新注册（需重启或后续加 `listener restore`）。
5. 48790 的 RosettaRemote 插件仍按原配置绑定 0.0.0.0；本 mod 新桥默认 loopback，两者互不影响。

## 4. 下一步建议

- P3（脚本引擎整合 + CoderAdapter）：把 `registerBukkitListener` 升级为脚本侧标准注册入口（可自动携带类加载器）；
- 给 `listener` 增加 `restore`，给纯 Forge 的 console 回退做一次 server-only 冒烟；
- 生产部署：保留 loopback + SSH 隧道/防火墙；调试结束卸载 mod 或关桥。
