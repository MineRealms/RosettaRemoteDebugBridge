# RainJava 技术报告

> 分析对象:**RainJava 1.0.0**(Forge 1.20.1 / Minecraft 1.20.1 / Java 17)
> 依据:项目反编译源码、依赖 jar 的字节码级分析、运行时实测日志
> 报告定位:描述该 mod 的**系统设计、内部实现、脚本 API 与技术评估**

---

## 目录

1. [项目概述](#1-项目概述)
2. [总体架构](#2-总体架构)
3. [脚本执行引擎](#3-脚本执行引擎)
4. [脚本 API](#4-脚本-api)
5. [Mixin / CoreMod 子系统](#5-mixin--coremod-子系统)
6. [命令与权限](#6-命令与权限)
7. [日志与错误系统](#7-日志与错误系统)
8. [典型数据流](#8-典型数据流)
9. [技术评估](#9-技术评估)
10. [附录](#10-附录)

---

## 1. 项目概述

### 1.1 定位

RainJava 是一个**运行期 Java 脚本引擎 mod**:用户把普通 `.java` 源码放入游戏目录的
`RainJava/` 文件夹,mod 在启动、开服、客户端初始化等时机**在内存中编译并执行**这些脚本,
并提供 `/java` 命令族进行热重载与错误查看。其宣传的核心能力包括:

- 免打包、免重启的脚本开发闭环(内置 Eclipse ECJ 编译器,不依赖系统 JDK)
- 脚本以完整 Forge/原版类路径运行,可直接调用 Bukkit 式的 Minecraft/Forge API
- 脚本事件系统(Forge 事件全量桥接 + 自定义总线)
- 动态 Mixin / CoreMod 源码注入(见 §5,该能力在 1.0.0 中未接线)
- 资源包/数据包注入(脚本目录旁的 `assets/`、`data/` 直接生效)

### 1.2 运行环境与元数据

| 项 | 值 |
|---|---|
| modId / 显示名 | `rainjava` / RainJava |
| MC / Forge | 1.20.1 / 47.x(`loaderVersion="[47,)"`) |
| Java | 17(编译脚本时 `-source 17 -target 17`) |
| 许可 | MIT |
| 入口类 | `net.rain.rainjava.RainJava`(`@Mod("rainjava")`) |
| 命令 | `/java`、别名 `/j`(需要权限等级 2) |

### 1.3 依赖构成

| 依赖 | 形态 | 用途 |
|---|---|---|
| Eclipse ECJ | 重定位为 `net.rain.repack.ecj.*`(外部库 RainAPI 提供) | 脚本内存编译 |
| JavaParser | 重定位为 `net.rain.repack.javaparser.*` | MCP→SRG 源码转换 |
| Mixin 分支 | 重定位为 `org.spongepowered.rain.asm.*`(新增动态 Mixin API) | 动态 Mixin 管线 |
| Forge 原生 Mixin | `org.spongepowered.asm.*` | agent 补丁目标、少量工具类 |
| `rainjava-core`(可选) | 独立 ModLauncher 服务 + 内嵌 Java agent | 替换原生 Mixin 的类加载逻辑,支持磁盘 Mixin |

设计特点:**自带编译器与映射表**(`assets/mappings/map/mappings.tsrg`,运行时实测约
6,674 类 / 31,004 字段 / 54,309 方法),因此在正式环境中无需任何开发工具链即可编译用户脚本。

---

## 2. 总体架构

### 2.1 组件分层

```
┌────────────────────────────────────────────────────────────────────────┐
│ 入口层        RainJava(@Mod) 静态初始化 / commonSetup / clientSetup    │
│               onServerStarting / RainJava.EVENT_BUS(脚本总线实例)      │
├────────────────────────────────────────────────────────────────────────┤
│ 核心编排层    RainJavaCore:目录初始化、示例生成、按 ScriptType 装载、   │
│               热重载(reload/loadScripts)、资源包注册(onAddPackFinders) │
├────────────────────────────────────────────────────────────────────────┤
│ 脚本引擎      JavaScriptLoader(发现/编排)                              │
│               McpToSrgTransformer(源码名称转换)                        │
│               JavaSourceCompiler(ECJ 内存编译 + classpath 构建)        │
│               DynamicClassLoader(脚本类加载)                           │
│               ClassReplacementManager(类替换,未接线)                   │
├────────────────────────────────────────────────────────────────────────┤
│ 脚本 API      RainEventBus / RainSubscribeEvent / RainEventSubscriber  │
│               ForgeEventBridge(Forge 事件桥,6 + 191 个事件)            │
│               MinecraftHelper(映射反射助手)                            │
│               NetworkUtils(网络) / RegUtils(注册)                      │
│               RainJavaResourcePack(assets/data 注入)                   │
├────────────────────────────────────────────────────────────────────────┤
│ 字节码层      DynamicMixinLoader / MixinManager / MixinJarBuilder      │
│               BytecodeProviderWrapper / RainMixinConnector             │
│               RefMapGenerator / MixinInfoInjector(磁盘兜底)            │
│   (可选)      rainjava-core:ModLauncher 服务 + agent,补丁原生 Mixin    │
├────────────────────────────────────────────────────────────────────────┤
│ 支撑层        RainJavaLogger / ScriptErrorCollector / ScriptError      │
│               RuntimeModuleOpener / ModuleAccessHelper / Unsafe...     │
└────────────────────────────────────────────────────────────────────────┘
```

### 2.2 启动时序

| 时机 | 动作 |
|---|---|
| `RainJava` 类静态块(Forge 构造 mod 类时) | 创建 `RainJavaCore` → 立即执行 **STARTUP** 脚本(编译+`init()`) |
| `RainJava` 构造器 | 注册 `commonSetup` / `clientSetup` 到 mod 事件总线;注册自身到 Forge 总线 |
| `FMLCommonSetupEvent` | 空实现 |
| `FMLClientSetupEvent` | 执行 **CLIENT** 脚本 |
| `ServerStartingEvent` | 执行 **SERVER** 脚本 |
| `AddPackFindersEvent` | 注册 `rainjava_assets` / `rainjava_data` 资源包 |

要点:**STARTUP 脚本在类初始化阶段运行**,早于任何 Forge 生命周期事件;这意味着启动脚本
可参与 mod 初始化早期的行为定制,但也意味着此时其它 mod 可能尚未加载完毕。

### 2.3 运行时目录布局

```
<gameDir>/RainJava/
├─ startup/    游戏初始化时执行一次
├─ server/     每次开服/进世界时执行
├─ client/     客户端初始化时执行
├─ assets/     作为 rainjava_assets 资源包加载(实时读取)
├─ data/       作为 rainjava_data 数据包加载(实时读取)
├─ mixins/     (1.0.0 未接线)
├─ coremod/    (1.0.0 未接线,目录不会创建)
└─ README.txt  自动生成的目录说明
```

`RainJavaCore.initializeFolders()` 实际只创建 `startup/server/client/data/assets` 五个目录,
`coremod` 字段存在但既不创建也不使用。`createExampleFiles()` 在首次运行时生成
`startup/Example.java` 与 `README.txt`。

---

## 3. 脚本执行引擎

### 3.1 加载流程总览

```
Files.walk(脚本目录)
   │  过滤:路径包含 mixins/ 或 replace/ 的文件跳过
   ▼
读取源码 → McpToSrgTransformer.transformSource()   ← MCP 名称 → SRG 名称
   ▼
extractClassName() 解析包名/类名
   ▼
JavaSourceCompiler.compileFromString()             ← ECJ 内存编译
   ▼
CompiledClass{className, bytecode}
   ▼
DynamicClassLoader.addCompiledClass() + loadClass()
   ▼
processRainEventSubscriber()  ← @RainEventSubscriber 自动注册总线
   ▼
executeClass()                ← 查找并调用入口方法
```

### 3.2 源码发现与过滤

`JavaScriptLoader.loadJavaScripts(Path)`:

- 递归收集 `*.java`;
- 过滤规则:`mixins/`、`replace/` 子路径下的文件不参与普通脚本加载(前者归 Mixin 管线,
  后者归类替换管线);
- 每个文件按"编译成功/失败"计数,输出 `Compiled x/y` 日志。

### 3.3 MCP→SRG 源码转换

生产环境的 Minecraft 运行时使用 SRG 名称(`m_xxxxx_`),而用户脚本写的是官方(MCP/Mojmap)
名称。`McpToSrgTransformer` 用 JavaParser 解析脚本 AST,重写三类节点:

- `MethodCallExpr`(方法调用)
- `FieldAccessExpr`(字段访问)
- `NameExpr`(简单名)

名称解析委托给 `MinecraftHelper`,并沿继承链查找(`findSrgMethodInHierarchy` /
`findSrgFieldInHierarchy`),只在 `TRANSFORM_PACKAGES` 白名单包内改写。
转换失败时回退使用原始源码(仅告警)。

### 3.4 内存编译(ECJ)

`JavaSourceCompiler` 的编译策略:

- **编译器**:强制实例化重定位的 `EclipseCompiler`;若失败则整条脚本管线停用(不抛异常)。
- **编译参数**:`-source 17 -target 17 -encoding UTF-8 -warn:none -proceedOnError
  -g:vars,lines,source -preserveAllLocals`,并注册 Mixin 注解处理器
  (`MixinObfuscationProcessorInjection/Targets`,`-Amixin.env.remapRefMap=true`)。
- **classpath 构建**(`buildClassPath()`,按顺序去重):
  1. `java.class.path`
  2. 线程上下文类加载器及其父链的 URL(`URLClassLoader.getURLs()` /
     `BuiltinClassLoader.ucp` 反射)
  3. `ModList.getMods()` 中每个 mod 的文件路径(多重探测:`getFilePath`/`getFile`/
     `SecureJar.getPrimaryPath`/`toString` 解析)
  4. 通过已知 MC 类(`Item`、`Block`、`ForgeRegistries`、`DeferredRegistration` 等)的
     `ProtectionDomain`/`getResource` 反查 jar 路径
  5. 游戏根目录、`mods/`、`libraries/` 目录扫描
- **输入输出**:`InMemoryJavaFileObject`/`EclipseCompatibleJavaFileObject`(内存源码)与
  `CustomFileManager`(把 class 写入 `ByteArrayOutputStream`),产物 `CompiledClass` 不落盘。
- 编译错误解析 ECJ 的 `Line N:` 输出,写入 `ScriptErrorCollector`。

### 3.5 类加载与执行

- `DynamicClassLoader extends ClassLoader`,parent 为线程上下文类加载器;
  `findClass` 命中内存字节码时 `defineClass`,否则委派父加载器。**parent-first** 语义:
  父加载器能解析的同名类无法被脚本覆盖。
- 入口方法发现顺序(`JavaScriptLoader.executeClass`):
  1. `public static` 且无参:依次尝试 `init` → `initialize` → `onLoad` → `load` → `register`;
  2. `public static` 且参数为 `FMLJavaModLoadingContext` 的同名方法(传入 `FMLJavaModLoadingContext.get()`);
  3. 都没有时:非抽象/非接口类尝试无参构造实例化;否则仅记录日志。
- 执行顺序取决于 `Files.walk` 的遍历顺序(**未排序**,不确定)。
- `@RainEventSubscriber` 标注的脚本类在加载后自动 `RainJava.EVENT_BUS.register(clazz)`。

### 3.6 热重载

`/java reload [startup|server|client]` 调用链:

```
RainJavaCommands.reload()
  ├─ ScriptErrorCollector.clear(type)
  ├─ DistExecutor.unsafeRunWhenOn(CLIENT, RainJavaClientEvents::resetShownFlag)
  └─ RainJavaCore.reload(type)
       ├─ loadedFlags.put(type,false)
       ├─ loaders.put(type, new JavaScriptLoader(type))   ← 整体替换
       └─ doLoad(type)                                     ← 重新扫描/编译/执行
```

每次重载都会**重建整个加载器**:重新生成 ECJ 编译器、重新全量构建 classpath、新建
`DynamicClassLoader`。语义上有两点需要知晓:

- 旧的脚本类实例/静态状态被丢弃(可 GC),但**已注册到事件总线的监听器不会反注册**,
  重载后同一脚本类会产生重复回调;
- 旧 `DynamicClassLoader` 中的类不再可达,但若其它代码持有其引用(如注册表对象),
  仍可能存活。

### 3.7 类替换管线(设计存在,未接线)

`ClassReplacementManager` 设计用于"用脚本重写现有类":

- 工作目录 `RainJava/replace/`;
- `processReplacements()`:扫描 → `JavaSourceCompiler.compile(Path)` → 编译产物写入
  `<进程CWD>/.rainjava_replacements/<pkg>/<Cls>.class`;
- 日志注明"下次游戏启动时应用"。

**1.0.0 中该类没有任何实例化点**,且没有任何代码读取 `.rainjava_replacements`,
因此该功能实际不可用;相关辅助 `PathUtils.removeRainJavaPrefix` 也仅被它引用。

---

## 4. 脚本 API

### 4.1 事件总线(`net.rain.eventbus`)

**注解**

| 注解 | 目标 | 属性 |
|---|---|---|
| `@RainSubscribeEvent` | 方法 | `priority`(默认 `NORMAL`)、`receiveCanceled`(默认 false) |
| `@RainEventSubscriber` | 类 | `bus`(默认 FORGE;仅用于日志,不改变实际总线) |

**注册**:脚本类标注 `@RainEventSubscriber` 后由 `JavaScriptLoader.processRainEventSubscriber()`
自动调用 `RainEventBus.register(Class)`。要求监听方法为 **static、public、恰好 1 个参数**,
否则告警跳过;`register` 以传入的 Class 作为 owner,重复注册跳过,支持 `unregister(Class)`。

**分发**:`RainEventBus.post(Object)` 沿事件类的**超类 + 接口 BFS** 收集全部类型,
按优先级(HIGHEST→MONITOR)依次**同步反射调用** `method.invoke(null, event)`;
单个监听器异常被捕获记录,不中断其它监听器。

**取消语义**:仅当事件是 `net.minecraftforge.eventbus.api.Event` 时有效;
被取消后,`receiveCanceled=false` 且非 MONITOR 的监听器被跳过。

**线程模型**:在调用线程同步执行——服务端 tick/命令/聊天在 Server thread,客户端 tick
在 Client thread,`RenderTickEvent` 在 Render thread,加载类事件在 mod 加载线程。
总线容器为 `ConcurrentHashMap` + `CopyOnWriteArrayList`,注册与分发的并发是安全的。

### 4.2 Forge 事件桥(`api.ForgeEventBridge`)

通过 `@Mod.EventBusSubscriber(modid="rainjava")` 把 Forge 事件原样转发到
`RainJava.EVENT_BUS.post(...)`:

| 总线 | 数量 | 代表事件 |
|---|---:|---|
| MOD | 6 | `FMLCommonSetupEvent`、`FMLClientSetupEvent`、`FMLDedicatedServerSetupEvent`、`InterModEnqueueEvent`、`InterModProcessEvent`、`FMLLoadCompleteEvent` |
| FORGE | 191 | Tick 族(Server/Client/Level/Player/Render,START+END)、生命周期(ServerStarting/Started/Stopping、TagsUpdated、AddReloadListener、OnDatapackSync…)、玩家(登录/登出/交互/物品/经验/进度…)、实体与生物(伤害/死亡/掉落/生成/效果…)、方块与世界(破坏/放置/爆炸/区块/流体…)、注册与数据(RegisterCommands、RegisterStructureConversions、LootTableLoad…) |

脚本因此可以用同一套总线监听几乎全部 Forge 事件,例如:

```java
@RainEventSubscriber
public class MyEvents {
    @RainSubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) { ... }
}
```

### 4.3 映射反射助手(`MinecraftHelper`)

用于在脚本中访问"映射表里存在但编译期不方便引用"的成员:

- **数据源**:优先从 jar 内资源 `assets/mappings/map/mappings.tsrg`(TSRG2)加载,
  多重路径+三级 ClassLoader 探测;失败时回退磁盘文件
  (`config/forge/mappings.tsrg`、`mappings/mappings.tsrg`、Gradle 缓存)。
- **解析结构**:类名映射、方法映射(简单键 + 带描述符键)、返回类型、字段映射。
- **公开 API**:`getStaticField/setStaticField/getField/setField`、
  `invokeStaticMethod/invokeMethod`(支持按类名+方法名的字符串形式)、
  `getParameterTypes`、`clearCache`;查找顺序为官方名 → `ObfuscationReflectionHelper`
  → SRG 名,并缓存 Field/Method。
- **已知短板**:方法缓存键不含参数签名(重载会误命中);基本类型参数匹配脆弱;
  `findFieldType()` 为永远返回 null 的桩;常量 `MAPPING_RESOURCE_PATH` 路径拼写与
  实际资源路径不一致(实际加载走多路径探测所以可用)。

### 4.4 网络封装(`NetworkUtils`)

- `init(modid)` 创建 `SimpleChannel("modid:main")`,协议版本 `"1"`,双端版本校验。
- `register(Class, Supplier)` 对同一消息类分别注册 `PLAY_TO_SERVER` / `PLAY_TO_CLIENT`
  两个方向,处理体经 `consumerMainThread` 回主线程(`handleServer(player)` /
  `handleClient()`)。
- `PacketBuilder` 提供顺序写 `String/int/long/float/double/boolean/byte[]` 的 DSL,
  生成 `QuickPacket`;分发 API:`sendToServer`、`sendToPlayer`、`sendToAllPlayers`、
  `sendToNearby(radius)`、`sendToDimension`。

**严重缺陷(该类实际不可用)**:静态块 `{ packetId=0; registerQuickPacket(); }` 在
`CHANNEL` 赋值(仅在 `init()` 中)之前就调用注册 → 类初始化即抛
`ExceptionInInitializerError`;后续引用得到 `NoClassDefFoundError`。此外:

- `QuickPacket` 的 `serverHandler/clientHandler` 不参与序列化,接收端回调恒为 null;
- mod 自身不会调用 `init()`,通道无人初始化;
- 同一消息类在两个方向以不同 id 注册,而 Forge 1.20.1 的 `IndexedMessageCodec`
  以 Class 为键索引,后注册方向会覆盖索引,存在编号错乱风险。

### 4.5 注册封装(`RegUtils`)

- `init(modId, modEventBus)` 为每个 modId 创建 `ModRegistries`,内含三个
  `DeferredRegister`:`BLOCKS`、`ITEMS`、`ENTITY_TYPES`,并立即 `register(eventBus)`。
- 便捷方法:`block`、`item`、`blockWithItem`、`stone()`(复制石头属性)、
  `entity`(`EntityType.Builder`);`registerCustom` 支持 `Supplier`、实例、
  `Class`(反射无参构造)、`Class + 参数`(按参数个数 + `isAssignable` 匹配构造器)。
- 局限:自定义注册表创建后不保存引用;实体注册仅覆盖 `EntityType`;未做 id 合法性校验;
  注册对象需脚本自行保存为静态字段,否则热重载可能重复注册。

### 4.6 资源/数据包注入

`RainJavaCore.onAddPackFinders` 把脚本旁的目录注册成资源包:

| 目录 | Pack id | 类型 | 行为 |
|---|---|---|---|
| `RainJava/assets/` | `rainjava_assets` | `CLIENT_RESOURCES` | `Pack.Position.TOP`,required |
| `RainJava/data/` | `rainjava_data` | `SERVER_DATA` | 同上 |

`RainJavaResourcePack implements PackResources`:命名空间为一级子目录;
`getResource` **实时从磁盘读取**,因此改完文件后 `F3+T` 重载资源即可生效,无需重启;
`pack.mcmeta` 的 `pack_format=15`,描述为 "RainJava Dynamic Resources"。

---

## 5. Mixin / CoreMod 子系统

> 该子系统在 1.0.0 中**设计完整但未接线**(见 §5.5),以下先描述设计链路。

### 5.1 设计链路

目标:用户把 Mixin 源码放入 `RainJava/mixins/`,游戏内完成"扫描→编译→注册→生效":

```
MixinManager.runFullWorkflow()
 ├─ scanMixinSources()      扫描源码,生成 MixinInfo{sourceFile,className,targetClass,side}
 ├─ needsRecompile()        与 compile_state.json 比对(文件数/mtime/size)
 ├─ compileMixins()         内存编译,class 落盘 .rain_mixin/rainjava/mixins/
 ├─ generateRefMap()        TSRG2 解析 @Shadow/@Inject/@Redirect 引用,生成 refmap
 ├─ generateMixinConfig()   写 .rain_mixin/rainjava.mixins.json
 ├─ saveCompileState()      写 compile_state.json
 ├─ validateConfiguration() 校验配置/class/path/classpath
 └─ showRestartMessage()    提示重启生效
```

运行期注册(另一条内存链路,`DynamicMixinLoader`):

1. `MixinProcessorHolder.getInstance()` 取全局 Mixin 处理器;
2. 用 `BytecodeProviderWrapper` 包装并**替换 Mixin 服务的字节码提供器**;
3. `JavaSourceCompiler` 内存编译 `mixins/` 源码;
4. 字节码登记进包装器缓存(双键 `a.b.C` / `a/b/C`);
5. `MixinConfig.createDynamic("dynamic_rainjava_<uuid8>","rainjava.mixins",1000,false)`
   创建动态配置;
6. `DefaultMixinConfigPlugin.registerDynamicMixin(name)` 登记 Mixin 名;
7. 注入 `extensions`、`service`、`plugin` 等 Mixin 内部字段;
8. `config.registerDynamicMixin(name, bytes)` → `prepare()` → `postInitialise()`;
9. 把配置挂进 `MixinProcessor.configs` 并重排序;
10. 目标类加载时,Mixin 经 `IClassBytecodeProvider.getClassNode()` 命中包装器缓存,
    完成注入。

### 5.2 分支 Mixin 的扩展 API

项目使用的 Mixin 被整体重定位为 `org.spongepowered.rain.asm`,并新增:

| 扩展 | 作用 |
|---|---|
| `MixinProcessorHolder` | 全局 `MixinProcessor` 实例持有者(`get/setInstance`) |
| `MixinConfig.createDynamic(name,pkg,priority,required)` | 构造空动态配置 |
| `MixinConfig.registerDynamicMixin(name, bytes)` | 反射定义类 → 解析 → 构造 `MixinInfo` → `parseTargets/validate` |
| `DefaultMixinConfigPlugin.registerDynamicMixin(name)` | 静态注册表,`getMixins()` 返回 |
| `MixinServiceModLauncher.forceInitializeBytecodeProvider()` | 预热字节码提供器 |

该 fork 与 mod 代码**硬耦合**(如 `DefaultMixinConfigPlugin` 直接引用
`RainJava.LOGGER`),不能独立使用。

### 5.3 磁盘兜底与 agent 补丁

`rainjava-core`(独立 jar)提供:

- **ModLauncher 服务** `RainMixinTransformationService`(服务名 `rainmixin`),
  在启动早期把自身从 ModLauncher 的发现列表/模块层中"摘除",避免暴露;
- **自附加 Java agent**:从 `java.io.tmpdir` 释放内嵌 agent jar,通过
  `VirtualMachine.attach(pid).loadAgent(...)` 注入;
- **字节码补丁**(针对 Forge 原生 Mixin):
  - 改写 `MixinInfo.loadMixinClass` 中的 `IClassBytecodeProvider.getClassNode(name,true)`
    调用为 `MixinInfo.getMixinClassNode(provider,name,runTransformers,flags)`;
  - 注入 `MixinInfoInjector.getMixinClassNode`:先走正常提供器,失败时从
    `<gamedir>/.rain_mixin/<name>.class` 读取并解析为 `ClassNode`(磁盘兜底);
  - 修 `MixinConfig.create` 的缺失资源异常路径。

这套机制的目的是:在没有启动器参数配合的场景下,让 Mixin 能加载**磁盘上动态生成**的
Mixin class。

### 5.4 Unsafe / 模块绕过

| 组件 | 手段 | 用途 |
|---|---|---|
| `RuntimeModuleOpener` | `Module.implAddOpensToAllUnnamed/implAddExportsToAllUnnamed`,失败降级为 `Unsafe` 直接改 `Module.openPackages` | 打开 `java.base`、Mixin 包给无名模块 |
| `ModuleAccessHelper` | `implAddOpens/implAddReads` | 模块读/开放修正(未接线) |
| `UnsafeClassDefiner` | 试图用 `Unsafe` 绕过 `defineClass` 访问控制 | **坏死代码**(句柄从未赋值,调用即 NPE) |

风险:直接修改 JDK 内部字段随版本失效;异常普遍降级为 debug 日志,故障静默。
实测 dev 环境无 `--add-opens` 时,`RuntimeModuleOpener` 的模块打开全部失败(仅记录日志)。

### 5.5 1.0.0 的接线状态

全量引用检索结论:

| 组件 | 引用数 | 状态 |
|---|---:|---|
| `DynamicMixinLoader` / `MixinManager` / `BytecodeProviderInstaller` | 0 | 从未实例化 |
| `MixinJarBuilder` / `MixinDebugHelper` / `MixinDiagnosticTool` | 0 | 仅诊断工具,未调用 |
| `MixinConfigHelper` / `UnsafeClassDefiner` / `ModuleAccessHelper` / `MixinUtils` | 0 | 死代码 |
| `JavaScriptLoader.processMixins()` | 调用但**空实现** | STARTUP 路径空转 |
| `java.mixins.json` | 无注册机制(MANIFEST 无 `MixinConfigs`) | 孤儿文件(且引用了不存在的 `MixinBootstrap`) |
| `RainMixinConnector` | MANIFEST 无 `MixinConnector` 属性 | 永不被 Mixin 调用 |

即:**1.0.0 的 `mixins/`、`coremod/` 热注入链路整体不可用**;脚本、事件、资源包等
其余功能不受影响。

---

## 6. 命令与权限

注册于 `RegisterCommandsEvent`,根命令 `/java` 与别名 `/j`,**统一要求权限等级 2**。

| 命令 | 行为 |
|---|---|
| `/java reload [startup\|server\|client]` | 无参重载全部(顺序 SERVER→CLIENT→STARTUP);清空错误状态 → 重建加载器 → 统计结果 |
| `/java errors [startup\|server\|client]` | 无参显示全部类型;输出错误/警告计数与前 5 条明细,附日志文件打开链接 |
| `/java hand getId` | 手持物品注册名(青色、可点击复制);空手提示 |
| `/java hand getClass` | 手持物品类名(金色、可复制);`ItemStack` 运行时类不同则追加一行 |

反馈格式:

- 开始:`▶ RainJava: Reloading <type> scripts...`(黄)
- 成功:`✔ RainJava: <type> scripts reloaded successfully.`(绿)
- 失败:`✘ RainJava: <type> reload finished with N error(s) and M warning(s).`(红,
  附 `[Open Log]`/`[View Error Screen]` 点击控件)
- 无问题:`✔ RainJava <type>: No errors or warnings.`(绿)

已知命令缺陷:失败消息中的 `[View Error Screen]` 指向 `/rainjava_errors <type>`,
该命令**从未注册**(全库仅注册 `/java`、`/j`),点击无效。

---

## 7. 日志与错误系统

### 7.1 日志(`RainJavaLogger`)

- 每类脚本一份独立文件:`<gameDir>/logs/Java/{startup|server|client}.log`
  (截断模式,自动 flush);
- 格式:`[yyyy-MM-dd HH:mm:ss] [TYPE/LEVEL] message`,同时镜像到 Log4j(`RainJava`);
- 编译输出单独成块(`=== Compiler Output ===`),ECJ 原始输出完整落盘;
- 首次初始化依赖 `FMLPaths` 就绪,失败会复位标志以便重试。

### 7.2 错误模型(`ScriptError` / `ScriptErrorCollector`)

- `ScriptError`:类型(ERROR/WARN)、脚本类型、消息、文件名、行号、时间戳、堆栈;
- `ScriptErrorCollector`:按 `ScriptType` 分桶的 `CopyOnWriteArrayList`,提供
  `addError/addWarning/addFromThrowable/clear` 与只读视图;
- `/java errors` 与客户端错误屏均消费该收集器。

**注意**:编译期错误会进入收集器;但脚本 **`init()` 运行期异常只写日志、不进收集器**
(`executeClass` 捕获后仅 `logger.error`),因此此类错误在 `/java errors` 中显示为 0。

### 7.3 客户端错误界面(`RainJavaErrorScreen`)

- **触发**:客户端 tick 检测到 STARTUP 错误且当前在主菜单时,自动弹出(每次运行一次);
  进入世界后若有错误/警告,则发送聊天消息 `[RainJava] <type> scripts: N error(s)...`;
- **内容**:列表展示序号、`文件名:行号`、时间、消息(最多 3 行);悬停显示堆栈
  (Shift 展开全部);
- **交互**:双击左键打开对应脚本文件;双击右键复制完整堆栈;按钮有
  `Open Log File`、`Close`、(STARTUP 时为 `Quit Game`,且 ESC 不可关闭);
- 右上角可在 `View Errors [n]` / `View Warnings [n]` 间切换。

---

## 8. 典型数据流

### 8.1 一次脚本热重载(完整调用链)

```
玩家:/java reload server
  → RainJavaCommands.reload(ctx, SERVER)
      → ScriptErrorCollector.clear(SERVER)
      → RainJavaCore.reload(SERVER)
          → loaders.put(SERVER, new JavaScriptLoader(SERVER))
              → RuntimeModuleOpener.openMixinModules()
              → new EclipseCompiler()            // 重定位 ECJ
              → new JavaSourceCompiler(compiler)
              → new DynamicClassLoader(TCCL)
              → new McpToSrgTransformer()
          → doLoad(SERVER)
              → loader.loadJavaScripts(RainJava/server)
                  → Files.walk → McpToSrg 转换 → ECJ 内存编译
                  → DynamicClassLoader 定义并加载类
              → processLoadedClasses()
                  → @RainEventSubscriber 注册
                  → executeClass() → init() 调用
      → 统计错误/警告 → 聊天栏反馈(✔/✘)
```

### 8.2 一次事件派发(以玩家 tick 为例)

```
Forge Server thread 触发 TickEvent.PlayerTickEvent
  → ForgeEventBridge.ForgeBusHandler.onPlayerTick(e)
      → RainJava.EVENT_BUS.post(e)
          → BFS 收集 e 的超类/接口类型
          → 按 priority 升序查找监听器
          → 反射调用脚本方法 onPlayerTick(e)
```

### 8.3 资源热更新

```
玩家修改 RainJava/assets/<ns>/textures/foo.png
  → 游戏内 F3+T(重载资源)
      → RainJavaResourcePack.getResource() 实时读盘
          → 新资源生效(无需重启,无需重载脚本)
```

---

## 9. 技术评估

### 9.1 设计亮点

1. **完整的脚本闭环**:内存编译(ECJ)+ 内存类加载 + 自动入口方法 + 独立日志 +
   错误界面 + 可点击反馈,形成了接近"游戏内 IDE"的体验。
2. **映射感知的脚本兼容层**:`McpToSrgTransformer` + `MinecraftHelper` + 随包
   `mappings.tsrg`,让用户在生产环境直接使用官方名称写脚本,是很务实的设计。
3. **事件桥覆盖面广**:MOD 6 + FORGE 191 个事件的转发,加上注解式自动注册,
   脚本能介入几乎全部游戏逻辑。
4. **动态 Mixin 方案有技术深度**:fork 暴露 `MixinProcessorHolder` /
   `createDynamic` / `registerDynamicMixin`,再配合 agent 改写原生 Mixin 的
   类加载路径,给出了一条"无启动器参数也能动态注入 Mixin"的可行路线。
5. **资源/数据包直读**:`PackResources` 实时读盘,改完即生效,免打包。

### 9.2 缺陷与风险清单(1.0.0 实测/代码确认)

| 级别 | 问题 | 影响 |
|---|---|---|
| 高 | `NetworkUtils` 静态初始化顺序错误 | 整个网络封装不可用,报错隐晦 |
| 高 | Mixin/CoreMod 管线未接线(§5.5) | 宣传的核心能力缺失 |
| 高 | 脚本 `init()` 运行期异常不进错误收集器 | `/java errors` 显示 0,误导 |
| 中 | 热重载不反注册事件监听器 | 重载后回调重复执行 |
| 中 | 命令链接指向未注册的 `/rainjava_errors` | 客户端/聊天反馈点击无效 |
| 中 | `MinecraftHelper` 方法缓存忽略参数签名、基本类型匹配脆弱 | 重载方法可能静默调错 |
| 中 | Mixin 管线路径/命名约定不一致(`rainjava.mixins` 包名、扁平 class 落盘、两套入口命名) | 即使接线也难互通 |
| 中 | `UnsafeClassDefiner` 坏死代码;`ModuleAccessHelper` 未接线 | 功能缺失/误导 |
| 低 | 脚本执行顺序依赖 `Files.walk`(未排序) | 初始化顺序不确定 |
| 低 | `RegUtils` 自定义注册表不保存引用、id 未校验 | 易用性/健壮性 |
| 低 | `ClassReplacementManager` 与 `.rainjava_replacements` 无消费者 | 死功能 |

### 9.3 安全模型

- **信任边界 = 文件写入权限**:`RainJava/` 目录的写入者等价于在游戏进程内执行任意代码
  (脚本零沙箱,可反射、可发网络包、可改字节码);
- `/java` 命令要求 OP 2,但命令只是操作入口,不构成安全边界;
- 防御性设计仅有:总线逐监听器 try/catch、错误收集/展示、日志分级;
  没有脚本签名、哈希校验、沙箱或审计;
- **结论**:适用于单人/整合包/调试场景,不适合多租户或不受信脚本环境。

---

## 10. 附录

### 附录 A:类清单(按包)

| 包 | 类 |
|---|---|
| `net.rain.rainjava` | `RainJava`(入口) |
| `.core` | `RainJavaCore`、`ScriptType` |
| `.java` | `JavaScriptLoader`、`JavaSourceCompiler`(+4 内部类)、`DynamicClassLoader`、`McpToSrgTransformer`、`CompiledClass`、`ClassReplacementManager`、`MixinUtils` |
| `.java.helper` | `BytecodeProviderInstaller`、`BytecodeProviderWrapper`、`MixinServiceHelper`、`MixinConfigHelper`、`ModuleAccessHelper`、`RuntimeModuleOpener`、`UnsafeClassDefiner` |
| `.java.util` | `NetworkUtils`(+4 内部类)、`RegUtils` |
| `.java.utils` | `MinecraftHelper`、`MC` |
| `.mixin` | `MixinManager`(+4 内部类)、`DynamicMixinLoader`、`MixinJarBuilder`、`MixinDebugHelper`、`MixinDiagnosticTool`、`RainMixinConnector` |
| `.mixin.refmap` | `RefMapGenerator` |
| `.api` | `ForgeEventBridge` |
| `.command` | `RainJavaCommands` |
| `.client` | `RainJavaClientEvents`、`RainJavaErrorScreen` |
| `.resources` | `RainJavaResourcePack` |
| `.logging` | `RainJavaLogger`、`ScriptError`、`ScriptErrorCollector` |
| `.utils` | `PathUtils` |
| `net.rain.eventbus` | `RainSubscribeEvent`、`RainEventSubscriber`、`bus.RainEventBus` |
| `cpw.mods.modlauncher.MixinCore` | `MixinInfoInjector`(磁盘 Mixin 兜底) |

### 附录 B:文件/路径约定

| 路径 | 用途 |
|---|---|
| `RainJava/{startup,server,client}` | 脚本目录 |
| `RainJava/{assets,data}` | 资源/数据包(实时读取) |
| `RainJava/README.txt` | 自动生成的说明 |
| `logs/Java/{startup,server,client}.log` | 分类日志 |
| `.rain_mixin/` | 动态 Mixin 运行目录(配置/refmap/编译状态/class 兜底) |
| `.rain_mixin/rainjava.mixins.json` | 动态 Mixin 配置(1.0.0 未生成) |
| `.rain_mixin/rainjava.refmap.json` | refmap(1.0.0 未生成) |
| `.rain_mixin/compile_state.json` | 增量编译状态(1.0.0 未生成) |
| `.rainjava_replacements/` | 类替换输出(未接线) |

### 附录 C:关键实测数据(dev 环境)

| 项 | 数据 |
|---|---|
| 映射表加载 | 6,674 类 / 31,004 字段 / 54,309 方法(简单名)/ 57,813 方法(带描述符) |
| 示例脚本编译 | `rainjava.startup.Example` → 602 字节 class |
| 启动脚本执行 | 静态块阶段完成,早于 `FMLCommonSetupEvent` |
| 客户端脚本 | ClientSetup 阶段加载 |
| 服务端脚本 | `ServerStartingEvent` 阶段加载 |
| 资源包 | `rainjava_assets` / `rainjava_data` 注册成功,`pack_format=15` |
