# P3 验证报告：监听器生命周期闭环 + 脚本注册清理 + 启动参数覆盖 + CoderAdapter

> 日期：2026-09-22
> 被测产物：`build/libs/rosetta_remote_debug_bridge-1.0.0.jar`
> SHA256：`749E34304A73F57177B13D3E4EF0ABD6CE76B4BCF15D5025E3628681A178E0A5`（构建产物与部署到 `run-fast/mods` 的 jar 哈希一致）
> 环境：`projects/mohist/run-fast`（Mohist 1.20.1-ecf8d5ad / Forge 47.4.13 / JDK 17）
> 新桥：`127.0.0.1:48791`（默认，token 落盘 `run-fast/RosettaRemoteDebugBridge/remote-token.txt`）
> 结论：**4/4 验收项通过**；过程中发现并修复 1 个命令反注册缺陷（见 §2.5）。

---

## 1. 本次改动

| 文件 | 改动 |
|---|---|
| `net/BukkitAdapter.java` | 新增 `registerCommand/unregisterCommand`（按类加载器跟踪）；`cleanupClassLoader` 同时清理命令，返回 `[listeners, commands]`；自检监听器新增实例计数与取消计数；新增 `restoreSelfCheck()`；`describe()` 增加 `selfCheckInstances/selfCheckCancels` |
| `net/NexusBukkit.java`（新增） | 脚本侧注册门面：`registerListener/unregisterListener/registerCommand/unregisterCommand/status` |
| `net/CoderAdapter.java`（新增） | 跨类加载器反射调用 `dev.codestuff.coder.api.CoderAPI`：`list/api` |
| `net/RemoteBridge.java` | `listener` 新增 `restore`；新增 `coder` 命令（`list/api/run`，`run` 走 `coderc run/compile` 控制台派发并回显输出） |
| `core/RosettaCore.java` | reload 日志同时报告 listener/command 清理数量 |
| 测试服 `RosettaRemoteDebugBridge/server/Probe.java` | 裸 `commandMap.register` → `NexusBukkit.registerCommand("rosetta", command)`（该文件位于被 .gitignore 的 `projects/mohist/` 下，仅存在于测试服） |

构建：

```
> Task :compileJava
> Task :jar
> Task :reobfJar
BUILD SUCCESSFUL in 29s

jar tf ... | findstr net/NexusBukkit net/CoderAdapter
  com/rosetta/remotedebugbridge/net/NexusBukkit.class
  com/rosetta/remotedebugbridge/net/CoderAdapter.class
```

---

## 2. 验收项与原始证据

控制通道：`python tools/rosetta_remote.py --port 48791 --token <T> ...`；原始 JSON 由
`rr.py`（`rosetta_remote.call` 薄封装，见仓库外测试工具）发送并回显。

### 2.1 监听器生命周期闭环（cleanup / restore / status）

1) 初始状态：

```
listener {"action":"status"}
{"status":"present=true tracked=1 selfCheck=true selfCheckInstances=1 selfCheckCancels=0 owner=com.mohistmc.plugins.Main"}
```

2) summon 苦力怕 → 自检监听器取消，计数 +1：

```
console "summon minecraft:creeper 0 100 0"
[17:50:33] [NexusProbe] Bukkit EntitySpawnEvent CANCELLED a creeper spawn
[17:50:33] [RosettaNexus] self-check listener cancelled creeper spawn at Location{world=CraftWorld{name=fastworld},x=0.5,y=100.0,z=0.5,...}

listener status → {"status":"... selfCheckInstances=1 selfCheckCancels=1 ..."}
```

3) cleanup（为隔离行文，先用 exec 移除 Probe 脚本监听器，再做 summon）：

```
listener {"action":"cleanup"}
{"removed":1,"status":"present=true tracked=0 selfCheck=false selfCheckInstances=0 selfCheckCancels=1 owner=com.mohistmc.plugins.Main"}

exec: unregister HandlerList.getRegisteredListeners(owner) 中类名以 rosetta.server.Probe 开头的监听器
{"result":"probe listeners removed=1 remaining=0  [3ms]"}

console "summon minecraft:creeper 10 100 0"
[17:50:41] [Minecraft]: 召唤了新的苦力怕      ← 无任何取消行，爬行者未被取消

listener status → {"status":"... selfCheck=false selfCheckInstances=0 selfCheckCancels=1 ..."}   ← 计数未变
```

4) restore 后重新注册，summon 再次被取消：

```
listener {"action":"restore"}
{"status":"present=true tracked=1 selfCheck=true selfCheckInstances=1 selfCheckCancels=1 owner=com.mohistmc.plugins.Main"}

console "summon minecraft:creeper 20 100 0"
[17:50:44] [RosettaNexus] self-check listener cancelled creeper spawn at Location{...x=20.5...}

listener status → {"status":"... selfCheckInstances=1 selfCheckCancels=2 ..."}
```

### 2.2 脚本注册内容的 reload 清理（命令 + 监听器）

Probe.java 经 `NexusBukkit.registerCommand` 注册；启动时日志：

```
[17:50:21] [NexusProbe] ... NexusBukkit.registerCommand=registered nprobe prefix=rosetta accepted=true loader=com.rosetta.remotedebugbridge.script.DynamicClassLoader@76bb065b;
```

连续两次 `/java reload server`：

```
reload #1:
[17:50:48] [SERVER] Unregistered 0 Bukkit listener(s) and 2 command(s) from previous server script classes
[17:50:48] [NexusProbe] ... accepted=true loader=...DynamicClassLoader@6009a2bb;

reload #2:
[17:50:54] [SERVER] Unregistered 1 Bukkit listener(s) and 2 command(s) from previous server script classes
[17:50:55] [NexusProbe] ... accepted=true loader=...DynamicClassLoader@46d44f53;
```

> 说明：`2 command(s)` 指 knownCommands 中 `nprobe` 与 `rosetta:nprobe` 两个键（CraftCommandMap 双键注册）。

reload 后 `/nprobe` 只执行一次，且执行的是最新脚本类加载器：

```
console "nprobe"
[17:50:59] [Console]: NexusProbe command OK: 1.20.1-ecf8d5ad (MC: 1.20.1) / java 17.0.18 / loader=1188319059
（仅一行）

exec: dump knownCommands 中 key 含 "nprobe" 的条目
hits=[nprobe -> 1142922369 loader=1188319059, rosetta:nprobe -> 1142922369 loader=1188319059]
（两个键指向同一个最新命令对象，无旧类加载器残留）

console "summon minecraft:creeper 30 100 0"
[17:51:06] [RosettaNexus] self-check listener cancelled ...
[17:51:06] [NexusProbe] Bukkit EntitySpawnEvent CANCELLED a creeper spawn
（两个监听器各恰好一行；listener status 的 selfCheckCancels 2→3，无重复取消）
```

### 2.3 启动参数覆盖实测（-Drosetta.remote.port / -Drosetta.remote.token）

带参数启动（PowerShell 中对每个 `-D` 参数加引号，否则 5.1 会把参数按 `.` 拆坏）：

```
& $java "-Drosetta.remote.port=48792" "-Drosetta.remote.token=TESTTOKEN-123" -Xms1G -Xmx2G -jar ...\rosetta-1.20.1-ecf8d5ad-server.jar nogui
```

`console3.log`：

```
[17:53:52 INFO]: Remote token loaded from -Drosetta.remote.token (13 chars)
[17:53:52 WARN]: Remote bridge listening on 127.0.0.1:48792 - token auth enforced, ...
```

端口/令牌验证：

```
netstat: TCP 127.0.0.1:48792 LISTENING（48791 不存在；48790 为旧插件桥，未受影响）

ping @48792 token=TESTTOKEN-123
{"server":"127.0.0.1:48792","bridge":"rosetta-nexus rosetta_remote_debug_bridge","java":"17.0.18",
 "bukkit":true,"adapter":"present=true tracked=1 selfCheck=true selfCheckInstances=1 selfCheckCancels=0 ...","running":true}

ping @48792 token=<文件 token>  → ERROR: bad token（exit=1）
```

恢复默认启动（48791 + 文件 token）：

```
[17:55:24 INFO]: Remote token loaded from G:\...\run-fast\RosettaRemoteDebugBridge\remote-token.txt
[17:55:24 WARN]: Remote bridge listening on 127.0.0.1:48791 - ...
ping @48791 → {"server":"127.0.0.1:48791", ... "selfCheckInstances=1 selfCheckCancels=0" ...}   ✔
48792 已释放。
```

> 注：覆盖测试的启动脚本放在临时目录（`start-override.ps1`），未写入 run-fast。

### 2.4 CoderAdapter 命令（跨类加载器）

```
coder {"action":"list"}
{"plugin":"Coder","version":"2.5.0-mohist-java17","apiClass":"dev.codestuff.coder.api.CoderAPI",
 "loader":"org.bukkit.plugin.java.PluginClassLoader@1be7e0ee","methods":133,"instance":true}

coder {"action":"api","method":"getMinecraftVersion","args":[]}
{"method":"getMinecraftVersion","returns":"java.lang.String","result":"1.20.1-ecf8d5ad (MC: 1.20.1)","ms":0}

coder {"action":"run","file":"smoke.java"}
{"commands":["coderc run smoke","coderc compile smoke.java"],
 "output":"[17:51:13] [Console]: [Coder] Class not found in JavaClasses/Runtime/: smoke.class
           [17:51:13] [Console]: [Coder] Compiling smoke.java to JavaClasses/Runtime/...
           [17:51:14] [Console]: [Coder] Compilation successful (734ms)
           [17:51:14] [Minecraft]: [CoderSmoke] java engine ok: 1.20.1-ecf8d5ad (MC: 1.20.1) / java 17.0.18
           [17:51:14] [Console]: [Coder] Compilation successful (748ms). Output: JavaClasses/Runtime/smoke.class"}
```

`run` 逻辑：先派发 `coderc run <class>`；输出含 `Class not found` 时回退 `coderc compile <file>`
（Coder 的 compile 会编译并执行 main），两条命令与完整控制台输出一并返回。

### 2.5 过程中发现并修复的缺陷：CraftCommandMap 双键残留

首版实现只用 `Command#unregister(commandMap)`，实测未清干净（首版实测证据）：

```
NexusBukkit.registerCommand=... accepted=false loader=...DynamicClassLoader@4aa6548e
knownCommands hits=[nprobe -> rosetta.server.Probe$2 loader=472529803,
                    rosetta:nprobe -> rosetta.server.Probe$2 loader=1252414606]
（旧命令仍占 nprobe 键 → 新注册 accepted=false 被降级到 rosetta:nprobe，旧类加载器命令仍会响应）
```

修复：`unregisterCommand` 除调用 `unregister` 外，直接扫描 `commandMap.knownCommands`，移除所有
value == 该命令对象的键。修复后 §2.2 证据显示 `accepted=true`、两键同指最新对象、`/nprobe` 仅执行一次。

### 2.6 回归（P1P2 基础项在新构建上的抽查）

```
plugins → Coder 2.5.0-mohist-java17 / RosettaRemote 1.0.0 / ForgeKit 1.0.1 / Mohist 1.20.1（全部 enabled）
exec "return org.bukkit.Bukkit.getVersion();" → "1.20.1-ecf8d5ad (MC: 1.20.1)  [0ms]"
console2.log ERROR/SEVERE/FATAL 行数 → 0；crash-reports → 0
```

---

## 3. 清理动作 / 遗留

清理：

- 测试用苦力怕已全部清除（`kill @e[type=minecraft:creeper]` → `杀死了苦力怕`），无残留实体；
- 服务器已恢复默认启动（48791 + 文件 token）并保持运行；48792 覆盖实例已停止；
- 未改动旧桥 48790、其他插件与服务器 jar；`run-fast` 下仅 `mods/` 的 mod jar、`server/Probe.java`、
  `console3.log`（覆盖测试日志，留证）发生变化。

遗留（未变）：

1. 纯 Forge（无 Bukkit）console 回退分支仍未实测（P1P2 遗留 1）；
2. `-Drosetta.remote.bind` 单独覆盖未测（默认 loopback 全程验证）；
3. `listener cleanup` 只清理自检/适配层跟踪的监听器；脚本监听器由 reload 按类加载器回收（本报告 §2.2 已验证）；
4. 48790 旧插件仍绑定 0.0.0.0（未触碰）。
