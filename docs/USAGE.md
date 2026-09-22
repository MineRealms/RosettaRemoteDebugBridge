# RosettaRemoteDebugBridge 使用手册（`rosetta_remote_debug_bridge`）

> 适用产物：`rosetta_remote_debug_bridge-1.0.0.jar`（Forge 1.20.1 单产物：脚本引擎 + 静态 Mixin + Bukkit 适配 + 远程控制面）
> 配套文档：[README](../README.md) · [技术手册](ARCHITECTURE.md) · [测试与验收](TESTING.md)
> 状态：默认端口 **48790**；旧 `RosettaRemote` 插件桥已退役（jar 备份于 `plugins-disabled/`）。

---

## 1. 安装与升级

### 1.1 部署

1. 把 `rosetta_remote_debug_bridge-1.0.0.jar` 放入服务端 `mods/`（Mohist / 纯 Forge 1.20.1）。
2. 启动服务端。首次启动会在**服务端工作目录**（本测试服为 `run-fast/`）生成：
   - `RosettaRemoteDebugBridge/remote-token.txt`：28 字符随机 token；
   - `RosettaRemoteDebugBridge/{server,data,mixins}/`：脚本、数据包、动态 Mixin 目录。
3. 启动日志关键行：

```
[... INFO]: RosettaRemoteDebugBridge initializing...
[... INFO]: Remote bridge listening on 127.0.0.1:48790 - token auth enforced, loopback-only by default. ...
[... INFO]: Bukkit runtime detected - adapter active (owner plugin: Mohist)
[... INFO]: Bukkit adapter: active owner=Mohist
```

### 1.2 端口 / token 配置

| 配置 | 默认值 | 覆盖方式 |
|---|---|---|
| 监听端口 | `48790` | `-Drosetta.remote.port=<port>` |
| 绑定地址 | `127.0.0.1`（仅回环） | `-Drosetta.remote.bind=0.0.0.0`（不推荐，见 §5） |
| token | `<工作目录>/RosettaRemoteDebugBridge/remote-token.txt` | `-Drosetta.remote.token=<token>`（优先级最高） |
| 文件命令根目录逃逸 | 禁止 | `-Drosetta.remote.allowPathEscape=true`（不建议） |
| 脚本编译的 Mixin AP | 关闭（ECJ `-proc:none`） | `-Drosetta.mixin.annotationProcessors=true` |

在服务端启动命令的 JVM 参数中追加，例如：

```
java -Drosetta.remote.port=48790 -Drosetta.remote.token=MySecret -jar server.jar nogui
```

说明：

- token 文件不存在时，桥会生成随机 token 并写回该文件（启动日志含生成提示）。
- `-Drosetta.remote.token` 优先于文件；两者都无则生成随机并落盘。
- 修改端口/token/Bind 后需重启服务端（`RemoteBridge` 只在 `ServerStartedEvent` 启动一次）。

### 1.3 升级

- **mod 本体**：停服 → 覆盖 `mods/` 下的 jar → 起服 → `ping` 验证。
- **Bukkit 插件**（Coder/ForgeKit 等）：无需重启，用 `update` 命令热更新（见 §3.11）。
- 端口冲突排查：`netstat -ano | findstr 48790`（桥默认仅绑定 `127.0.0.1`）。

---

## 2. 线协议与 Python 客户端

### 2.1 线协议

- TCP，**每行一个 JSON**，一请求一连接（请求行结束即处理并返回，服务端随后断开）。
- 请求：`{"token":"...","cmd":"...","args":{...}}`
- 成功：`{"ok":true,"result":...}`；失败：`{"ok":false,"error":"...","exception":"...","cause":"..."}`
- token 连续错 3 次断开连接。
- 单行上限 64 MiB；返回文本结果截断至 200,000 字符。
- 超时：`console` 30s、`exec` 60s、`reflect` 30s、`update` 120s；`upload/read/tail/ls` 受客户端超时控制（客户端默认 180s/600s）。
- 所有连接/断开/命令名写入服务端日志（审计）。

### 2.2 Python 客户端

路径：`legacy/tools/rosetta_remote.py`（旧 `RosettaRemote` 路径已废弃，客户端本身通用）。

```
python legacy/tools/rosetta_remote.py --host 127.0.0.1 --port 48790 --token <TOKEN> <cmd> [args...]
```

- 环境变量回退：`ROSETTA_REMOTE_HOST` / `ROSETTA_REMOTE_PORT` / `ROSETTA_REMOTE_TOKEN`（客户端默认端口已是 48790）。
- 子命令：`ping`、`console`、`eval`（等价 `exec` 内联代码）、`exec <file>`、`upload`、`load`、`enable/disable`、`plugins`、`ls`、`tail`、`read`、`reflect`、`update`、`shell`。
- `coder` / `listener` 没有独立子命令，可直接调用客户端的传输函数（原样返回 JSON）：

```python
import sys
sys.path.insert(0, "legacy/tools")
import rosetta_remote as rr
TOKEN = open("run-fast/RosettaRemoteDebugBridge/remote-token.txt").read().strip()
print(rr.call("127.0.0.1", 48790, TOKEN, "listener", {"action": "status"}))
print(rr.call("127.0.0.1", 48790, TOKEN, "coder", {"action": "list"}))
```

- 注意：客户端子命令 `load` 属于旧插件桥，mod 桥不支持（等价能力是 `update`，见 §3.11）。

---

## 3. 命令手册

以下返回示例均来自 run-fast 实机（Mohist 1.20.1 / 48790）。

### 3.1 `ping`

- args：无。
- 返回：桥标识、端口、Java、Bukkit 适配状态、在线玩家/插件数。

```
$ python ... --port 48790 --token <T> ping
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

`server`/`bridge` 字段用于区分 mod 桥与旧插件桥（旧桥返回 `Mohist 1.20.1-...` + `remote` 字段）。

### 3.2 `console`

- args：`{"command":"<原样命令>"}`（客户端 `console "plugin list"` 会合并剩余参数）。
- 返回：`{"dispatched":bool,"output":"<命令执行后新增的服务端日志片段>"}`（等待 250ms 后读取日志增量）。
- Mohist 上走 Bukkit `ConsoleCommandSender`；纯 Forge 上走原版命令分发器。

```
$ python ... console "plugins"
{"dispatched": true}
[18:30:11] [Server thread/INFO] [Console]: Plugins (3): Coder, ForgeKit, Mohist
```

### 3.3 `exec`

- args：`{"code":"<Java 方法体>","args":["..."]}`；`args` 会作为 `Object[]` 传给任务。
- 返回：`{"result":"<String.valueOf(返回值)>  [Nms]","class":"rosetta.remote.generated.RosettaTask<...>"}`。
- 生成的类实现 `run(plugin,args)`，方法体必须 `return`；Mohist 上自动 `import org.bukkit.*` 并实现 `NexusTask`，纯 Forge 上 `plugin` 为 `null`。
- SRG 运行时会自动做 MCP→SRG 源码转换（可写官方名）。

```
$ python ... eval 'return org.bukkit.Bukkit.getVersion();'
{
  "result": "1.20.1-ecf8d5ad (MC: 1.20.1)  [1ms]",
  "class": "rosetta.remote.generated.RosettaTask5690632955000"
}
```

### 3.4 `reflect`

- args：`{"class":"<FQCN>","method":"<name>","target":"server|nms-server|plugin"?,"args":[...]?}`。
- `args` 支持裸字符串（按 `String`）或类型对象 `{"type":"int|long|double|float|boolean|short|byte|char|string|class|null","value":"..."}`。
- 返回：`{"result":"<String.valueOf(返回值)>"}`。

```
$ python ... reflect org.bukkit.Bukkit getVersion server
{
  "result": "1.20.1-ecf8d5ad (MC: 1.20.1)"
}
```

### 3.5 `upload`

- args：`{"path":"<相对服务器根目录的路径>","base64":"<文件内容>"}`。
- 返回：`{"path":"<绝对路径>","bytes":N}`；自动创建父目录；路径逃逸被拒绝。

```
$ python ... upload probe.txt RosettaRemoteDebugBridge/server/upload_probe.txt
{"path": "G:\\...\\RosettaRemoteDebugBridge\\server\\upload_probe.txt", "bytes": 28}
```

### 3.6 `read`

- args：`{"file":"<路径>","offset":0,"max":65536}`。
- 返回：`{"base64":"...","size":<文件总大小>}`；客户端 `read` 子命令会解码并打印文本。

```
$ python ... read RosettaRemoteDebugBridge/server/upload_probe.txt
upload/read probe
line2
```

### 3.7 `tail`

- args：`{"file":"<路径>","lines":200}`。
- 返回：`{"text":"<最后 N 行>"}`；客户端直接打印 `text`。

```
$ python ... tail logs/latest.log 3
[18:31:35] [RosettaNexus-Conn/INFO] [RosettaNexus/Remote]: disconnected 127.0.0.1
[18:31:35] [RosettaNexus-Conn/INFO] [RosettaNexus/Remote]: connection from 127.0.0.1
[18:31:35] [RosettaNexus-Conn/INFO] [RosettaNexus/Remote]: 127.0.0.1 -> tail
```

### 3.8 `ls`

- args：`{"dir":"<路径>"}`。
- 返回：条目名数组；目录带尾 `/`。

```
$ python ... ls RosettaRemoteDebugBridge/server
["upload_probe.txt", "HelloWorld.java"]
```

### 3.9 `plugins`

- args：无。Mohist 上返回 Bukkit 插件；纯 Forge 上退化为 ModList（`enabled` 恒 `true`）。
- 返回：`[{"name","version","enabled"}]`。

```
$ python ... plugins
[{"name":"Coder","version":"2.5.0-mohist-java17","enabled":true},
 {"name":"Mohist","version":"1.20.1","enabled":true},
 {"name":"ForgeKit","version":"1.0.1","enabled":true}]
```

### 3.10 `enable` / `disable`

- args：`{"name":"<插件名>"}`。
- 返回：`{"name":"<插件名>","enabled":bool}`；无 Bukkit 环境抛 `Bukkit is not present on this server`。

```
$ python ... disable ForgeKit
{"name": "ForgeKit", "enabled": false}
$ python ... enable ForgeKit
{"name": "ForgeKit", "enabled": true}
```

### 3.11 `update`（插件热更新）

- args：`{"name":"<插件名>","path":"plugins/<文件名>.jar","base64":"<新 jar>"}`。
- 语义：卸载旧插件（disable → 注销监听 → 摘除命令/权限 → 从管理器内部移除 → 关闭类加载器 → 清 JarFile 缓存）→ 写盘 → `loadPlugin` → `enablePlugin`。
- 返回：`{"unload":"<卸载步骤摘要>","loaded":"<名称 版本>","enabled":bool}`。

```
$ python ... update ForgeKit G:\...\run-fast\plugins\ForgeKit.jar
{
  "unload": "disabled, listeners, commands:2, manager, loader-closed",
  "loaded": "ForgeKit 1.0.1",
  "enabled": true
}
# 服务端日志：
[18:30:34 INFO]: [ForgeKit] ... ForgeKit v1.0.1 ...卸载完成
[18:30:35 INFO]: [ForgeKit] Forge hooks unregistered (hot unload clean)
[18:30:35 INFO]: [ForgeKit] Forge runtime bus hooked: EntityJoinLevelEvent (guard=off) [build v1.0.1 hot-update proof]
```

### 3.12 `listener`

- args：`{"action":"status|cleanup|restore"}`。
- `status`：`{"status":"present=... tracked=N selfCheck=... selfCheckInstances=N selfCheckCancels=N owner=..."}`
- `cleanup`：注销适配层跟踪的全部监听器（含自检监听器），`{"removed":N,"status":"..."}`。
- `restore`：重新注册自检监听器（幂等），`{"status":"..."}`。

```
$ python -c ... listener {"action":"status"}
{"status": "present=true tracked=1 selfCheck=true selfCheckInstances=1 selfCheckCancels=0 owner=com.mohistmc.plugins.Main"}
```

### 3.13 `coder`

- `{"action":"list"}`：`{"plugin","version","apiClass","loader","methods","instance"}`
- `{"action":"api","method":"<CoderAPI 方法>","args":[...]}`：`{"method","returns","result","ms"}`
- `{"action":"run","file":"<server 目录下的 .java>"}`：先 `coderc run <name>`，输出含 `Class not found` 时自动 `coderc compile <file>`；返回 `{"commands":[...],"output":"<控制台增量日志>"}`。

```
$ ... coder {"action":"list"}
{"plugin":"Coder","version":"2.5.0-mohist-java17","apiClass":"dev.codestuff.coder.api.CoderAPI",
 "loader":"org.bukkit.plugin.java.PluginClassLoader@10c33","methods":133,"instance":true}

$ ... coder {"action":"api","method":"getMinecraftVersion"}
{"method":"getMinecraftVersion","returns":"java.lang.String","result":"1.20.1-ecf8d5ad (MC: 1.20.1)","ms":0}
```

### 3.14 `clientdebug`(客户端远程调试,CRD)

前提:目标客户端也安装本 mod,且其 `RosettaRemoteDebugBridge/client-debug.toml` 中
`allowRemoteDebug=true`(默认关闭)。会话需要客户端玩家在确认屏上点 Accept。

| 调用 | 作用 |
|---|---|
| `{"action":"list"}` | 已握手的客户端(+ 会话状态) |
| `{"action":"info","player":"Dev"}` | 单个客户端详情 |
| `{"action":"identity"}` | 服务器身份指纹(可让玩家核对) |
| `{"action":"selftest"}` | 加密自检(ECDH/HKDF/AES-GCM/签名往返) |
| `{"action":"session","player":"Dev","operation":"open","permission":"RELOAD"}` | 发起会话(客户端弹确认) |
| `{"action":"session","player":"Dev","operation":"close"}` | 关闭会话 |
| `{"action":"op","player":"Dev","op":"collect_info"}` | READ:采集客户端信息 |
| `{"action":"op","player":"Dev","op":"tail_log","args":{"lines":100}}` | READ:读取客户端日志尾 |
| `{"action":"op","player":"Dev","op":"resource_reload"}` | RELOAD:远程 F3+T |
| `{"action":"op","player":"Dev","op":"push_resource_pack","args":{"url":"https://.../pack.zip","sha1":"..."}}` | RELOAD:下发资源包并启用 |
| `{"action":"op","player":"Dev","op":"run_client_action","args":{"action":"screenshot"}}` | ACTION:白名单动作 |
| `{"action":"op","player":"Dev","op":"eval_client_script","args":{"source":"package rosetta.client; public class X { public static void init() { System.out.println(\"hi\"); } }"}}` | SCRIPT:逐次弹窗确认后执行 |

客户端侧:会话请求会弹出确认界面;会话期间左上角 HUD 常驻显示,`/crd status` 查看、`/crd disconnect` 断开。
所有会话与操作写入客户端 `logs/Rosetta/client-debug.log` 与服务端日志。

```
$ ... clientdebug {"action":"list"}
{"clients":[{"name":"Dev","authorized":true,"maxPermission":"RELOAD","session":{"permission":"RELOAD","timeoutSeconds":1800}}], "count":1, "stage":"C4-remote-debug"}

$ ... clientdebug {"action":"session","player":"Dev","operation":"open","permission":"RELOAD"}
{"sessionId":123,"server":"RosettaNexus @ *:25567","client":"Dev","permission":"RELOAD","fingerprint":"ab12..."}

$ ... clientdebug {"action":"op","player":"Dev","op":"collect_info"}
{"requestId":1,"ok":true,"result":{"mcVersion":"1.20.1","fps":120,"modCount":4,...}}
```

---

## 4. Bukkit 适配说明（Mohist）

### 4.1 探测与 owner

- `BukkitAdapter.present()` 以 `Class.forName("org.bukkit.Bukkit")` 探测运行时；结果缓存。
- owner 插件优先 `PluginManager#getPlugin("Mohist")`，否则取插件列表第一个（本服为 `com.mohistmc.plugins.Main`）。
- 启动时适配层用 ECJ 现场编译一个自检监听器（取消 CREEPER 生成）并注册，用于证明“mod 注册 Bukkit 监听器”链路可用；`listener status` 的 `selfCheck*` 即其状态。

### 4.2 监听器注册（ServerAPI）

- 脚本统一走 `com.rosetta.remotedebugbridge.net.NexusBukkit.registerListener(listener)`。
- Mohist 下优先 `com.mohistmc.api.ServerAPI#putBukkitEvents(Listener, Plugin)`（owner 归属 Mohist 插件，避免未注册插件的加载器问题）；方法不存在时回退 `PluginManager#registerEvents`。
- 注册按**监听器类的 ClassLoader** 记账，供脚本 reload 精确清理。

### 4.3 命令注册（CommandMap）

- `NexusBukkit.registerCommand("<fallbackPrefix>", command)` → `CommandMap#register(prefix, command)`，并记账到命令类的 ClassLoader。
- 注销时除 `Command#unregister(map)` 外，还会直接清理 `knownCommands` 中所有指向该实例的键（含 `prefix:label` 形式），避免重复命令残留。

### 4.4 插件热更新语义

`update` 顺序：`disablePlugin` → `HandlerList.unregisterAll` → 摘除该插件的 `PluginCommand` → `removePermission` → 从 `SimplePluginManager` 的 `plugins`/`lookupNames` 移除 → `URLClassLoader.close()`（释放 Windows 文件句柄）→ 写新 jar → `clearJarCache`（清 `sun.net.www.protocol.jar.JarFileFactory` 缓存）→ `loadPlugin` → `enablePlugin`。
副作用：插件自身的静态状态不会保留（新类加载器），需要持久化的数据要落盘。

### 4.5 监听器 cleanup / restore

- `listener cleanup`：注销适配层跟踪的所有监听器并清空账本（自检监听器也在内）；用于调试后收尾。
- `listener restore`：幂等地把自检监听器装回，返回最新 `describe()`。
- 脚本 reload（Rosetta/Coder 触发）走 `BukkitAdapter.cleanupClassLoader(loader)`：
  1. 注销该 ClassLoader 记账的监听器；
  2. 扫描 owner 插件的 `HandlerList#getRegisteredListeners`，注销同 ClassLoader 的监听器（覆盖脚本直接经 ServerAPI 注册的情况）；
  3. 注销该 ClassLoader 记账的命令。
- 结论：脚本里请用 `NexusBukkit` 门面注册；直接摸 `CommandMap`/`ServerAPI` 的注册无法被命令账本回收。

### 4.6 与 Coder 的协作

- `CoderAdapter` 通过 Coder 插件的 `PluginClassLoader` 反射加载 `dev.codestuff.coder.api.CoderAPI`（跨类加载器）。
- `coder api` 可调用 133 个 API 方法；`coder run` 经控制台驱动 `coderc run/compile`。
- Coder 侧脚本若要注册监听/命令，同样建议使用 `NexusBukkit`，即可获得 reload 清理语义。

### 4.7 纯 Forge（无 Bukkit）路径

代码审查（`com.rosetta.remotedebugbridge.net.*`）：

- 除 `NexusTask` 接口签名包含 `org.bukkit.plugin.Plugin` 外，其余类**零硬引用**，一律 `Reflect.load` 反射访问；`BukkitAdapter.present()` 捕获所有 `Throwable`。
- `exec` 仅在检测到 Bukkit 时才生成 `implements NexusTask` 的源码；纯 Forge 生成 `run(Object,Object[])`。
- `console` 回退原版命令分发器；`plugins` 回退 `ModList`；`update`/`enable`/`disable` 明确抛 `Bukkit is not present on this server`。
- 无 Bukkit 类路径下所有适配入口均惰性返回或抛出明确异常（`Bukkit is not present on this server`），无 ClassNotFound 崩溃路径。

---

## 5. 安全

- **token 强制**：所有命令（含 `ping`）都要 token；错误 3 次断连。token 是任意代码执行凭据，等同服务器 shell，谨防泄漏（日志、截图、聊天）。
- **回环绑定**：默认 `127.0.0.1`。除非防火墙 + SSH 隧道，否则不要设 `-Drosetta.remote.bind=0.0.0.0`。
- **SSH 隧道**：`ssh -L 48790:127.0.0.1:48790 user@server`，本地再连 `127.0.0.1:48790`；服务端无需暴露端口。
- **文件边界**：`upload/read/tail/ls` 限制在服务端工作目录内（`allowPathEscape` 默认关闭）。
- **调试后卸载**：停服 → 删除 `mods/` 下的 jar（或移出）→ 起服；如需同时清凭据，删除 `RosettaRemoteDebugBridge/remote-token.txt`（注意目录内还有脚本/数据，按需保留）。
- **审计**：服务端日志记录每次 `connection from`/`disconnected`/`<ip> -> <cmd>`，可用于回溯。

---

## 6. 验收与测试

功能验收清单、测试套件与覆盖边界见 [TESTING.md](TESTING.md)。

### 回滚（恢复旧插件）

1. 停服；
2. 将 `mods/rosetta_remote_debug_bridge-1.0.0.jar` 移出（或覆盖回旧版本）；
3. 将 `plugins-disabled/RosettaRemote.jar` 移回 `plugins/`；
4. 起服确认 `48790` 由旧桥监听。注意旧桥绑定 `0.0.0.0`，且不能与 mod 桥默认端口共存——若两者都要，mod 需 `-Drosetta.remote.port=48791` 之类的替代端口。
