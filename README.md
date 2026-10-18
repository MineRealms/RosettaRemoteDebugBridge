# RosettaRemoteDebugBridge — Fabric 1.20.1

运行期 Java 脚本引擎 + 远程调试桥。把 `.java` 源码放进游戏目录的
`RosettaRemoteDebugBridge/`，模组会在内存里用 ECJ 编译并执行，无需 JDK、无需打包；
同时提供 TCP 远程控制面（逐行 JSON 协议），供自动化测试直接调用游戏内代码。

| 项 | 值 |
|---|---|
| 游戏 / 加载器 | Minecraft 1.20.1 / Fabric Loader 0.16.x |
| Java | 17 |
| 产物 | `rosetta_remote_debug_bridge-1.0.0-fabric.jar`（自包含，含 ECJ 编译器） |
| 许可 | MIT（见 `LICENSE.txt`，含第三方组件声明） |
| 分支 | `fabric-1.20.1`（`master` 仍为 Forge 47.x 版本） |

## 这个分支支持什么

- **远程 `exec`**：编译并执行一段 Java 方法体，在服务器线程上运行，返回 `toString()`。
- **`console`**：执行任意原版命令并回传 `latest.log` 的新增输出。
- **脚本目录**：`startup/`、`server/`、`client/`，每个类实现 `public static void init()`。
- **热重载**：`/java reload [startup|server|client]`（按 ClassLoader 注销旧监听器）。
- **文件与反射**：`upload` / `read` / `tail` / `ls` / `reflect` / `plugins`。
- **事件总线**：`@RosettaEventSubscriber` + `@RosettaSubscribeEvent`（不依赖 Forge Event）。

### 不再包含（相对 Forge 分支）

动态 Mixin / CoreMod、Forge 事件桥（197 个事件）、Bukkit/Mohist 适配、插件热更新、
CRD 客户端调试通道。对应命令会返回明确的 `unsupported` 错误。

## 远程控制面

服务器启动（集成服务器或专用服务器）后监听 `127.0.0.1:48790`：

```json
{"token":"<token>","cmd":"exec","args":{"code":"return com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge.getServer().getPlayerCount();"}}
```

- 地址/端口：`-Drosetta.remote.bind` / `-Drosetta.remote.port`
- Token：`RosettaRemoteDebugBridge/remote-token.txt`，或 `-Drosetta.remote.token`
- 该端口等同于任意代码执行，默认只绑定回环地址。

`exec` 的代码体运行在 `Object run(Object plugin, Object[] args)` 里，
第一参数在 Fabric 版本为 `null`；用 `RosettaRemoteDebugBridge.getServer()`
拿到当前服务器实例（已在本线程，可直接调用）。

## 构建与运行

```bash
./gradlew build          # build/libs/rosetta_remote_debug_bridge-1.0.0-fabric.jar
./gradlew runServer      # 专用服务器（首次需 run-server/eula.txt）
./gradlew runClient      # 客户端（开发环境）
```

冒烟测试（需要已启动的服务器）：

```bash
python tools/fabric_smoke_test.py --token-file run-server/RosettaRemoteDebugBridge/remote-token.txt
```

## 文档

- [Fabric 使用手册](docs/USAGE_FABRIC.md) — 安装、脚本、全部命令、自动化测试
- [Fabric 移植说明与验证记录](docs/FABRIC.md)
- [技术手册](docs/ARCHITECTURE.md) · [测试与验收](docs/TESTING.md) · [CRD 设计](docs/CRD-DESIGN.md)（Forge 分支内容）
