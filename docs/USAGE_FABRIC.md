# RosettaRemoteDebugBridge 使用手册（Fabric 1.20.1）

适用分支：`fabric-1.20.1`。Forge 47.x 的完整手册见 [USAGE.md](USAGE.md)（含 Bukkit/Mohist、
动态 Mixin、CoreMod、CRD 客户端通道），这些能力在本分支**不存在**。

本分支只做两件事：

1. 把 `.java` 源码在游戏内编译并执行（ECJ，无需 JDK）；
2. 通过 TCP 远程控制面执行代码、跑命令、读写文件，供自动化测试驱动。

---

## 1. 安装

### 1.1 部署

1. 安装 **Fabric Loader 0.16.x** 与 **Fabric API**（1.20.1）。
2. 把 `rosetta_remote_debug_bridge-1.0.0-fabric.jar` 放进实例的 `mods/`。
   - 服务端、客户端（含单人集成服务器）都可以；
   - 不需要 Fabric API 之外的任何前置。
3. 启动。首次启动会在**游戏工作目录**生成：

```
RosettaRemoteDebugBridge/
├── remote-token.txt   28 字符随机 token
├── README.txt
├── startup/           游戏初始化时执行一次
├── server/            每次服务器启动时执行
├── client/            客户端初始化时执行
├── assets/            资源包根目录（可选）
└── data/              数据包根目录（可选）
logs/Rosetta/{startup,server,client}.log
```

4. 启动日志关键行：

```
[Server thread/INFO] (RosettaRemoteDebugBridge) [SERVER] Server started - loading server scripts
[Server thread/WARN] (RosettaNexus/Remote) Remote bridge listening on 127.0.0.1:48790 ...
```

> 桥只在**服务器启动后**拉起：专用服务器是开服完成；客户端是进入世界（集成服务器启动）之后。
> 停留在主菜单时端口不会监听。

### 1.2 端口 / token

| 配置 | 默认值 | 覆盖方式 |
|---|---|---|
| 监听端口 | `48790` | `-Drosetta.remote.port=<port>` |
| 绑定地址 | `127.0.0.1` | `-Drosetta.remote.bind=0.0.0.0`（不推荐） |
| token | `<工作目录>/RosettaRemoteDebugBridge/remote-token.txt` | `-Drosetta.remote.token=<token>`（优先级最高） |
| 文件命令根目录逃逸 | 禁止 | `-Drosetta.remote.allowPathEscape=true`（不建议） |

Loom 开发环境加 JVM 参数：`./gradlew runServer -Dorg.gradle.jvmargs=...` 不会传给游戏进程，
请在 `build.gradle` 的 `loom { runs { server { vmArg '-Drosetta.remote.port=48790' } } }` 里加。

改端口/token 后必须重启服务器（桥只在 `SERVER_STARTED` 启动一次）。

### 1.3 升级

停服 → 覆盖 `mods/` 里的 jar → 起服 → `ping` 验证。脚本无需重打包，`/java reload` 即可。

---

## 2. 脚本

### 2.1 规则

- 每个脚本类必须有 `public static void init()`（也接受 `initialize/onLoad/load/register`）。
- 包名必须与目录一致：

| 目录 | 包名 | 执行时机 |
|---|---|---|
| `startup/` | `rosetta.startup` | 模组初始化（世界加载前） |
| `server/` | `rosetta.server` | 服务器启动（专用/集成） |
| `client/` | `rosetta.client` | 客户端初始化 |

- 编译 classpath 自动包含：Minecraft、所有 Fabric mod、`mods/` 与 `libraries/` 下的 jar。
  所以脚本里可以直接 `import net.minecraft.world.item.ItemStack;`。
- 没有 `init()` 时，会尝试无参构造（仅实例化，不注册任何东西）。
- 脚本异常写入 `logs/Rosetta/<type>.log` 与错误收集器，可用 `/java errors` 查看。

### 2.2 示例

`RosettaRemoteDebugBridge/server/Hello.java`

```java
package rosetta.server;

import net.minecraft.server.MinecraftServer;

public class Hello {
    public static void init() {
        MinecraftServer server = com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge.getServer();
        System.out.println("[Hello] tick=" + server.getTickCount()
                + " players=" + server.getPlayerCount());
    }
}
```

### 2.3 事件

`@RosettaEventSubscriber` 把类注册到 Rosetta 事件总线；方法用 `@RosettaSubscribeEvent` 标记，
必须是 `public static`、单参数。事件对象没有基类要求（取消状态通过反射读取 `isCanceled()`）。

```java
package rosetta.server;

import com.rosetta.remotedebugbridge.eventbus.RosettaEventSubscriber;
import com.rosetta.remotedebugbridge.eventbus.RosettaSubscribeEvent;

@RosettaEventSubscriber
public class Ticker {
    @RosettaSubscribeEvent
    public static void onCustom(MyEvent event) {
        System.out.println("[Ticker] " + event);
    }
}
```

> 本分支**没有** Forge 事件桥（原版的 197 个 Forge 事件不会转发）。需要监听原版事件时，
> 用 `exec` 轮询（例如读 `server.getTickCount()`），或自行写静态 Mixin 进 `fabric.mod.json`。

---

## 3. 远程控制面

### 3.1 线协议

- TCP，**每行一个 JSON**，一请求一连接（处理完返回并断开）。
- 请求：`{"token":"...","cmd":"...","args":{...}}`
- 成功：`{"ok":true,"result":...}`
- 失败：`{"ok":false,"error":"...","exception":"...","cause":"..."}`
- token 连续错 3 次断开；单行上限 64 MiB；文本结果截断到 200,000 字符。
- 超时：`console` 30s、`exec` 60s、`reflect` 30s。

最小客户端：

```python
import json, socket

TOKEN = open("run-server/RosettaRemoteDebugBridge/remote-token.txt").read().strip()

def call(cmd, args=None, port=48790):
    with socket.create_connection(("127.0.0.1", port), timeout=60) as s:
        s.sendall((json.dumps({"token": TOKEN, "cmd": cmd, "args": args or {}}) + "\n").encode())
        data = b""
        while not data.endswith(b"\n"):
            data += s.recv(65536)
    return json.loads(data.decode("utf-8", "replace"))

print(call("ping"))
```

### 3.2 命令总览

| 命令 | 作用 | 本分支 |
|---|---|---|
| `ping` | 桥信息、映射命名、在线玩家 | ✅ |
| `console` | 执行原版命令并回传日志增量 | ✅ |
| `exec` | 编译并执行 Java 方法体 | ✅ |
| `reflect` | 反射调用静态/实例方法 | ✅（target 仅 `nms-server`） |
| `upload/read/tail/ls` | 文件读写（限制在游戏目录） | ✅ |
| `plugins` | 列出 Fabric mod | ✅ |
| `enable/disable/update/listener/coder/clientdebug` | Bukkit / CRD | ❌ 返回 `unsupported` |

---

## 4. 命令手册

### 4.1 `ping`

args：无。

```json
{
  "server": "127.0.0.1:48790",
  "bridge": "rosetta-nexus rosetta_remote_debug_bridge",
  "loader": "fabric",
  "mappings": "named",
  "java": "21.0.8",
  "bukkit": false,
  "adapter": "none",
  "players": 0,
  "plugins": 0,
  "running": true
}
```

- `mappings`：`named`（Loom 开发环境）或 `intermediary`（正式实例，见 §7）。
- `players` 是服务器玩家数；`plugins` 字段保留兼容，恒为 0（用 `plugins` 命令拿 mod 列表）。

### 4.2 `console`

args：`{"command":"<命令，可带或不带前导 />"}`

返回：`{"dispatched":true,"output":"<执行后 250ms 内 latest.log 的新增内容>"}`

```python
call("console", {"command": "list"})
# {"dispatched": true, "output": "[...] There are 0 of a max of 20 players online: ..."}
```

命令在服务器线程执行，等价于控制台输入。权限按控制台（4 级）。

### 4.3 `exec`

args：`{"code":"<Java 方法体>","args":["..."]?}`

返回：`{"result":"<String.valueOf(返回值)>  [Nms]","class":"rosetta.remote.generated.RosettaTask..."}`

生成的类固定为：

```java
package rosetta.remote.generated;
public class RosettaTask... {
    public Object run(Object plugin, Object[] args) throws Throwable {
        <你的代码>
    }
}
```

- 代码体必须自己 `return`（没有返回值就 `return null;`）。
- `plugin` 在 Fabric 版本恒为 `null`；`args` 是请求里的字符串数组。
- 在**服务器线程**执行，可直接读写世界/实体/方块，不需要再 `server.execute(...)`。
- 访问当前服务器：`com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge.getServer()`。
- 客户端环境（单人）里还可以直接 `net.minecraft.client.Minecraft.getInstance()`，
  因为集成服务器与客户端同 JVM、同一线程模型（仍在服务器线程上）。

常用写法：

```python
call("exec", {"code": "return net.minecraft.SharedConstants.getCurrentVersion().getName();"})
# "1.20.1  [3ms]"

call("exec", {"code": "return com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge.getServer().getPlayerCount();"})

call("exec", {"code": "var lvl = com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge.getServer().overworld();"
                      "lvl.setDayTime(6000L); return \"dayTime=\" + lvl.getDayTime();"})
# "dayTime=6000  [0ms]"

# 用 args 传参
call("exec", {"code": "return args[0] + \"/\" + args[1];", "args": ["a", "b"]})
# "a/b  [0ms]"
```

编译失败时返回 `ok:false`，`error` 里是 ECJ 诊断（含行号）。`exec` 的源码在内存中编译，
不会落盘；类名带纳秒后缀，可反复执行。

### 4.4 `reflect`

args：`{"class":"<FQCN>","method":"<name>","target":"nms-server"?,"args":[...]?}`

- `args` 支持裸字符串（按 `String`）或类型对象：
  `{"type":"int|long|double|float|boolean|short|byte|char|string|class|null","value":"..."}`
- 返回：`{"result":"<String.valueOf(返回值)>"}`
- 在服务器线程调用，自动 `setAccessible(true)`。

```python
call("reflect", {"class": "net.minecraft.server.MinecraftServer", "method": "getPlayerCount",
                 "target": "nms-server"})
```

> 方法名按**运行期名字**匹配。Loom 开发环境就是 Mojmap 名；正式实例是 intermediary 名，
> 用 `exec` + 反射按签名查找更稳。

### 4.5 `upload` / `read` / `tail` / `ls`

根目录是**游戏工作目录**（`run-server/`、实例目录或 `.minecraft`），禁止逃逸。

| 命令 | args | 返回 |
|---|---|---|
| `upload` | `{"path","base64"}` | `{"path","bytes"}`，自动建父目录 |
| `read` | `{"file","offset"?, "max"?}` | `{"base64","size"}` |
| `tail` | `{"file","lines"?}` | `{"text"}` |
| `ls` | `{"dir"}` | 名称数组，目录带尾 `/` |

```python
import base64
call("upload", {"path": "RosettaRemoteDebugBridge/server/Probe.java",
                "base64": base64.b64encode(source.encode()).decode()})
call("console", {"command": "java reload server"})
print(call("tail", {"file": "logs/latest.log", "lines": 20})["text"])
```

### 4.6 `plugins`

args：无。返回 Fabric 已加载 mod：

```json
[{"name":"minecraft","version":"1.20.1","enabled":true},
 {"name":"fabricloader","version":"0.16.14","enabled":true},
 {"name":"rosetta_remote_debug_bridge","version":"1.0.0-fabric","enabled":true}]
```

---

## 5. 游戏内命令

需要权限等级 2（单人需开作弊 / 服务器 OP）。

| 命令 | 说明 |
|---|---|
| `/java reload [startup\|server\|client]` | 重新编译并执行该目录脚本；按 ClassLoader 注销旧监听器 |
| `/java errors [startup\|server\|client]` | 显示错误/警告摘要，附日志链接 |
| `/java hand getId` | 手持物品的注册 ID（可点击复制） |
| `/java hand getClass` | 手持物品的 Java 类名 |
| `/j ...` | `/java` 的别名 |

`reload` 会新建 ClassLoader；旧的脚本类不会被卸载，但事件监听器会按 ClassLoader 移除，
因此反复 reload 不会重复触发。

---

## 6. 自动化测试

### 6.1 等待就绪

```python
import socket, time

def wait_bridge(port=48790, timeout=180):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            socket.create_connection(("127.0.0.1", port), timeout=1).close()
            return True
        except OSError:
            time.sleep(1)
    raise TimeoutError("bridge did not start")
```

### 6.2 断言

```python
def must(ok, message):
    if not ok:
        raise AssertionError(message)

ping = call("ping")
must(ping["ok"] and ping["result"]["running"], "bridge not running")

res = call("exec", {"code": "return com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge"
                            ".getServer().getTickCount() > 0;"})
must(res["ok"] and res["result"]["result"].startswith("true"), "server not ticking")
```

### 6.3 冒烟脚本

```bash
./gradlew runServer
python tools/fabric_smoke_test.py --token-file run-server/RosettaRemoteDebugBridge/remote-token.txt
```

覆盖 `ping`、游戏类访问、`getServer()`、`console list`，失败时退出码 1。

### 6.4 场景脚本

- **放置/破坏方块**：`exec` 里用 `server.overworld().setBlockAndUpdate(new BlockPos(...), Blocks.STONE.defaultBlockState())`，
  再用 `getBlockState` 断言；别忘了 `server.overworld().getChunk(...)` 已加载。
- **给物品/清背包**：`ServerPlayer#getInventory()`，玩家可用 `server.getPlayerList().getPlayers()` 取。
- **推进时间/天气**：`ServerLevel#setDayTime` / `setWeatherParameters`。
- **截图（客户端）**：在单人环境用 `exec` 调 `net.minecraft.client.Screenshot` 或原版命令；
  本分支没有内置截图命令。

---

## 7. 已知限制

### 7.1 生产实例（intermediary）不能直接编译 Mojmap 代码

Fabric 正式实例的 Minecraft 类名是 intermediary（`net.minecraft.class_310`），
而脚本按 Mojmap 书写。当前源码转换器只重写成员名，不重写类名，因此：

```
exec -> ok:false, "compilation failed on an intermediary (production Fabric) runtime ..."
```

可用范围：**Loom 开发环境**（`runServer`/`runClient`，`mappings=named`）。
若必须在正式实例调试，暂时只能用反射 + 字符串类名，或等待类名重写/tiny-remapper 支持。

### 7.2 没有动态 Mixin / CoreMod

Fabric 没有 ModLauncher，也没有运行期类重转换；`mixins/`、`coremod/` 目录不会创建，
相关 API 已删除。需要改字节码请写静态 Mixin 进 `fabric.mod.json`。

### 7.3 没有 Bukkit / CRD

`enable`、`disable`、`update`、`listener`、`coder`、`clientdebug` 一律返回
`unsupported`。客户端远程调试请直接用 `exec` + `Minecraft.getInstance()`（单人环境）。

---

## 8. 排错

| 现象 | 原因 / 处理 |
|---|---|
| 端口不监听 | 未进入世界/未开服；桥只在 `SERVER_STARTED` 启动 |
| `bad token` | 用了别的实例的 token；确认工作目录下的 `remote-token.txt`，或 `-Drosetta.remote.token` |
| `cannot bind` | 端口被占用：`netstat -ano \| findstr 48790`；换 `-Drosetta.remote.port` |
| `exec` 编译失败 | 看 `error` 的 ECJ 诊断；确认类在 classpath（mod 是否加载）；正式实例见 §7.1 |
| 脚本没执行 | 包名与目录不匹配；没有 `public static init()`；看 `logs/Rosetta/<type>.log` |
| `/java` 无权限 | 单人开作弊，服务器给自己 OP（等级 2） |
| 中文乱码 | 日志按 UTF-8 读取；游戏 JVM 若用 GB18030，`tail` 可能乱码，直接读文件并指定编码 |

---

## 9. 安全

- 控制面 = 任意代码执行。token 等同 shell 凭据，不要贴到日志/截图/聊天。
- 默认只绑定 `127.0.0.1`；远程调试用 SSH 隧道：
  `ssh -L 48790:127.0.0.1:48790 user@host`。
- 文件命令限制在游戏目录内（`allowPathEscape` 默认关闭）。
- 调试结束：停服 → 移出 jar；需要清凭据时删除 `RosettaRemoteDebugBridge/remote-token.txt`。
- 审计：服务端日志记录每次 `connection from` / `disconnected` / `<ip> -> <cmd>`。
