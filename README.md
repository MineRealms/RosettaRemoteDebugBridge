# RosettaRemoteDebugBridge

运行期 Java 脚本引擎 + 远程调试桥 + Mixin 注入的 Minecraft Forge 模组。

| 项 | 值 |
|---|---|
| 游戏 / 加载器 | Minecraft 1.20.1 / Forge 47.x(兼容 Mohist 混合端) |
| Java | 17 |
| 产物 | `rosetta_remote_debug_bridge-1.0.0.jar`(自包含,含 ECJ 编译器) |
| 许可 | MIT(见 `LICENSE.txt`,含第三方组件声明) |
| 文档 | [使用手册](docs/USAGE.md) · [技术手册](docs/ARCHITECTURE.md) · [测试与验收](docs/TESTING.md) |

---

## 这是什么

把普通 `.java` 源码放进游戏目录的 `RosettaRemoteDebugBridge/` 文件夹,模组会在启动 / 开服 /
客户端初始化时**在内存中编译并执行**;无需系统 JDK、无需打包、`/java reload` 即可热重载。
同时提供一个 **TCP 远程控制面**(逐行 JSON 协议),可远程执行 Java、执行控制台命令、
热更新 Bukkit 插件;内置静态 Mixin 注入与 Bukkit/Mohist 适配。

---

## 功能全景

```mermaid
mindmap
  root((RosettaRemoteDebugBridge))
    脚本引擎
      ECJ 内存编译 无需 JDK
      热重载 免重启
      编译与运行期错误收集
      多 class 与内部类注册
    脚本 API
      事件总线 注解自动注册
      Forge 事件桥 197 个事件
      映射感知反射助手
      网络封装 命名频道
      注册封装 方块物品实体
      资源与数据包实时注入
    静态 Mixin
      MixinGradle 接线
      refmap 注解处理器
      探测 Mixin Chicken.aiStep
    动态 Mixin 与 CoreMod
      内存编译与动态注册
      磁盘兜底 .rosetta_mixin
      依赖外部 agent 自动降级
    远程控制面
      TCP 行 JSON 协议
      Token 鉴权 回环绑定
      14 个远程命令
      Bukkit 适配与插件热更新
      Coder 插件桥
    运维
      分类 UTF-8 日志
      客户端错误屏
      无人值守自动化测试
```

---

## 运行时架构

```mermaid
mindmap
  root((运行时组件))
    入口层
      RosettaRemoteDebugBridge 静态块执行 STARTUP
      RosettaEventBus 脚本事件总线
    核心编排层
      RosettaCore 目录初始化与按类型装载
      ScriptType startup server client
    脚本引擎
      JavaScriptLoader 发现编排执行
      JavaSourceCompiler ECJ 与 classpath
      DynamicClassLoader 内存类加载
      McpToSrgTransformer 命名转换
      CompiledClass 多 class 产物
    脚本 API
      RosettaSubscribeEvent 注解
      ForgeEventBridge 事件转发
      MinecraftHelper 映射反射助手
      NetworkUtils 网络封装
      RegUtils 注册封装
      RosettaResourcePack 资源包
    远程控制面
      RemoteBridge TCP 控制面
      NexusTask exec 任务契约
      BukkitAdapter 全反射适配
      NexusBukkit 脚本注册门面
      CoderAdapter Coder 插件桥
      PluginReloader 插件热更新
    支撑
      RosettaLogger UTF-8 分类日志
      ScriptErrorCollector 错误桶
      StaticMixinProbe 静态 Mixin 探针
```

### 脚本加载与热重载

```mermaid
flowchart LR
    A[扫描 .java] --> B[路径排序]
    B --> C{SRG 运行时}
    C -- 是 --> D[MCP 转 SRG 源码]
    C -- 否 --> E[保留官方名]
    D --> F[ECJ 内存编译]
    E --> F
    F --> G[按字节码二进制名注册全部 class]
    G --> H[发现并调用入口方法]
    H --> I[异常收口到错误收集器]
    R1["/java reload"] --> R2[按 ClassLoader 注销监听器与命令]
    R2 --> R3[清空错误桶]
    R3 --> R4[重建编译器与类加载器]
    R4 --> A
```

### 服务器生命周期

```mermaid
flowchart LR
    S0[类静态块] --> S1[RosettaCore 初始化 目录与网络通道]
    S1 --> S2[STARTUP 脚本]
    S2 --> S3[ClientSetup 执行 CLIENT 脚本]
    S3 --> S4[ServerStarting 执行 SERVER 脚本]
    S4 --> S5[ServerStarted 启动远程桥监听]
    S5 --> S6[ServerStopping 停止远程桥]
```

### 远程桥请求流程

```mermaid
flowchart TB
    P[Python 客户端 rosetta_remote.py] -->|TCP 127.0.0.1:48790| S[RemoteBridge Accept 线程]
    S --> T{Token 校验}
    T -- 失败 --> X[错误响应 连错 3 次断连]
    T -- 通过 --> D[dispatch 分发命令]
    D --> E[exec ECJ 编译执行]
    D --> C[console 控制台命令]
    D --> L[listener coder plugins update 等]
    E --> ST[投递到服务器线程]
    C --> ST
    ST --> W[写服务端审计日志]
    ST --> R[返回行 JSON 响应]
```

---

## 目录结构

```mermaid
mindmap
  root((工程与运行目录))
    仓库
      src/main/java com.rosetta.remotedebugbridge
      src/main/resources mixins.json 与映射表
      src/bukkitStubs Bukkit 编译桩
      libs 影子依赖 jar
      autotest 无人值守测试脚本
      docs 使用 技术 测试文档
      legacy 归档工具与旧插件
    服务端工作目录
      RosettaRemoteDebugBridge
        startup 启动脚本
        server 服务端脚本
        client 客户端脚本
        assets 资源包
        data 数据包
        mixins 动态 Mixin 源码
        remote-token.txt 桥凭据
      logs/Rosetta
        startup.log
        server.log
        client.log
      plugins Bukkit 插件
      plugins-disabled 退役的旧桥插件
```

---

## 快速开始

### 1. 安装

把 `rosetta_remote_debug_bridge-1.0.0.jar` 放入服务端 `mods/`(纯 Forge 或 Mohist 均可)。

### 2. 首次启动

服务端工作目录会生成:

```
RosettaRemoteDebugBridge/
├─ startup/ server/ client/    脚本目录
├─ assets/ data/               资源与数据包
├─ mixins/                     动态 Mixin 源码目录
├─ remote-token.txt            28 字符随机 token
└─ README.txt                  目录说明
logs/Rosetta/{startup,server,client}.log   分类日志
```

启动日志关键行:

```
[INFO] RosettaRemoteDebugBridge initializing...
[INFO] Remote bridge listening on 127.0.0.1:48790 - token auth enforced, loopback-only by default.
```

### 3. 写第一个脚本

`RosettaRemoteDebugBridge/server/Hello.java`:

```java
package rosetta.server;

public class Hello {
    public static void init() {
        System.out.println("Hello from RosettaRemoteDebugBridge!");
    }
}
```

进入世界或执行 `/java reload server` 即可生效。脚本入口方法按
`init → initialize → onLoad → load → register` 顺序查找(可带
`FMLJavaModLoadingContext` 参数)。

### 4. 远程连接

```powershell
python legacy/tools/rosetta_remote.py --port 48790 --token <TOKEN> ping
```

```json
{
  "server": "127.0.0.1:48790",
  "bridge": "rosetta-nexus rosetta_remote_debug_bridge",
  "java": "17.0.18",
  "bukkit": true,
  "players": 0,
  "plugins": 3,
  "running": true
}
```

---

## 配置项(JVM 启动参数)

| 参数 | 默认 | 说明 |
|---|---|---|
| `-Drosetta.remote.port` | `48790` | 桥监听端口 |
| `-Drosetta.remote.bind` | `127.0.0.1` | 绑定地址;`0.0.0.0` 仅限防火墙/SSH 隧道场景 |
| `-Drosetta.remote.token` | token 文件 | 优先于 `RosettaRemoteDebugBridge/remote-token.txt` |
| `-Drosetta.remote.allowPathEscape` | `false` | 允许文件命令逃出服务端工作目录(不建议) |
| `-Drosetta.mixin.annotationProcessors` | `false` | 脚本编译开启 Mixin 注解处理器(ECJ AP) |

修改端口/token 需重启服务端(桥在 `ServerStartedEvent` 启动一次)。

---

## 命令速查

### 游戏内命令(需要 OP / 权限等级 2)

| 命令 | 作用 |
|---|---|
| `/java reload [startup\|server\|client]` | 热重载脚本(不带参数重载全部) |
| `/java errors [startup\|server\|client]` | 查看错误与警告(附日志链接) |
| `/java hand getId` | 显示手持物品注册名(可复制) |
| `/java hand getClass` | 显示手持物品类名 |

### 远程桥命令(TCP + token,完整说明见 [docs/USAGE.md](docs/USAGE.md))

| 类别 | 命令 |
|---|---|
| 基础 | `ping`、`console`、`plugins` |
| 代码执行 | `exec`(ECJ 内存编译)、`reflect`(反射调用) |
| 文件 | `upload`、`read`、`tail`、`ls` |
| 插件 | `enable`、`disable`、`update`(热更新) |
| 集成 | `listener`(status/cleanup/restore)、`coder`(list/api/run) |

---

## 构建与测试

```powershell
# 构建(输出 build/libs/rosetta_remote_debug_bridge-1.0.0.jar)
.\gradlew.bat build

# 开发客户端 / 服务端
.\gradlew.bat runClient
.\gradlew.bat runServer --nogui

# 无人值守自动化测试(自动进世界、执行断言、自动退出)
.\gradlew.bat runClient -PquickPlay=autotest
```

测试详情与验收记录见 [docs/TESTING.md](docs/TESTING.md)。

---

## 安全声明

- 远程桥的 **token 等同于服务器 shell 权限**:`exec` 可执行任意 Java,`console` 可执行任意命令,
  `update` 可热替换插件。切勿泄漏 token(日志、截图、聊天)。
- 默认仅监听回环 `127.0.0.1`;远程访问请使用 SSH 隧道:
  `ssh -L 48790:127.0.0.1:48790 user@server`。
- 脚本目录写入者等价于任意代码执行;请限制 `RosettaRemoteDebugBridge/` 目录权限。
- 调试完成后建议移出 mod jar 并删除 `remote-token.txt`。

---

## 来源与许可

本项目是上游 RainJava 1.0.0 的重构与重命名版本(上游作者 RainMelody / "Rain"),
在原命名空间之外新增了静态 Mixin、Bukkit/Mohist 适配与远程控制面。
项目代码以 **MIT** 许可发布,详见 [`LICENSE.txt`](LICENSE.txt);
随包分发的 ECJ(EPL-2.0)、Mixin(MIT)、JavaParser(Apache-2.0)以重定位形式提供,
许可证与归属见同文件第三方声明。

归档内容:`legacy/remote-bridge`(退役的旧 RosettaRemote 插件源码)、
`legacy/tools/rosetta_remote.py`(当前仍可用的 Python 客户端)、
`legacy/forge-kit` 与 `legacy/portal-probe`(联调用测试插件)。
