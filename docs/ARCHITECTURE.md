# RosettaRemoteDebugBridge 技术手册

> 适用版本:`rosetta_remote_debug_bridge-1.0.0`(Minecraft 1.20.1 / Forge 47.x / Java 17)
> 覆盖范围:核心脚本引擎、脚本 API、静态与动态 Mixin、远程控制面、Bukkit/Mohist 适配、构建打包与安全模型。
> 相关文档:[使用手册](USAGE.md) · [测试与验收](TESTING.md) · [README](../README.md)

---

## 目录

1. [概述](#1-概述)
2. [总体架构](#2-总体架构)
3. [脚本引擎](#3-脚本引擎)
4. [脚本 API](#4-脚本-api)
5. [Mixin 子系统](#5-mixin-子系统)
6. [远程控制面](#6-远程控制面)
7. [游戏内命令系统](#7-游戏内命令系统)
8. [日志与错误](#8-日志与错误)
9. [构建与打包](#9-构建与打包)
10. [安全模型](#10-安全模型)
11. [类清单与包结构](#11-类清单与包结构)
12. [术语表](#12-术语表)

---

## 1. 概述

### 1.1 定位

RosettaRemoteDebugBridge 由四个子系统组成:

| 子系统 | 作用 |
|---|---|
| **脚本引擎** | 运行期把 `RosettaRemoteDebugBridge/{startup,server,client}` 下的 Java 源码用内嵌 ECJ 编译进内存并执行,支持热重载 |
| **脚本 API** | 事件总线、Forge 事件桥、映射反射助手、网络/注册封装、资源与数据包注入 |
| **Mixin 注入** | 静态 Mixin(构建期接线)与动态 Mixin/CoreMod(运行期源码编译注册,依赖 agent,自动降级) |
| **远程控制面** | TCP 行 JSON 协议上的 14 个命令:远程执行 Java、控制台命令、反射调用、文件操作、插件热更新、Coder 插件桥 |

### 1.2 运行环境

| 项 | 值 |
|---|---|
| Minecraft / Forge | 1.20.1 / 47.4.10(构建), 运行要求 `[47,)` |
| Java | 17(脚本按 `-source 17 -target 17` 编译) |
| 服务端 | 纯 Forge 或 Mohist 混合端(Bukkit 适配为可选运行时增强) |
| modId / 分组 | `rosetta_remote_debug_bridge` / `com.rosetta.remotedebugbridge` |
| 许可 | MIT(第三方组件见 `LICENSE.txt`) |

### 1.3 依赖与重定位

| 依赖 | 所在 | 命名空间 | 说明 |
|---|---|---|---|
| Eclipse ECJ | `libs/rainapi-repack-1.0.2.jar` | `net.rain.repack.ecj.*` | 脚本内存编译(自包含,无需 JDK) |
| JavaParser | 同上 | `net.rain.repack.javaparser.*` | MCP→SRG 源码级转换 |
| Mixin 分支 | `libs/mixin-0.8.5-dev.jar` | `org.spongepowered.rain.asm.*` | 动态 Mixin 管线与扩展 API;分支新 `META-INF/services` 已剥离 |
| Forge 原生 Mixin | Forge 运行时 | `org.spongepowered.asm.*` | 静态 Mixin 的实际执行者;少量工具类 |
| Bukkit 桩 | `src/bukkitStubs` | `org.bukkit.plugin.Plugin` | 仅编译期,不进 jar;运行时解析 Mohist 真类 |
| 映射表 | 随包资源 | `assets/mappings/map/mappings.tsrg` | 运行期加载约 6,674 类 / 54,309 方法 |

上述 `net.rain.repack.*` 与 `org.spongepowered.rain.asm.*` 为**外部依赖既有命名空间**,
在项目重命名时刻意保留,避免破坏二进制与反射约定。

### 1.4 产物

- `build/libs/rosetta_remote_debug_bridge-1.0.0.jar`:**自包含**(内嵌 ECJ/JavaParser 与 Mixin 分支)
- 运行期不需要额外安装 RainAPI;Bukkit 相关功能在 Mohist 上自动激活,纯 Forge 上惰性禁用

---

## 2. 总体架构

### 2.1 分层

```
入口层        RosettaRemoteDebugBridge(@Mod)
              静态块: RosettaCore 初始化 + STARTUP 脚本
              commonSetup / clientSetup / onServerStarting / onServerStarted / onServerStopping
              EVENT_BUS(RosettaEventBus 单例)
核心编排层    RosettaCore: 目录创建、示例生成、按 ScriptType 装载、热重载、
              资源/数据包注册、网络通道初始化
脚本引擎      JavaScriptLoader → McpToSrgTransformer → JavaSourceCompiler(ECJ)
              → CompiledClass(allClasses) → DynamicClassLoader
              ClassReplacementManager(仅编译)
脚本 API      RosettaEventBus / RosettaSubscribeEvent / RosettaEventSubscriber
              ForgeEventBridge / MinecraftHelper / NetworkUtils / RegUtils
              NexusBukkit / RosettaResourcePack
Mixin         StaticMixinProbe + ChickenAiStepProbeMixin(静态,构建期接线)
              DynamicMixinLoader / MixinManager / BytecodeProviderWrapper / MixinInfoInjector
远程控制面    RemoteBridge(TCP) → NexusTask(exec) / BukkitAdapter / CoderAdapter
              PluginReloader(插件热更新) / Reflect(零链接反射工具)
支撑          RosettaLogger / ScriptErrorCollector / RuntimeModuleOpener / UnsafeClassDefiner
```

### 2.2 生命周期

| 时机 | 动作 |
|---|---|
| 类静态块 | `new RosettaCore()`(目录、网络通道)→ `loadScripts(STARTUP)`,执行 `startup/` 脚本 |
| `FMLCommonSetupEvent` | 空实现(保留) |
| `FMLClientSetupEvent` | 执行 `client/` 脚本 |
| `ServerStartingEvent` | 执行 `server/` 脚本;随后按 ClassLoader 清理上一代 Bukkit 注册 |
| `ServerStartedEvent` | `RemoteBridge.start(server)` 监听 TCP(默认 `127.0.0.1:48790`) |
| `ServerStoppingEvent` | `RemoteBridge.stop()` |
| `AddPackFindersEvent` | 注册 `rosetta_assets` / `rosetta_data` |

要点:STARTUP 脚本在**类初始化期**运行(早于 Forge 生命周期事件);所有执行路径的异常
均收口到错误收集器,不崩服。

### 2.3 运行时目录

```
<工作目录>/RosettaRemoteDebugBridge/
├─ startup|server|client/     脚本目录(UTF-8,递归扫描)
├─ assets|data/               资源包/数据包(实时读盘,pack id: rosetta_assets / rosetta_data)
├─ mixins/                    动态 Mixin 源码目录(依赖 agent)
├─ remote-token.txt           桥鉴权 token(28 字符随机生成)
└─ README.txt                 自动生成说明
<工作目录>/logs/Rosetta/{startup,server,client}.log   分类日志(UTF-8)
```

---

## 3. 脚本引擎

### 3.1 加载流水线

```
Files.walk(脚本目录)
  → 过滤 mixins/、replace/ 子路径
  → 按绝对路径排序(确定性)
  → 读取源码(UTF-8)
  → [SRG 运行时] McpToSrgTransformer.transformSource(MCP→SRG)
  → extractClassName()
  → JavaSourceCompiler.compileFromString()      (ECJ 纯内存)
  → CompiledClass{className, bytecode, allClasses}
  → DynamicClassLoader 注册全部 class(真实二进制名,含 $)
  → @RosettaEventSubscriber 自动注册
  → executeClass(): 入口方法发现与调用
```

### 3.2 源码发现与过滤

`JavaScriptLoader.loadJavaScripts(Path)`:

- 递归收集 `*.java`;`mixins/` 与 `replace/` 子路径分别归 Mixin 与类替换管线;
- 加载前按路径排序,保证初始化顺序确定;
- 输出 `Compiled x/y` 统计;扫描异常写入错误收集器。

### 3.3 运行时命名探测与 MCP→SRG 转换

- `MinecraftHelper.isSrgRuntime()`:反射 `ItemStack` 的 `EMPTY`(官方映射)/`f_41583_`(SRG)
  判断当前命名模式并缓存;
- **仅 SRG 运行时**对脚本源码做名称改写(JavaParser AST:方法调用/字段访问/简单名,
  沿继承链解析,包白名单内),dev 官方映射环境跳过;
- 生产服因此可直接用官方名写脚本。

### 3.4 内存编译(ECJ)与 classpath

- 编译器为内嵌 `net.rain.repack.ecj.internal.compiler.tool.EclipseCompiler`;
- 编译选项:`-source 17 -target 17 -encoding UTF-8 -warn:none -proceedOnError
  -g:vars,lines,source -preserveAllLocals`;Mixin 注解处理器仅在
  `-Drosetta.mixin.annotationProcessors=true` 时启用(默认 `-proc:none`);
- `buildClassPath()` 顺序:
  1. `java.class.path`
  2. `jdk.module.path`(dev 下补全 MC/Forge 模块 jar)
  3. 模组自身 code source(脚本可引用本节所述 API)
  4. TCCL/系统类加载器 URL 链(`URLClassLoader` 与 `BuiltinClassLoader.ucp`)
  5. `ModList` 中所有 mod 文件
  6. 已知 MC 类反查 jar + 游戏根/`mods/`/`libraries/` 扫描

### 3.5 FileManager 内存化(关键正确性)

ECJ 的 `EclipseCompilerImpl.getCompilationUnits()` 对不在 source location 中的编译单元会做
`new File(unit.getName()).exists()` 磁盘兜底,失败即抛 `unit.missing`("File ... is missing")。
`CustomFileManager` 通过以下覆盖保证**纯内存编译**,与包名/目录名/工作目录无关:

- `hasLocation(StandardLocation.SOURCE_PATH)` → `true`;
- `contains(SOURCE_PATH, 内存源文件对象)` → `true`(其余委托默认实现)。

### 3.6 多 class 注册与真实二进制名

- `CustomFileManager.getAllCompiledClasses()` 收集全部 CLASS 输出;
- 每个产物的**二进制名由字节码常量池 `this_class` 解析**(`JavaSourceCompiler.readBinaryClassName`),
  而非采信编译器给出的名字,确保内部类 `Outer$Inner` 正确命名;
- `JavaScriptLoader` 将 `allClasses` 全量注入 `DynamicClassLoader`,避免反射解析方法签名时
  `NoClassDefFoundError`。

### 3.7 入口方法与执行语义

1. `public static` 无参:`init → initialize → onLoad → load → register`;
2. `public static` 且参数为 `FMLJavaModLoadingContext`;
3. 否则尝试无参构造实例化(非抽象/非接口)。

执行异常(`Throwable`)一律记录到分类日志与 `ScriptErrorCollector`,并解包
`InvocationTargetException`,保证 `/java errors` 中消息可读。

### 3.8 热重载与 ClassLoader 记账

`/java reload <type>` 流程:

1. 清空该类型错误桶;
2. **注销上一代注册物**:
   - `RosettaEventBus.unregisterByClassLoader(旧加载器)`;
   - `BukkitAdapter.cleanupClassLoader(旧加载器)`(监听器 + 命令,详见 §6.5);
3. 重建 `JavaScriptLoader`(ECJ、classpath、`DynamicClassLoader`);
4. 重新扫描/编译/执行,重载后不再产生重复监听器或命令。

### 3.9 类替换管线(仅编译)

`ClassReplacementManager` 编译 `replace/` 源码并输出到 `<CWD>/.rainjava_replacements`
(历史目录名),支持多 class 产物;运行期应用替换类需要 agent/class-transformer,
当前版本输出明确警告,不提供运行时替换。

### 3.10 启动覆盖

- `startup/` 脚本在静态块执行一次(早于 Forge 事件),可用于早期行为定制;
- `loadScripts` 带 `loadedFlags` 去重;显式 `reload` 后重新加载。

---

## 4. 脚本 API

### 4.1 事件总线(`eventbus`)

| 注解 | 属性 | 说明 |
|---|---|---|
| `@RosettaSubscribeEvent` | `priority`(HIGHEST…MONITOR)、`receiveCanceled` | 标注 `static`、`public`、单参数方法 |
| `@RosettaEventSubscriber` | `bus`(仅展示) | 类级自动注册 |

- 分发:沿事件类超类+接口 BFS,按优先级同步反射调用;单监听器异常隔离;
- 取消语义仅对 Forge `Event` 生效;`receiveCanceled=false` 且非 MONITOR 的监听器在取消后跳过;
- 注销:`unregister(Class)`、`unregisterAll()`、`unregisterByClassLoader(ClassLoader)`;
- 查询:`getRegisteredClasses/getRegisteredEventTypes/getTotalListenerCount`。

### 4.2 Forge 事件桥(`api.ForgeEventBridge`)

把 MOD 总线 6 个事件与 FORGE 总线 191 个事件原样转发到 `RosettaEventBus`;
脚本可用同一注解体系监听 Tick、玩家、生物、方块、世界、注册与数据等事件。

### 4.3 映射反射助手(`script.utils.MinecraftHelper`)

- 映射数据来自随包 `mappings.tsrg`(TSRG2),含类/字段/方法(简单键+描述符键)/返回类型;
- `isSrgRuntime()` 命名探测;`clearCache()` 重置;
- 访问 API:`getStaticField/setStaticField/getField/setField`、
  `invokeStaticMethod/invokeMethod`、`findFieldType`;
- 方法解析:签名感知缓存、父类链查找、基本类型解箱匹配、重载消歧(歧义时告警)。

### 4.4 网络封装(`script.util.NetworkUtils`)

- 通道在 `RosettaCore` 构造期以 modId 自动初始化(协议版本 `"1"`);
- 每个消息类单次注册;接收侧按 `getReceptionSide()` 派发 `handleServer/handleClient`;
- `PacketBuilder` DSL + 命名频道 `QuickPacket`;处理器用
  `onServerReceive/onClientReceive(channel, handler)` 注册;
- 分发:`sendToServer/sendToPlayer/sendToAllPlayers/sendToNearby/sendToDimension`。

### 4.5 注册封装(`script.util.RegUtils`)

- `init(modId, modEventBus)` 建立 `BLOCKS/ITEMS/ENTITY_TYPES` 三个 `DeferredRegister`;
- `createRegister(...)` 自定义注册表会保存引用并可用 `getCustomRegister(modId, name)` 取回;
- 便捷方法覆盖方块/物品/实体/属性复制等。

### 4.6 Bukkit 注册门面(`net.NexusBukkit`)

脚本侧统一入口(详见 §6.5):

```java
NexusBukkit.registerListener(myListener);
NexusBukkit.registerCommand("rosetta", myCommand);
// /java reload server 时按脚本 ClassLoader 精确回收
```

### 4.7 资源/数据包注入

- `RosettaRemoteDebugBridge/assets` → `rosetta_assets`(CLIENT_RESOURCES,TOP,required);
- `RosettaRemoteDebugBridge/data` → `rosetta_data`(SERVER_DATA,TOP,required);
- `RosettaResourcePack` 实时读盘,改文件后 `F3+T` 生效;
- 数据包注意:1.20.1 谓词目录必须复数 `predicates/`;JSON 不得带 BOM;
  Mohist 的 `/reload` 不重载数据包,需要重启服务端。

---

## 5. Mixin 子系统

### 5.1 静态 Mixin(构建期接线,当前启用)

- 构建:MixinGradle `0.7.38` + `annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'`;
- 配置:`mixin { add sourceSets.main, 'rosetta_remote_debug_bridge.refmap.json';
  config 'rosetta_remote_debug_bridge.mixins.json' }`,MixinGradle 自动写入清单
  `MixinConfigs` 并把 refmap 打进产物;
- 配置内容:`src/main/resources/rosetta_remote_debug_bridge.mixins.json`,包
  `...mixin.staticprobe`,目标 `ChickenAiStepProbeMixin`(`@Mixin(Chicken.class)`,
  `@Inject(method="aiStep", at=@At("HEAD"))`);
- 探针:`StaticMixinProbe` 统计回调次数并节流输出,用于证明静态注入生效;
- **约束**:Mixin 回调 helper(如 `StaticMixinProbe`)必须放在 mixin 配置包之外,
  否则 ModLauncher 排除该包导致 `NoClassDefFoundError`;
- 启用是无条件的(随 jar 生效);`required:true` 表示应用失败即报错。

### 5.2 动态 Mixin 与 CoreMod(运行期,依赖 agent)

设计链路:

```
RosettaRemoteDebugBridge/mixins/ 源码
 → DynamicMixinLoader: MixinProcessorHolder → BytecodeProviderWrapper
 → JavaSourceCompiler 编译 → 字节码登记(双键)
 → MixinConfig.createDynamic("dynamic_rosetta_<uuid>","rosetta.mixins",1000,false)
 → DefaultMixinConfigPlugin.registerDynamicMixin(name)
 → 注入 extensions/service/plugin → 挂入 MixinProcessor.configs
 → 目标类加载时经 IClassBytecodeProvider 命中内存字节码
```

磁盘链路(`MixinManager`):扫描 → 增量判断 → 编译落盘 `.rosetta_mixin/rainjava/mixins/`
→ 生成 `rosetta.mixins.json` 与 `rosetta.refmap.json` → 编译状态 `compile_state.json`
→ 校验并提示重启。

外部 `rainjava-core`(可选)提供 ModLauncher 服务与自附加 agent,补丁原生 Mixin 的
`MixinInfo.loadMixinClass`,在正常提供器失败时从 `<gameDir>/.rosetta_mixin/<name>.class`
读取(磁盘兜底)。

**降级策略**:`JavaScriptLoader.processMixins()` 先探测
`MixinProcessorHolder.isAvailable()`;不可用时输出明确警告(脚本/事件不受影响),
可用时尝试加载并把异常收口到错误收集器。

### 5.3 模块绕过工具

| 组件 | 手段 | 说明 |
|---|---|---|
| `RuntimeModuleOpener` | `implAddOpensToAllUnnamed` / `Unsafe` 修改 `Module.openPackages` | 每次创建脚本加载器调用,失败降级日志 |
| `ModuleAccessHelper` | `implAddOpens/implAddReads` | 保留工具 |
| `UnsafeClassDefiner` | trusted `MethodHandles.Lookup` + `ClassLoader.defineClass` | 现代实现 |

---

## 6. 远程控制面

### 6.1 组成与线程模型

| 类 | 职责 |
|---|---|
| `RemoteBridge` | TCP 服务;Accept 守护线程 + 每连接守护线程;命令分发与会话 |
| `NexusTask` | `exec` 任务契约:`Object run(org.bukkit.plugin.Plugin, Object[])` |
| `BukkitAdapter` | 全反射 Bukkit/Mohist 适配(零编译链接),含注册记账与自检 |
| `NexusBukkit` | 脚本侧注册门面(委托 BukkitAdapter) |
| `CoderAdapter` | 通过 Coder 插件 `PluginClassLoader` 反射其 API |
| `PluginReloader` | 插件热更新(卸载/写盘/重载) |
| `Reflect` | 反射工具(load/call/field/findMethod,参数打分匹配) |

- 桥由 `ServerStartedEvent` 启动、`ServerStoppingEvent` 停止;进程内单例、幂等;
- Socket I/O 永不占用服务器线程;需要游戏状态的操作用 `onServerThread(task, timeout)`
  投递到服务器线程执行;
- 所有连接/断开/命令名写入服务端日志(审计)。

### 6.2 线协议与鉴权

- TCP,**每行一个 JSON**,一请求一连接;
- 请求:`{"token":"...","cmd":"...","args":{...}}`;
- 成功 `{"ok":true,"result":...}`;失败 `{"ok":false,"error","exception","cause"}`;
- token 校验:系统属性 `-Drosetta.remote.token` > `<工作目录>/RosettaRemoteDebugBridge/remote-token.txt`
  > 随机生成(28 字符,SecureRandom,写回文件并打印日志);
- 单连接 token 连错 3 次断开;单行上限 64 MiB;文本结果截断 200,000 字符;
- 超时:`console` 30s、`exec` 60s、`reflect` 30s、`update` 120s。

### 6.3 命令参考

| 命令 | 参数 | 说明 |
|---|---|---|
| `ping` | — | 桥/Java/Bukkit/玩家/插件状态 |
| `console` | `{"command": "..."}` | 控制台执行命令;返回执行后日志增量(Bukkit 走 ConsoleCommandSender,纯 Forge 走原版分发器) |
| `exec` | `{"code":"<方法体>","args":[...]}` | ECJ 内存编译执行;返回结果字符串与耗时、生成类名 |
| `reflect` | `{"class","method","target","args"}` | 反射调用;args 支持裸字符串或 `{type,value}` |
| `upload` | `{"path","base64"}` | 写文件(限制在工作目录内) |
| `read` | `{"file","offset","max"}` | 读文件(返回 base64) |
| `tail` | `{"file","lines"}` | 读日志尾部 |
| `ls` | `{"dir"}` | 列目录 |
| `plugins` | — | Bukkit 插件列表;纯 Forge 退化为 ModList |
| `enable` / `disable` | `{"name"}` | 启停插件(无 Bukkit 报错) |
| `update` | `{"name","path","base64"}` | 插件热更新(见 §6.7) |
| `listener` | `{"action":"status\|cleanup\|restore"}` | 适配层监听器管理 |
| `coder` | `{"action":"list\|api\|run",...}` | Coder 插件桥 |

### 6.4 `exec` 执行契约

- 提交内容为**方法体**(需 `return`;空则等价 `return null;`);
- 生成类 `rosetta.remote.generated.RosettaTask<nanotime>`;
- Mohist 上自动 `import org.bukkit.*` 且实现 `NexusTask`,参数 `plugin` 为
  `BukkitAdapter.ownerPlugin()`;纯 Forge 上 `plugin` 为 `null`;
- SRG 运行时自动做 MCP→SRG 源码转换(可直接写官方名);
- 编译依赖:内嵌 ECJ(无需 JDK);类加载用一次性 `DynamicClassLoader`(父为 TCCL)。

### 6.5 BukkitAdapter 与 NexusBukkit

- `present()` 惰性探测 `org.bukkit.Bukkit`;纯 Forge 上所有操作惰性禁用;
- owner 插件优先 `PluginManager#getPlugin("Mohist")`,否则插件列表首个;
- 监听器注册优先 `com.mohistmc.api.ServerAPI#putBukkitEvents`,回退
  `PluginManager#registerEvents`;命令走 `CommandMap#register`;
- **按 ClassLoader 记账**:`TRACKED`/`TRACKED_COMMANDS` 以实例 ClassLoader 为键;
  `cleanupClassLoader(loader)` 返回 `[listenersRemoved, commandsRemoved]`,
  同时扫描 owner 的 `HandlerList` 兜底清理,并清理 `knownCommands` 中所有指向旧实例的键
  (含 `prefix:label` 双键);
- 自检:`init()` 现场 ECJ 编译一个取消 CREEPER 生成的自检监听器,
  `listener restore` 幂等恢复(用于验证事件链路与收尾清理)。

### 6.6 CoderAdapter

- 通过 `BukkitAdapter.pluginClassLoader("Coder")` 跨类加载器反射
  `dev.codestuff.coder.api.CoderAPI`;
- `list` 返回插件版本、API 类、加载器、方法数、单例可用性;
- `api` 反射调用 API 方法(参数自动转换);
- `run` 先执行 `coderc run <name>`,输出含 `Class not found` 时回退 `coderc compile <file>`。

### 6.7 PluginReloader 与插件热更新

`update` 顺序:

```
disablePlugin → HandlerList.unregisterAll → 摘除该插件 PluginCommand
→ removePermission → 从 SimplePluginManager(plugins/lookupNames)移除
→ URLClassLoader.close() → 写新 jar → 清理 JarFileFactory 缓存
→ loadPlugin → enablePlugin
```

副作用与风险:

- 插件静态状态不保留(新类加载器),持久数据必须落盘;
- 依赖 JDK 私有字段与 `sun.*`;被依赖插件或持有外部资源的插件可能出现半卸载状态;
- 更新失败时服务端仍在运行,但目标插件处于未启用状态。

### 6.8 安全

- 默认回环绑定;对外暴露必须显式 `-Drosetta.remote.bind=0.0.0.0` 并置于防火墙/SSH 隧道之后;
- 文件命令锁定工作目录,`allowPathEscape` 默认关闭;
- **token 等同服务器 shell 权限**;审计日志记录所有连接与命令名。

---

## 7. 游戏内命令系统

`/java` 与别名 `/j`,权限等级 2(`RosettaCommands.onRegisterCommands`):

| 命令 | 说明 |
|---|---|
| `/java` / `/java reload` | 重载全部脚本(顺序 SERVER→CLIENT→STARTUP) |
| `/java reload startup\|server\|client` | 按类型重载(含 ClassLoader 记账清理) |
| `/java errors [type]` | 错误/警告统计与前 5 条明细;附日志文件链接与错误屏链接 |
| `/java hand getId` | 主手物品注册名(点击复制) |
| `/java hand getClass` | 主手物品类名(点击复制) |

反馈约定:开始黄字、成功绿字、失败红字;失败附 `[Open Log]` 与
`[View Error Screen]`(`/java errors <type>`)。

---

## 8. 日志与错误

### 8.1 日志

- 分类文件:`<工作目录>/logs/Rosetta/{startup,server,client}.log`,UTF-8、截断模式、自动 flush;
- 格式 `[yyyy-MM-dd HH:mm:ss] [TYPE/LEVEL] message`,镜像到 log4j;
- 编译器原始输出独立成块(`=== Compiler Output ===`)。

### 8.2 错误模型

- `ScriptError`:类型/脚本类型/消息/文件/行号/时间戳/堆栈;
  `fromThrowable` 解包 `InvocationTargetException` 与 `ExceptionInInitializerError`;
- `ScriptErrorCollector`:按 `ScriptType` 分桶的并发容器,覆盖编译错误与运行期异常;
- 客户端错误屏:主菜单自动弹窗(STARTUP 错误,单次)、进世界聊天提示;
  支持打开脚本、复制堆栈、打开日志、错误/警告视图切换。

---

## 9. 构建与打包

### 9.1 Gradle 与插件

- ForgeGradle `[6.0,6.2)` + MixinGradle `0.7.38`;Gradle 8.8;Java toolchain 17;
- 编译输出英文错误(确保日志可读):`forkOptions.jvmArgs` 固定 `-Duser.language=en` 等。

### 9.2 影子依赖与排除

- `processResources { from zipTree('libs/rainapi-repack-1.0.2.jar'); from zipTree('libs/mixin-0.8.5-dev.jar') }`
  —— 依赖声明为 `compileOnly`,类在 dev 与产物中均属于 mod 模块;
- **必须 `exclude 'module-info.class'`**:Mixin 分支的 JPMS 描述符会再次注册名为
  `mixin` 的转换服务,与 Forge 冲突(`Duplicate key mixin` 崩溃);
- 无 Shadow / jarJar;`jar` 以 `reobfJar` 收尾;清单包含 `MixinConfigs`(MixinGradle 注入)。

### 9.3 Bukkit 编译桩

`src/bukkitStubs` 独立 source set,仅提供 `org.bukkit.plugin.Plugin` 空接口桩,
以 `compileOnly` 加入主源码编译;不进 jar,运行时解析 Mohist 真类。

### 9.4 测试入口

`build.gradle` 的 client 运行配置支持 `-PquickPlay=<world>` → 参数
`--quickPlaySingleplayer <world>`,用于无人值守测试(见 `docs/TESTING.md`)。

### 9.5 依赖 jar 的生成

`libs/` 仅保留两个**实际使用**的 jar,均由上游原件剥离生成(工作区脚本
`strip_rainapi.py` / `strip_mixin.py`):

| 产物 | 来源 | 剥离内容 |
|---|---|---|
| `rainapi-repack-1.0.2.jar` | 上游 RainAPI-1.0.2 | 仅保留 `net/rain/repack/**`(ECJ / JavaParser / 工具库) |
| `mixin-0.8.5-dev.jar` | 改造版 Mixin 0.8.5 分支 | 移除 `META-INF/services/**` 与 MANIFEST(避免与 Forge 转换服务重名) |

重建这两个 jar 需要上游原件(RainAPI-1.0.2 与 mixin-0.8.5 分支);仓库不再携带
未使用的原件副本(如未剥离的 RainAPI、旧版 ECJ/Equinox、原始 mixin-0.8.5)。

---

## 10. 安全模型

| 面 | 边界 |
|---|---|
| 脚本目录 | 写入者 = 游戏进程内任意代码执行;需限制目录权限 |
| 远程桥 | token 强制;默认回环;文件命令锁工作目录;审计日志 |
| 静态 Mixin | 随 jar 生效,构建期可控;运行时无开关 |
| 动态 Mixin | 依赖外部 agent;能力等同 CoreMod(可改任意字节码) |
| 插件热更新 | 可替换插件 jar;失败可能半卸载 |
| 命令 | `/java` 需权限 2;桥命令需 token(与 OP 无关) |

适用场景:单人 / 整合包 / 调试与远程运维;不适用于多租户或不受信脚本环境。

---

## 11. 类清单与包结构

| 包 | 类 |
|---|---|
| `com.rosetta.remotedebugbridge` | `RosettaRemoteDebugBridge`(入口) |
| `.core` | `RosettaCore`、`ScriptType` |
| `.script` | `JavaScriptLoader`、`JavaSourceCompiler`(+内部类)、`DynamicClassLoader`、`CompiledClass`、`ClassReplacementManager`、`DynamicMixinLoader`、`MixinUtils` |
| `.script.helper` | `BytecodeProviderInstaller`、`BytecodeProviderWrapper`、`MixinServiceHelper`、`MixinConfigHelper`、`ModuleAccessHelper`、`RuntimeModuleOpener`、`UnsafeClassDefiner` |
| `.script.transformer` | `McpToSrgTransformer` |
| `.script.util` | `NetworkUtils`(+内部类)、`RegUtils` |
| `.script.utils` | `MinecraftHelper`、`MC` |
| `.mixin` | `MixinManager`(+内部类)、`MixinJarBuilder`、`MixinDebugHelper`、`MixinDiagnosticTool`、`RosettaMixinConnector`、`StaticMixinProbe` |
| `.mixin.staticprobe` | `ChickenAiStepProbeMixin`(静态 Mixin) |
| `.mixin.refmap` | `RefMapGenerator` |
| `.eventbus` | `RosettaSubscribeEvent`、`RosettaEventSubscriber`、`bus.RosettaEventBus` |
| `.api` | `ForgeEventBridge` |
| `.command` | `RosettaCommands` |
| `.client` | `RosettaClientEvents`、`RosettaErrorScreen` |
| `.resources` | `RosettaResourcePack` |
| `.logging` | `RosettaLogger`、`ScriptError`、`ScriptErrorCollector` |
| `.net` | `RemoteBridge`、`NexusTask`、`NexusBukkit`、`BukkitAdapter`、`CoderAdapter`、`PluginReloader`、`Reflect` |
| `.utils` | `PathUtils` |
| `cpw.mods.modlauncher.MixinCore` | `MixinInfoInjector`(磁盘兜底,包名保留) |

---

## 12. 术语表

| 术语 | 含义 |
|---|---|
| 脚本 | 放入 `RosettaRemoteDebugBridge/{startup,server,client}` 的 `.java` 文件 |
| 入口方法 | `init/initialize/onLoad/load/register`(public static,无参或 FML 上下文参数) |
| 远程桥 / 控制面 | `RemoteBridge` 提供的 TCP 行 JSON 接口(代号 rosetta-nexus) |
| owner 插件 | Bukkit 侧承载注册归属的插件(Mohist 环境下为 Mohist 插件) |
| 记账清理 | 按脚本 ClassLoader 精确回收监听器与命令的机制 |
| 静态 Mixin | 构建期由 MixinGradle/AP 接线的 Mixin(当前 `ChickenAiStepProbeMixin`) |
| 动态 Mixin | 运行期编译并注册的 Mixin(依赖外部 agent,当前降级) |
| 影子依赖 | 通过 `processResources from zipTree` 打进 mod 的外部库(ECJ/Mixin 分支) |
