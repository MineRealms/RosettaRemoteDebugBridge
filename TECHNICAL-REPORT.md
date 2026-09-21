# RosettaRemoteDebugBridge 技术报告

> 项目:**RosettaRemoteDebugBridge 1.0.0**(Forge 1.20.1 / Minecraft 1.20.1 / Java 17)
> 上游来源:作者原始项目 RainJava 1.0.0(反编译还原后重构、重命名)
> 依据:当前工程源码、依赖 jar 字节码级分析、无人值守运行实测
> 报告定位:描述该 mod 的**系统设计、内部实现、脚本 API、工程质量与技术评估**

**命名迁移说明**:本工程已整体重命名为 RosettaRemoteDebugBridge:

| 项目 | 迁移前(上游) | 迁移后(当前) |
|---|---|---|
| 命名空间 | `net.rain.rainjava.*` / `net.rain.eventbus.*` | `com.rosetta.remotedebugbridge.*`(见附录 A) |
| 类前缀 | `RainJava*` / `Rain*` | `Rosetta*` |
| modId / 显示名 | `rainjava` / RainJava | `rosetta_remote_debug_bridge` / RosettaRemoteDebugBridge |
| 运行时目录 | `RainJava/` | `RosettaRemoteDebugBridge/` |
| 资源包/数据包 id | `rainjava_assets` / `rainjava_data` | `rosetta_assets` / `rosetta_data` |
| 日志目录 | `logs/Java/` | `logs/Rosetta/` |

外部依赖命名空间保持原样(不迁移):`net.rain.repack.*`(RainAPI 重定位的 ECJ/JavaParser)、
`org.spongepowered.rain.asm.*`(改造版 Mixin 分支)、`cpw.mods.modlauncher.MixinCore`(agent 注入点)。

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
9. [工程质量与验证](#9-工程质量与验证)
10. [附录](#10-附录)

---

## 1. 项目概述

### 1.1 定位

RosettaRemoteDebugBridge 是一个**运行期 Java 脚本与调试桥 mod**:

- 用户把普通 `.java` 源码放入游戏目录的 `RosettaRemoteDebugBridge/` 文件夹;
- mod 在启动 / 开服 / 客户端初始化时**在内存中编译并执行**这些脚本;
- 内置 Eclipse ECJ(由 RainAPI 重定位提供),**无需系统 JDK** 即可编译;
- 提供 `/java` 命令族进行热重载与错误查看;
- 附带脚本事件总线、Forge 事件桥、映射感知反射助手、网络与注册封装、资源/数据包注入;
- 预留动态 Mixin / CoreMod 管线(依赖外部 agent,当前环境自动降级,见 §5)。

### 1.2 运行环境与元数据

| 项 | 值 |
|---|---|
| modId / 显示名 | `rosetta_remote_debug_bridge` / RosettaRemoteDebugBridge |
| MC / Forge | 1.20.1 / 47.x(`loaderVersion="[47,)"`) |
| Java | 17(脚本按 `-source 17 -target 17` 编译) |
| 许可 | MIT(作者字段保留 `Rain`) |
| 入口类 | `com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge`(`@Mod`) |
| 构建 | ForgeGradle 6 / Gradle 8.8 / official 映射 |
| 产物 | `build/libs/rosetta_remote_debug_bridge-1.0.0.jar`(8,686,329 字节,自包含) |

### 1.3 依赖构成

| 依赖 | 提供方 | 用途 |
|---|---|---|
| Eclipse ECJ | `libs/rainapi-repack-1.0.2.jar`(`net.rain.repack.ecj.*`) | 脚本内存编译 |
| JavaParser | 同上(`net.rain.repack.javaparser.*`) | MCP→SRG 源码转换 |
| Mixin 分支 | `libs/mixin-0.8.5-dev.jar`(`org.spongepowered.rain.asm.*`) | 动态 Mixin 管线(扩展 API) |
| Forge 自带 Mixin | `org.spongepowered.asm.*` | agent 补丁目标、少量工具类与注解处理器 |
| `rainjava-core`(可选) | 外部 jar(ModLauncher 服务 + 内置 agent) | 磁盘 Mixin 兜底(当前未集成) |
| 映射表 | 随包资源 `assets/mappings/map/mappings.tsrg` | 运行时实测约 6,674 类 / 54,309 方法 |

**打包策略**:ECJ/JavaParser 与 Mixin 分支通过 `processResources { from zipTree(...) }`
**影子打包进 mod 自身**(依赖声明为 `compileOnly`),因此 dev 运行与发布 jar 均自包含,
且规避了 JPMS 下“classpath 普通 jar 属于 unnamed module、mod 读不到”的问题。

### 1.4 工程位置

| 路径 | 说明 |
|---|---|
| `H:\MinecraftMods\RainJava-work\RosettaRemoteDebugBridge\` | 工程根(git 仓库) |
| `autotest/server/*.java` | 可复现的无人值守测试脚本(见 §9.1) |
| `run/` | 开发运行目录(游戏目录,不受版本控制) |

---

## 2. 总体架构

### 2.1 组件分层

```
┌────────────────────────────────────────────────────────────────────────────┐
│ 入口层   RosettaRemoteDebugBridge(@Mod)                                    │
│          静态初始化 / commonSetup / clientSetup / onServerStarting         │
│          EVENT_BUS(RosettaEventBus 实例,脚本事件总线)                      │
├────────────────────────────────────────────────────────────────────────────┤
│ 核心编排 RosettaCore:目录初始化、示例生成、按 ScriptType 装载、热重载、     │
│          资源/数据包注册、网络通道自动初始化                               │
├────────────────────────────────────────────────────────────────────────────┤
│ 脚本引擎 JavaScriptLoader(发现/编排/执行)                                 │
│          McpToSrgTransformer(运行时命名探测后的源码名称转换)               │
│          JavaSourceCompiler(ECJ 内存编译 + FileManager 修正 + classpath)   │
│          DynamicClassLoader(多 class 注册与脚本类加载)                     │
│          CompiledClass{className, bytecode, allClasses}                    │
│          ClassReplacementManager(类替换:仅编译,运行期需 agent)            │
├────────────────────────────────────────────────────────────────────────────┤
│ 脚本 API RosettaEventBus / RosettaSubscribeEvent / RosettaEventSubscriber  │
│          ForgeEventBridge(Forge 事件桥,MOD 6 + FORGE 191)                 │
│          MinecraftHelper(映射反射助手 + SRG/官方命名探测)                  │
│          NetworkUtils(重写:自动初始化/单次注册/命名频道 QuickPacket)       │
│          RegUtils(注册封装,自定义注册表保存与校验)                         │
│          RosettaResourcePack(assets/data 注入)                             │
├────────────────────────────────────────────────────────────────────────────┤
│ 字节码层 DynamicMixinLoader / MixinManager / MixinJarBuilder               │
│          BytecodeProviderWrapper / RosettaMixinConnector                   │
│          RefMapGenerator / MixinInfoInjector(磁盘兜底)                     │
│  (可选)  rainjava-core:ModLauncher 服务 + agent(补丁原生 Mixin)           │
├────────────────────────────────────────────────────────────────────────────┤
│ 支撑层   RosettaLogger / ScriptErrorCollector / ScriptError(UTF-8 日志)    │
│          RuntimeModuleOpener / ModuleAccessHelper / UnsafeClassDefiner     │
└────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 启动时序

| 时机 | 动作 |
|---|---|
| `RosettaRemoteDebugBridge` 类静态块 | 创建 `RosettaCore` → 立即执行 **STARTUP** 脚本;构造期自动初始化网络通道 |
| 构造器 | 注册 `commonSetup` / `clientSetup` 到 mod 总线;注册自身到 Forge 总线 |
| `FMLCommonSetupEvent` | 空实现 |
| `FMLClientSetupEvent` | 执行 **CLIENT** 脚本 |
| `ServerStartingEvent` | 执行 **SERVER** 脚本 |
| `AddPackFindersEvent` | 注册 `rosetta_assets` / `rosetta_data` 资源包 |

要点:STARTUP 脚本在**类初始化阶段**执行(早于任何 Forge 生命周期事件),
可用于早期行为定制;此时其它 mod 可能尚未加载完毕,相关异常只进入日志与错误收集器。

### 2.3 运行时目录布局

```
<gameDir>/RosettaRemoteDebugBridge/
├─ startup/    游戏初始化时执行一次
├─ server/     每次开服/进世界时执行
├─ client/     客户端初始化时执行
├─ assets/     作为 rosetta_assets 资源包实时加载
├─ data/       作为 rosetta_data 数据包实时加载
├─ mixins/     (动态 Mixin 管线保留目录;当前环境自动降级)
├─ coremod/    (字段保留;1.0.0 上游未接线)
└─ README.txt  自动生成的说明(UTF-8)
```

`RosettaCore.initializeFolders()` 创建 `startup/server/client/assets/data`;
`createExampleFiles()` 首次运行生成 `startup/Example.java`(包名 `rosetta.startup`)与 `README.txt`。

---

## 3. 脚本执行引擎

### 3.1 加载流程总览

```
Files.walk(脚本目录)
   │  过滤:路径包含 mixins/ 或 replace/ 的跳过
   │  排序:按绝对路径排序(确定性)
   ▼
读取源码(UTF-8)
   ▼
运行时命名探测:MinecraftHelper.isSrgRuntime()
   │   官方映射(dev)  → 跳过转换
   │   SRG 命名(生产) → McpToSrgTransformer 转换 MCP 名称
   ▼
extractClassName() 解析包名/类名
   ▼
JavaSourceCompiler.compileFromString()       ← ECJ 纯内存编译
   ▼
CompiledClass{className, bytecode, allClasses}
   ▼
DynamicClassLoader 注册全部 class(按字节码真实二进制名,含 $ 内部类)
   ▼
processRainEventSubscriber()  ← @RosettaEventSubscriber 自动注册总线
   ▼
executeClass()                ← 入口方法发现与执行(Throwable 收口)
```

### 3.2 源码发现与过滤

`JavaScriptLoader.loadJavaScripts(Path)`:

- 递归收集 `*.java`;
- 过滤 `mixins/`、`replace/` 子路径(分别归 Mixin 与类替换管线);
- **加载前按路径排序**,保证初始化顺序确定;
- 输出 `Compiled x/y` 统计。

### 3.3 运行时命名探测与 MCP→SRG 转换

生产环境使用 SRG 名称(`m_xxxxx_`),脚本使用官方名称,因此需要源码级转换。当前实现:

- `MinecraftHelper.isSrgRuntime()`:反射 `ItemStack` 的 `EMPTY` / `f_41583_` 字段判断命名模式,
  结果缓存;`clearCache()` 可重置。
- **仅 SRG 运行时执行转换**(`McpToSrgTransformer.transformSource`),dev 官方映射环境跳过,
  避免把官方名改写成不存在的 SRG 名(这是 1.0.0 中导致 dev 脚本编译失败的缺陷之一)。

`McpToSrgTransformer` 使用 JavaParser AST 重写方法调用/字段访问/简单名,沿继承链查找 SRG 名,
仅在包白名单内改写;失败时回退原始源码并告警。

### 3.4 内存编译(ECJ)与 classpath 构建

`JavaSourceCompiler`:

- 强制使用重定位的 `net.rain.repack.ecj.internal.compiler.tool.EclipseCompiler`;
- 选项:`-source 17 -target 17 -encoding UTF-8 -warn:none -proceedOnError -g:vars,lines,source
  -preserveAllLocals` 及 Mixin 注解处理器参数;
- `buildClassPath()` 顺序去重拼装:
  1. `java.class.path`
  2. `jdk.module.path`(**dev 下补全 MC/Forge 模块 jar**)
  3. **mod 自身 code source**(`build/classes/java/main`,供脚本引用 mod API)
  4. 线程上下文/系统类加载器 URL 链(`URLClassLoader` 与 `BuiltinClassLoader.ucp`)
  5. `ModList` 中全部 mod 文件路径
  6. 通过已知 MC 类(`Item`/`Block`/`ForgeRegistries` 等)反查 jar
  7. 游戏根、`mods/`、`libraries/` 目录扫描
- 内存 FileObject 输入,`CustomFileManager` 捕获全部 CLASS 输出。

### 3.5 ECJ 文件管理修正(本次关键修复)

ECJ 的 `EclipseCompilerImpl.getCompilationUnits()` 对每个编译单元执行:

```
if (fileManager.contains(SOURCE_PATH, unit))      → 直接使用
else if (new File(unit.getName()).exists())       → 磁盘兜底
else throw IllegalArgumentException("unit.missing" → "File {0} is missing")
```

修复前 `CustomFileManager` 未声明 SOURCE_PATH 能力,ECJ 走磁盘兜底;
旧脚本包名 `rainjava.server` 与运行目录 `run/RainJava/` 在 **Windows 大小写不敏感**下偶然匹配,
因此长期“碰巧可用”。重命名为 `rosetta.server` / `RosettaRemoteDebugBridge` 后巧合消失,全部脚本编译失败。

**修复**:`CustomFileManager` 覆盖两处:

- `hasLocation(SOURCE_PATH)` → `true`;
- `contains(SOURCE_PATH, 内存源文件对象)` → `true`(其余情况委托默认实现)。

由此 ECJ 全程使用内存内容,**与包名、目录名、工作目录无关**。修复以独立探针
(`EcjProbe6`)在 JDK FileManager 上验证通过后再入工程。

### 3.6 多 class 注册与类加载

- `CustomFileManager.getAllCompiledClasses()` 收集全部输出;
- 每个 class 的**二进制名不再信任编译器传入名,而是解析字节码常量池 `this_class`**
  (`JavaSourceCompiler.readBinaryClassName`),正确处理 `Outer$Inner`;
- `JavaScriptLoader` 将 `CompiledClass.allClasses` 全部注册进 `DynamicClassLoader`,
  `findClass` 命中内存字节码时 `defineClass`;
- 解决了“内部类未注册导致反射解析方法签名时 `NoClassDefFoundError`”的崩服问题。

### 3.7 入口方法发现与执行

`executeClass` 规则:

1. `public static` 无参方法,依次尝试 `init` → `initialize` → `onLoad` → `load` → `register`;
2. `public static` 且参数为 `FMLJavaModLoadingContext` 的同名方法;
3. 都没有时:非抽象/非接口类尝试无参构造实例化。

异常处理:全部执行路径以 **`Throwable`** 收口,解包 `InvocationTargetException` 后写入
`ScriptErrorCollector` 与日志——脚本的链接错误/初始化异常**不会崩服**,并可在 `/java errors` 中查看。

### 3.8 热重载

```
/java reload <type>
  → ScriptErrorCollector.clear(type)
  → RosettaClientEvents.resetShownFlag()
  → RosettaCore.reload(type)
      → RosettaEventBus.unregisterByClassLoader(旧脚本类加载器)   ← 防止重复回调
      → new JavaScriptLoader(type)(重建编译器/classpath/类加载器)
      → doLoad(type)
```

旧类加载器连同其监听器一并注销,加载器整体替换;重载后监听器数量保持稳定(实测 1 → 1)。

### 3.9 类替换管线

`ClassReplacementManager` 编译 `RosettaRemoteDebugBridge/replace/` 下的源码到
`<CWD>/.rainjava_replacements`(历史目录名),**输入与输出文件均支持多 class 编译产物**。

当前版本在启动/重载时会输出明确警告:**替换产物不会被任何加载器消费**,运行期应用需要
agent/class-transformer;该功能保持“仅编译”语义,避免误导。

---

## 4. 脚本 API

### 4.1 事件总线(`com.rosetta.remotedebugbridge.eventbus`)

| 注解 | 目标 | 属性 |
|---|---|---|
| `@RosettaSubscribeEvent` | 方法 | `priority`(默认 NORMAL)、`receiveCanceled`(默认 false) |
| `@RosettaEventSubscriber` | 类 | `bus`(仅日志展示,不改变实际总线) |

- **注册**:脚本类标注 `@RosettaEventSubscriber` 后由加载器自动 `EVENT_BUS.register(clazz)`;
  要求监听方法 `static`、`public`、恰好 1 个参数;类检查带防御(反射失败仅告警不崩)。
- **分发**:沿事件类超类+接口 BFS,按优先级 HIGHEST→MONITOR 同步反射调用;单监听器异常隔离。
- **取消语义**:仅对 Forge `Event` 生效;`receiveCanceled=false` 且非 MONITOR 的监听器在取消后跳过。
- **线程模型**:在触发线程同步执行(服务端 tick 在 Server thread,渲染事件在 Render thread 等)。
- **注销**:`unregister(Class)`、`unregisterAll()`、`unregisterByClassLoader(ClassLoader)`
  (热重载专用)、查询 API `getRegisteredClasses/getRegisteredEventTypes/getTotalListenerCount`。

### 4.2 Forge 事件桥(`api.ForgeEventBridge`)

通过 `@Mod.EventBusSubscriber` 把 Forge 事件原样转发到脚本总线:

| 总线 | 数量 | 代表事件 |
|---|---:|---|
| MOD | 6 | `FMLCommonSetupEvent`、`FMLClientSetupEvent`、`FMLDedicatedServerSetupEvent`、`InterModEnqueueEvent`、`InterModProcessEvent`、`FMLLoadCompleteEvent` |
| FORGE | 191 | Tick 族、生命周期、玩家/交互/物品、生物/伤害/生成、方块/世界/区块、命令/聊天、注册/数据等 |

### 4.3 映射反射助手(`script.utils.MinecraftHelper`)

- 数据源:优先 jar 内 `assets/mappings/map/mappings.tsrg`(TSRG2),失败回退磁盘映射文件;
- 提供类名/方法(简单键+描述符键)/字段/返回类型映射与 `isSrgRuntime()` 命名探测;
- 访问 API:`getStaticField/setStaticField/getField/setField`、`invokeStaticMethod/invokeMethod`
  (支持类名+方法名字符串形式)、`findFieldType`、`clearCache`;
- **方法解析已重写**:签名感知缓存、父类链查找、基本类型解箱匹配、重载消歧并在歧义时告警,
  修复了上游“缓存键不含参数签名导致重载静默调错”的问题。

### 4.4 网络封装(`script.util.NetworkUtils`,本版完整重写)

- `init(modId)`:幂等创建 `SimpleChannel("<modId>:main")`,协议版本 `"1"`,双端校验;
  **mod 在 `RosettaCore` 构造期自动以 `rosetta_remote_debug_bridge` 初始化**,脚本无需手动调用。
- 注册:每个消息类**只注册一次**(无方向参数),接收侧按 `ctx.getDirection().getReceptionSide()`
  分发到 `handleServer(player)` / `handleClient()`;`init` 之前的注册进入待注册队列。
- `PacketBuilder` DSL:`writeString/Int/Long/Float/Double/Boolean/Bytes`,生成 `QuickPacket`;
- `QuickPacket`:**命名频道 + 字节负载**;处理器通过
  `onServerReceive(channel, handler)` / `onClientReceive(channel, handler)` 全局注册
  (修复了上游处理器字段不参与序列化的问题);缺处理器时仅告警。
- 分发 API:`sendToServer`、`sendToPlayer`、`sendToAllPlayers`、`sendToNearby(radius)`、`sendToDimension`。
- 修复了上游类初始化即抛 `ExceptionInInitializerError` 的致命缺陷,并对晚注册给出兼容性告警。

### 4.5 注册封装(`script.util.RegUtils`)

- `init(modId, modEventBus)`:校验 modId 与事件总线,建立 `ModRegistries`
  (`BLOCKS`/`ITEMS`/`ENTITY_TYPES` 三个 `DeferredRegister`);
- 便捷方法:`block/item/blockWithItem/blockItem/stone/entity/itemProps/copy` 等;
- `createRegister(...)` 创建自定义注册表并**保存引用**,可通过
  `getCustomRegister(modId, registryName)` 取回;存储容器为并发 Map。

### 4.6 资源/数据包注入

| 目录 | Pack id | 类型 | 行为 |
|---|---|---|---|
| `RosettaRemoteDebugBridge/assets/` | `rosetta_assets` | `CLIENT_RESOURCES` | `Pack.Position.TOP`,required,实时读盘 |
| `RosettaRemoteDebugBridge/data/` | `rosetta_data` | `SERVER_DATA` | 同上 |

`RosettaResourcePack implements PackResources`:命名空间为一级子目录,`getResource` 实时从磁盘读取,
改文件后 `F3+T` 即生效;`pack_format=15`。

---

## 5. Mixin / CoreMod 子系统

> 设计完整保留;完整链路依赖外部 agent,当前环境自动检测并降级,不崩服。

### 5.1 设计链路

磁盘管线(`MixinManager.runFullWorkflow`):

```
scanMixinSources() → needsRecompile() → compileMixins() → generateRefMap()
→ generateMixinConfig() → saveCompileState() → validateConfiguration() → 提示重启
```

运行期内存管线(`DynamicMixinLoader`):

```
MixinProcessorHolder.getInstance()
→ BytecodeProviderWrapper 包装并替换 Mixin 服务字节码提供器
→ JavaSourceCompiler 编译 RosettaRemoteDebugBridge/mixins/ 源码
→ 字节码登记(双键 a.b.C / a/b/C)
→ MixinConfig.createDynamic("dynamic_rosetta_<uuid8>","rosetta.mixins",1000,false)
→ DefaultMixinConfigPlugin.registerDynamicMixin(name)
→ 注入 extensions/service/plugin 并挂入 MixinProcessor.configs
→ 目标类加载时经 IClassBytecodeProvider.getClassNode() 命中内存字节码完成注入
```

### 5.2 分支 Mixin 的扩展 API

分支(`org.spongepowered.rain.asm`)新增:

| 扩展 | 作用 |
|---|---|
| `MixinProcessorHolder` | 全局 `MixinProcessor` 持有者(`get/setInstance`、`isAvailable`) |
| `MixinConfig.createDynamic(...)` | 构造空动态配置 |
| `MixinConfig.registerDynamicMixin(name, bytes)` | 反射定义类 → 解析 → 构造 `MixinInfo` |
| `DefaultMixinConfigPlugin.registerDynamicMixin(name)` | 静态注册表 |
| `MixinServiceModLauncher.forceInitializeBytecodeProvider()` | 预热字节码提供器 |

### 5.3 agent 补丁与磁盘兜底

`rainjava-core`(外部 jar)提供 ModLauncher 服务与内嵌 Java agent:自附加后补丁**原生 Mixin**
的 `MixinInfo.loadMixinClass`,注入 `MixinInfoInjector.getMixinClassNode`,当正常提供器
找不到 Mixin 类时从 `<gameDir>/.rosetta_mixin/<name>.class` 读取(磁盘兜底)。

### 5.4 模块绕过

| 组件 | 手段 | 说明 |
|---|---|---|
| `RuntimeModuleOpener` | `implAddOpensToAllUnnamed` / `Unsafe` 修改 `Module.openPackages` | 每次创建脚本加载器时调用;失败降级为日志 |
| `ModuleAccessHelper` | `implAddOpens/implAddReads` | 保留工具 |
| `UnsafeClassDefiner` | **已重写**:trusted `MethodHandles.Lookup` + `ClassLoader.defineClass` | 原实现为坏死代码 |

### 5.5 当前接线状态与降级策略

- `JavaScriptLoader.processMixins()` 现在会:
  1. 检查 `MixinProcessorHolder.isAvailable()`;
  2. 不可用时输出明确警告(提示需要 rainjava-core agent),**脚本与事件不受影响**;
  3. 可用时尝试运行 `DynamicMixinLoader`,异常收口到 `ScriptErrorCollector` 且不崩服。
- `DynamicMixinLoader` 对 `MixinProcessor == null` 的情况明确日志并终止本次尝试。
- 构建时剥离了分支 Mixin 的 `META-INF/services`,避免与 Forge 原生 Mixin 的转换服务重名冲突
  (上游 `Duplicate key mixin` 启动崩溃的根因)。
- **结论**:动态 Mixin 的完整生效仍取决于运行环境(需要分支服务或 agent 激活);
  本版保证“可检测、可降级、可诊断”。

---

## 6. 命令与权限

注册于 `RegisterCommandsEvent`,根命令 `/java` 与 `/j`,统一要求权限等级 2。

| 命令 | 行为 |
|---|---|
| `/java reload [startup\|server\|client]` | 无参重载全部(顺序 SERVER→CLIENT→STARTUP);含监听器反注册 |
| `/java errors [startup\|server\|client]` | 无参显示全部;输出计数与前 5 条明细,附日志打开链接 |
| `/java hand getId` | 手持物品注册名(可点击复制);空手提示 |
| `/java hand getClass` | 手持物品类名(可复制);`ItemStack` 运行时类不同则追加一行 |

反馈格式:开始 `▶`(黄)/ 成功 `✔`(绿)/ 失败 `✘`(红,附 `[Open Log]` 与
`[View Error Screen]`,后者指向已注册的 `/java errors <type>`)。

---

## 7. 日志与错误系统

### 7.1 日志(`RosettaLogger`)

- 每类脚本独立文件:`<gameDir>/logs/Rosetta/{startup|server|client}.log`(UTF-8,截断模式,自动 flush);
- 格式 `[yyyy-MM-dd HH:mm:ss] [TYPE/LEVEL] message`,镜像到 Log4j(`RosettaRemoteDebugBridge`);
- 编译器输出独立成块(`=== Compiler Output ===`)。

### 7.2 错误模型

- `ScriptError`:类型(ERROR/WARN)、脚本类型、消息、文件名、行号、时间戳、堆栈;
  `fromThrowable` 会**解包** `InvocationTargetException` / `ExceptionInInitializerError`,保证消息可读。
- `ScriptErrorCollector`:按 `ScriptType` 分桶的并发列表,供 `/java errors` 与客户端错误屏消费;
  编译错误与运行期异常(执行/注册/扫描)均会进入收集器。

### 7.3 客户端错误界面

- 主菜单检测到 STARTUP 错误时自动弹出(单次);进世界后若有错误/警告,聊天栏提示并可点击查看;
- 列表显示 `文件名:行号`、时间、消息;悬停看堆栈;双击左键打开脚本、双击右键复制堆栈;
- 提供 `Open Log File`、`Close`、STARTUP 场景下的 `Quit Game`;支持错误/警告视图切换。

---

## 8. 典型数据流

### 8.1 一次热重载

```
玩家: /java reload server
 → RosettaCommands.reload(ctx, SERVER)
     → ScriptErrorCollector.clear(SERVER)
     → RosettaCore.reload(SERVER)
         → RosettaEventBus.unregisterByClassLoader(旧加载器)
         → new JavaScriptLoader(SERVER)   (ECJ/classpath/类加载器重建)
         → doLoad(SERVER)
             → 扫描→排序→命名探测→(SRG 时)转换→ECJ 内存编译
             → 全部 class 注册(字节码真实二进制名)→ 入口执行(Throwable 收口)
     → 统计结果 → 聊天栏反馈(✔/✘)
```

### 8.2 一次事件派发

```
Forge Server thread 触发 TickEvent.PlayerTickEvent
 → ForgeEventBridge.ForgeBusHandler.onPlayerTick(e)
     → RosettaEventBus.post(e)
         → BFS 收集类型 → 按优先级反射调用脚本监听器
```

### 8.3 一次网络消息

```
脚本: NetworkUtils.createPacket("mychan").writeString("hi").build().sendToPlayer(player)
      (通道已由 mod 构造期自动初始化)
接收端: QuickPacket.handleClient()
      → CLIENT_RECEIVERS.get("mychan") → 用户处理器(FriendlyByteBuf)
```

### 8.4 资源热更新

```
修改 RosettaRemoteDebugBridge/assets/<ns>/... → F3+T
 → RosettaResourcePack.getResource() 实时读盘 → 生效,无需重启
```

---

## 9. 工程质量与验证

### 9.1 自动化测试体系

`autotest/` 提供可复现的无人值守测试(外部驱动 + 脚本内断言):

| 文件 | 作用 |
|---|---|
| `autotest/server/AutoTest.java` | 测试驱动器:反射等待服务器就绪 → 执行命令 → 断言 → 写结果文件 → 自动退出客户端 |
| `autotest/server/Listener.java` | `@RosettaEventSubscriber` 监听器(验证热重载不重复注册) |
| `autotest/server/McImportTest.java` | 直接 `import net.minecraft.world.item.ItemStack`(验证脚本编译器 classpath 与命名转换) |

流程:预生成世界 → 复制为 `run/saves/autotest` → `gradlew runClient -PquickPlay=autotest`
→ 脚本自动进世界执行 → 结果写入 `run/rosetta-autotest-result.txt` → 客户端自动退出。

### 9.2 最近验证结果

```
phase=client
server=found
serverRunning=true
cmd_java_errors=1
cmd_java_reload_startup=1
cmd_java_hand_getId=1
listeners_before=1
errors_before=0
networkutils=OK
cmd_reload_with_temp_throw=1
errors_during_temp_throw=1
temp_throw_detected=1
cmd_reload_cleanup=1
errors_after_cleanup=0
listeners_after=1
listener_reload_stable=1
PASS=1
```

覆盖点:脚本编译与执行、MC 导入编译、网络通道初始化、运行期异常收集、
热重载监听器稳定性、命令族可用、客户端自动进出、世界正常保存。

### 9.3 缺陷修复清单(相对上游 1.0.0)

| 级别 | 问题 | 现状 |
|---|---|---|
| 高 | `NetworkUtils` 静态初始化即崩、双向重复注册、QuickPacket 处理器不可序列化 | 完整重写(§4.4) |
| 高 | 脚本运行期异常不进错误收集器 | 全部 `Throwable` 收口并解包(§3.7) |
| 高 | 内部类编译产物未注册 → `NoClassDefFoundError` 崩服 | 按字节码真实二进制名全量注册(§3.6) |
| 高 | ECJ 编译单元磁盘兜底 → `File ... is missing` | `hasLocation/contains` 修正为纯内存(§3.5) |
| 高 | Mixin/CoreMod 管线未接线 | 可检测、可降级、可诊断(§5.5) |
| 中 | 热重载不反注册监听器 | `unregisterByClassLoader` + 重载清理(§3.8) |
| 中 | 命令链接指向未注册命令 | 统一指向 `/java errors <type>` |
| 中 | `MinecraftHelper` 方法缓存忽略参数签名、基本类型匹配脆弱 | 解析重写 + 命名探测(§4.3) |
| 中 | dev 环境脚本编译 classpath 缺失 + MCP→SRG 误转 | module path/自身 code source + SRG 门控(§3.3/3.4) |
| 中 | `UnsafeClassDefiner` 坏死代码 | 现代实现(§5.4) |
| 低 | 脚本执行顺序不确定 | 路径排序(§3.2) |
| 低 | `RegUtils` 自定义注册表不保存、id 未校验 | 保存/取回 + 校验(§4.5) |
| 低 | `ClassReplacementManager` 死功能 | 明确“仅编译”语义 + 多 class 支持(§3.9) |

### 9.4 命名迁移记录(本次)

- 包名 `net.rain.rainjava.*` / `net.rain.eventbus.*` → `com.rosetta.remotedebugbridge.*`;
- 类名 `RainJava*`/`Rain*` → `Rosetta*`(入口类为 `RosettaRemoteDebugBridge`);
- modId/显示名/分组 → `rosetta_remote_debug_bridge` / RosettaRemoteDebugBridge / `com.rosetta.remotedebugbridge`;
- 目录与资源:`RosettaRemoteDebugBridge/`、`rosetta_assets`/`rosetta_data`、
  `logs/Rosetta/`、`.rosetta_mixin/`、`rosetta.mixins.json`、`rosetta.refmap.json`;
- 脚本示例包名:`rosetta.startup` / `rosetta.server` / `rosetta.client`;
- **保留的外部命名空间**:`net.rain.repack.*`、`org.spongepowered.rain.asm`、
  `cpw.mods.modlauncher.MixinCore`(均为依赖/运行时机制需要,不改)。

### 9.5 安全模型

- **信任边界 = 文件写入权限**:脚本零沙箱,可读写文件、执行进程、开网络端口、改字节码;
  `RosettaRemoteDebugBridge/` 目录的写入者等价于任意代码执行;
- `/java` 命令要求 OP 2,但只是操作入口,不构成安全边界;
- 防御性设计:执行/注册/扫描 `Throwable` 收口、监听器异常隔离、错误收集与界面提示、UTF-8 日志;
- 适用场景:单人 / 整合包 / 调试;不适用于多租户或不受信脚本环境。

### 9.6 已知限制与后续工作

1. **动态 Mixin 完整生效依赖外部 agent/分支服务**(§5.5),当前保证降级与诊断;
2. **`coremod/` 目录未接线**(上游遗留);
3. **类替换仅编译、运行期不应用**(需 agent/class-transformer);
4. 脚本编译器对**基本类型宽化**(如 `int`→`long`)不做隐式匹配;`MinecraftHelper` 对
   `null` 实参存在歧义时按评分选择并告警;
5. 生产 jar 已通过 reobf 构建,**尚未在正式(非 dev)客户端实测**;
6. 事件桥为全量转发,重脚本场景建议自行做节流。

---

## 10. 附录

### 附录 A:类清单(当前)

| 包 | 类 |
|---|---|
| `com.rosetta.remotedebugbridge` | `RosettaRemoteDebugBridge`(入口) |
| `.core` | `RosettaCore`、`ScriptType` |
| `.script` | `JavaScriptLoader`、`JavaSourceCompiler`(+4 内部类)、`DynamicClassLoader`、`CompiledClass`、`ClassReplacementManager`、`DynamicMixinLoader`、`MixinUtils` |
| `.script.helper` | `BytecodeProviderInstaller`、`BytecodeProviderWrapper`、`MixinServiceHelper`、`MixinConfigHelper`、`ModuleAccessHelper`、`RuntimeModuleOpener`、`UnsafeClassDefiner` |
| `.script.transformer` | `McpToSrgTransformer` |
| `.script.util` | `NetworkUtils`(+内部类)、`RegUtils` |
| `.script.utils` | `MinecraftHelper`、`MC` |
| `.mixin` | `MixinManager`(+内部类)、`MixinJarBuilder`、`MixinDebugHelper`、`MixinDiagnosticTool`、`RosettaMixinConnector` |
| `.mixin.refmap` | `RefMapGenerator` |
| `.api` | `ForgeEventBridge` |
| `.command` | `RosettaCommands` |
| `.client` | `RosettaClientEvents`、`RosettaErrorScreen` |
| `.resources` | `RosettaResourcePack` |
| `.logging` | `RosettaLogger`、`ScriptError`、`ScriptErrorCollector` |
| `.utils` | `PathUtils` |
| `.eventbus` | `RosettaSubscribeEvent`、`RosettaEventSubscriber`、`bus.RosettaEventBus` |
| `cpw.mods.modlauncher.MixinCore` | `MixinInfoInjector`(磁盘兜底,包名保留) |

共 41 个源文件。

### 附录 B:路径与标识约定

| 路径/标识 | 用途 |
|---|---|
| `RosettaRemoteDebugBridge/{startup,server,client}` | 脚本目录 |
| `RosettaRemoteDebugBridge/{assets,data}` | 资源/数据包(实时读取) |
| `logs/Rosetta/{startup,server,client}.log` | 分类日志(UTF-8) |
| `.rosetta_mixin/` | 动态 Mixin 运行目录(配置/refmap/状态/class 兜底) |
| `.rosetta_mixin/rosetta.mixins.json` | 动态 Mixin 配置 |
| `.rosetta_mixin/rosetta.refmap.json` | refmap |
| `run/rosetta-autotest-result.txt` | 自动化测试结果文件 |
| `/java`、`/j` | 命令族(权限等级 2) |

### 附录 C:构建 / 运行 / 测试命令

```powershell
# 构建(输出 build/libs/rosetta_remote_debug_bridge-1.0.0.jar)
.\gradlew.bat build

# 开发运行
.\gradlew.bat runClient
.\gradlew.bat runServer --nogui

# 无人值守测试(自动进 autotest 世界、执行断言、自动退出)
.\gradlew.bat runClient -PquickPlay=autotest
```

### 附录 D:Git 历史

```
efabf47 Rename project to RosettaRemoteDebugBridge and fix ECJ in-memory source handling
9a32d89 Fix 1.0.0 defects: network stack, script error handling, hot-reload, compiler, dev classpath
38c4d74 Rewrite technical report to focus on RainJava project architecture and internals
919e0aa Add technical report and usage guide
d17b92e Remove CFR decompile summary artifact from source tree
56d4c1d Initial commit: RainJava 1.0.0 reconstructed for Forge 1.20.1
```

> 上游项目 RainJava 由原作者 RainMelody 发布;本工程为其 1.0.0 的重构与重命名版本。
