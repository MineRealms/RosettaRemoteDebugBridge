# RainJava 1.0.0 — 重建项目技术报告与使用指南

> 适用版本: Minecraft 1.20.1 / Forge 47.4.10 / Java 17
> 项目位置: `H:\MinecraftMods\RainJava-work\RainJava-MDK`

---

## 目录

1. [项目概览](#1-项目概览)
2. [原始工件清单](#2-原始工件清单)
3. [混淆状态判定(关键结论)](#3-混淆状态判定关键结论)
4. [逆向分析过程](#4-逆向分析过程)
5. [工程重建(MDK)](#5-工程重建mdk)
6. [运行时问题与修复](#6-运行时问题与修复)
7. [全自动测试方案](#7-全自动测试方案)
8. [ProGuard 反混淆工具](#8-proguard-反混淆工具)
9. [项目结构说明](#9-项目结构说明)
10. [使用指南](#10-使用指南)
11. [已知限制与后续工作](#11-已知限制与后续工作)
12. [附录](#12-附录)

---

## 1. 项目概览

### 1.1 目标

作者(RainMelody)提供的 `RainJava-main.zip` 只有构建脚本、ProGuard 混淆配置和混淆映射表,没有源码(`src/` 不存在)。目标是从公开发布的 jar 出发,重建出一个:

- **可编译**的 Forge 1.20.1 工程(Forge MDK 结构)
- **可在单人游戏 RunClient 实际运行**的 mod
- 附带可用的 **ProGuard 反混淆工具**(一旦拿到混淆版 jar 即可还原)

### 1.2 最终状态

| 项目 | 状态 | 说明 |
|---|---|---|
| CFR 反编译 | 完成 | 69 个 class → 41 个 `.java` |
| SRG→Mojmap 修复 | 完成 | 7 个文件 235 处替换,0 遗留 |
| 编译 | 通过 | `gradlew build` 成功,含 `reobfJar` |
| dev 客户端运行 | 通过 | 启动到主菜单,mod 正常初始化 |
| 单人世界运行 | 通过 | 全自动 quickPlay 测试,命令/热重载全部验证 |
| 生产 jar | 已产出(**未在正式客户端实测**) | `build/libs/rainjava-1.0.0.jar` |
| 反混淆工具 | 完成并自验证 | 类名 100%、成员 100%(ProGuard 往返测试) |
| 1.0.7 真实反混淆 | **无法执行** | 对应 jar 未公开,工具已就绪待用 |
| Git 仓库 | 已建立 | 提交 `56d4c1d`、`d17b92e` |

### 1.3 规模数据

- 反编译产物:41 个源文件(含内部类合并)
- 编译错误修复:23 个真实错误(反编译器产物类问题)
- 运行时问题修复:2 个(服务名冲突、JPMS 模块可见性)
- 最终 jar:`1,834,507` 字节(原版 `1,835,449`)

---

## 2. 原始工件清单

| 工件 | 大小 | 说明 |
|---|---:|---|
| `G:\DOWNLOAD-EDGE\RainJava-main.zip` | 18,130,401 | 作者仓库(无源码):build.gradle、proguard/、libs/、mapping.txt |
| `mapping.txt`(zip 内) | 75,611 | **1.0.7 的 ProGuard 映射表** |
| `used-configuration.txt` | 2,765 | ProGuard `-printconfiguration` 输出,证明输入为 `rain_java-1.0.7.jar` |
| `proguard/super-obfuscate.pro` | 4,474 | 激进混淆配置(见 §3.2) |
| `libs/rainapi-1.0.0.jar` | 3,158,508 | RainAPI 旧版(未重定位 `org/eclipse`) |
| `libs/rainjava-core-1.0.0.jar` | 178,502 | ModLauncher 服务 + 内嵌 agent(动态 Mixin 字节码提供器) |
| `libs/mixin-0.8.5.jar` | 1,043,892 | **改名的 Mixin 分支**:包名 `org.spongepowered.rain.asm` |
| `libs/org.eclipse.jdt.core_*.jar` | 6,924,526 | ECJ 编译器(未重定位) |
| `libs/org.eclipse.equinox.common_*.jar` | 143,036 | ECJ 依赖 |
| `G:\DOWNLOAD-EDGE\RainAPI-1.0.2.jar` | 7,562,333 | **可用版 RainAPI**:`net/rain/repack/{ecj,javaparser,javassist}` |
| `H:\MinecraftMods\rain_java-1.0.0-all.jar` | 1,835,449 | Modrinth 公开发布版(**未混淆**) |
| `MixinInfoInjector.class`(zip 内) | 2,344 | 注入到 `cpw.mods.modlauncher.MixinCore` 包的工具类 |
| `base64.txt` / `injector_base64.txt` | 3,320 / 3,128 | 上述类的 base64 副本(MixinJS 上游版本) |

### 2.1 混淆配置要点(super-obfuscate.pro)

```
-dontshrink                          # 不裁剪,只改名
-repackageclasses 'OoOo0Oo...O'      # 全部塞进单包
-obfuscationdictionary  ...          # 字典只含 O/o/0 字符(视觉混淆)
-overloadaggressively                # 允许仅返回类型不同的重载
-useuniqueclassmembernames
-adaptclassstrings                   # 字符串常量里的类名也改
-adaptresourcefilenames/contents     # 资源文件名/内容里的类名也改
-keepattributes SourceFile,LineNumberTable,*Annotation*
-optimizationpasses 10 / method/inlining/*
```

---

## 3. 混淆状态判定(关键结论)

**公开的 jar 全部没有混淆,`mapping.txt` 对应的是未公开的 1.0.7 版本。**

验证方式(枚举 zip 条目中的混淆特征 `OoOo0Oo0Oo0Oo0`):

| jar | 混淆条目数 |
|---|---:|
| `rain_java-1.0.0-all.jar`(Modrinth) | 0 |
| `RainAPI-1.0.2.jar` | 0 |
| `libs/rainjava-core-1.0.0.jar` | 0 |

证据链:

1. `used-configuration.txt` 显示 ProGuard 输入为 `.../build/libs/rain_java-1.0.7.jar`,输出 `rain_java-1.0.7-visual-obf.jar`(开发机 Termux 环境)。
2. `mapping.txt` 含 50 个类映射(49 个 `net.rain.rainjava.*` + 1 个诱饵 `cpw.mods.modlauncher.MixinCore.MixinInfoInjector`),而 1.0.0 jar 有 69 个类,类集合与映射不一致(1.0.7 重构过包结构)。
3. CurseForge 项目文件列表为空、Modrinth 只有 1.0.0 → 1.0.7 混淆 jar 无处可下。

**结论**:对 1.0.0 无需反混淆(直接 CFR 反编译);反混淆工具按 mapping.txt 格式开发并已完成自验证,拿到 1.0.7 jar 后可直接使用。

---

## 4. 逆向分析过程

### 4.1 反编译

```
java -jar cfr-0.152.jar rain_java-1.0.0-all.jar \
     --outputdir decompiled/rain_java-1.0.0 --silent true
```

CFR 0.152 → 41 个 `.java`(69 个 class 中的内部类被合并进父类)。

### 4.2 依赖分析(决定工程如何配依赖)

| 依赖 | 提供方 | 用途 |
|---|---|---|
| `org.spongepowered.rain.asm.*`(19 处 import) | `libs/mixin-0.8.5.jar`(**分支**) | 动态 Mixin 管线,含专属 API:`MixinProcessorHolder`、`MixinConfig.createDynamic`、`DefaultMixinConfigPlugin.registerDynamicMixin` |
| `net.rain.repack.ecj.*` | `RainAPI-1.0.2.jar` | 内置 Java 编译器(脚本编译) |
| `net.rain.repack.javaparser.*` | `RainAPI-1.0.2.jar` | 脚本源码解析(MCP→SRG 转换) |
| `org.spongepowered.asm.*`(仅 2 个文件) | Forge 自带 Mixin | `MixinInfoInjector`、`RainMixinConnector` |
| `sun.misc.Unsafe` | JDK | 绕过模块系统定义类 |
| `net.rain.api.*` | — | **代码实际没有引用**(仅 README 文案提及) |
| `net.rain.eventbus.*` | 源码自带 | 脚本事件总线 |

### 4.3 SRG → Mojmap 修复

发布 jar 的字节码里保留了 89 个 SRG 成员名(`m_xxxxx_`/`f_xxxxx_`),在 official 映射的 dev 环境无法编译。用 mod 自带的映射资源 `assets/mappings/map/mappings.tsrg`(tsrg2 格式,64,225 条成员映射)编写 Python 脚本批量替换:

- 脚本:`RainJava-work/fix_srg.py`
- 结果:**235 处替换 / 7 个文件 / 0 遗留**

受影响文件:`RainJavaErrorScreen`、`RainJavaCommands`、`RainJavaClientEvents`、`RainJavaResourcePack`、`RegUtils`、`NetworkUtils`、`RainJavaCore`。

### 4.4 损坏类重写(MixinInfoInjector)

CFR 对 `cpw.mods.modlauncher.MixinCore.MixinInfoInjector` 输出非法 Java(`GOTO`/类型混淆)。根据 `javap -p -c` 字节码手工重写,逻辑:

1. 先尝试 `provider.getClassNode(name, runTransformers)`;
2. 失败则从 `<gamedir>/.rain_mixin/<name>.class` 读取磁盘 class,用 `MixinClassReader` 解析为 ASM `ClassNode`;
3. IO 错误抛 `RuntimeException`。

这是"从磁盘热加载 Mixin class"的钩子(配合 rainjava-core 的 agent 使用)。

### 4.5 编译错误修复(23 处)

由独立 subagent 迭代修复,全部为反编译器产物,不改变逻辑:

| 类别 | 数量 | 典型修复 |
|---|---:|---|
| 原始类型(raw type) | 10 | 补全泛型局部变量,如 `ArrayList<Path>`、`DiagnosticCollector<JavaFileObject>` |
| lambda 类型推断 | 3 | 显式泛型(如 `SimplePacket` → `T` 的返回)、合成方法重命名 |
| 枚举 switch | 3 | `case ScriptType.SERVER:` → `case SERVER:` |
| 访问者泛型 | 3 | `visit(X, Object)` → `visit(X, Void)`/`super.visit(n, arg)` |
| 找不到符号 | 4 | 对照原始字节码重建(McpToSrgTransformer 等) |
| 其它 | — | `RuntimeModuleOpener` 按原字节码异常表重建 try/catch;`MinecraftHelper` 恢复 try-with-resources;`RainJavaErrorScreen` 去掉非法强转 |

---

## 5. 工程重建(MDK)

### 5.1 基础

- Forge MDK `1.20.1-47.4.10`,official 映射,Gradle 8.8(Wrapper),Java 17(Temurin)
- 源码放入 `src/main/java`,资源从原 jar 提取到 `src/main/resources`

### 5.2 build.gradle 关键改动

```groovy
dependencies {
    minecraft "net.minecraftforge:forge:${minecraft_version}-${forge_version}"
    compileOnly files('libs/rainapi-repack-1.0.2.jar')   // ECJ/JavaParser
    compileOnly files('libs/mixin-0.8.5-dev.jar')        // 分支 Mixin
}

tasks.named('processResources', ProcessResources).configure {
    ...
    from zipTree('libs/rainapi-repack-1.0.2.jar')  // 影子打包(见 §6.2)
    from zipTree('libs/mixin-0.8.5-dev.jar')
}

minecraft.runs.client {
    if (project.hasProperty('quickPlay')) {
        args '--quickPlaySingleplayer', project.property('quickPlay')  // 自动化测试入口
    }
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.fork = true
    options.forkOptions.jvmArgs += ['-Duser.language=en','-Duser.country=US','-Dfile.encoding=UTF-8']
    // ↑ 让 javac 输出英文错误(Windows 中文控制台会乱码)
}
```

### 5.3 资源与元数据修正

| 项 | 处理 |
|---|---|
| `mods.toml` | 依赖表键 `dependencies.rain_java` → `dependencies.rainjava`(与 modId 一致) |
| `java.mixins.json` | **删除**(孤儿文件:MANIFEST 无 `MixinConfigs`,且 client 项引用不存在的 `MixinBootstrap`,refmap 也缺失) |
| `META-INF/MANIFEST.MF`、`jarjar/metadata.json` | 从资源目录移除(构建时自动生成/无需) |
| `assets/mappings/map/mappings.tsrg` | 保留(9.1MB,脚本编译器运行时依赖) |
| `pack.mcmeta` | 保留(pack_format 15) |

### 5.4 依赖瘦身脚本

- `strip_rainapi.py`:从 `RainAPI-1.0.2.jar` 抽取 `net/rain/repack/**`(2,248 条)→ `rainapi-repack-1.0.2.jar`(5,755,492 字节)
- `strip_mixin.py`:从分支 Mixin jar 移除 `META-INF/services/**` 与 MANIFEST → `mixin-0.8.5-dev.jar`(688 条,1,041,462 字节)

---

## 6. 运行时问题与修复

### 6.1 问题一:`Duplicate key mixin`(启动即崩)

- 现象:`TransformationServicesHandler.discoverServices` 抛
  `IllegalStateException: Duplicate key mixin`
- 根因:分支 Mixin jar 的 `META-INF/services/cpw.mods.modlauncher.api.ITransformationService`
  注册了第二个名为 `mixin` 的转换服务,与 Forge 自带冲突。
- 修复:剥离该 jar 的全部 `META-INF/services/**`(见 §5.4)。

### 6.2 问题二:`NoClassDefFoundError: net/rain/repack/ecj/...`

- 现象:mod 构造时崩溃;普通 classpath 依赖(`implementation files(...)`)在 dev 环境不可见。
- 根因:FML/ModLauncher 以 JPMS 模块层加载 mod;classpath 上的普通 jar 属于 unnamed module,
  **named module 的 mod 默认读不到**(生产环境靠安装 RainAPI library mod 解决;dev 没有)。
- 修复:把 repack 与分支 Mixin 的类通过 `processResources { from zipTree(...) }` **影子打包进 mod 自身**,
  依赖改为 `compileOnly`。dev 与产物 jar 同时生效,且 mod 变为自包含。

修复后客户端正常进入主菜单,日志确认 mod 初始化:
`RainJava Core initialized at: ...run/RainJava`、`Mapped 6674 classes...`。

---

## 7. 全自动测试方案

### 7.1 设计(无人值守,自动进出游戏)

```
[1] gradlew runServer          → 生成 run/world(顺便验证服务端)
[2] 复制 run/world → run/saves/autotest
[3] gradlew runClient -PquickPlay=autotest
        ↓ 自动进入单人世界,集成服务端启动
[4] RainJava 加载 run/RainJava/server/*.java
        → AutoTest.init() 启动守护线程
        → 等服务器就绪 → 反射执行 3 条命令
        → 写 run/rainjava-autotest-result.txt
        → 反射调用 Minecraft.stop() 自动退出
[5] 轮询结果文件与日志,检查退出码
```

### 7.2 自动测试脚本

`run/RainJava/server/AutoTest.java`,关键设计:

- **纯反射 + 仅 JDK 依赖**:dev 环境脚本编译器找不到 MC/Forge jar(见 §11.1),直接 import 会编译失败
- 用 `FMLEnvironment.dist` 判断客户端(避免服务端误加载 `Minecraft` 类)
- `ServerLifecycleHooks.getCurrentServer()` 取服务器
- `Commands.performPrefixedCommand(createCommandSourceStack(), cmd)` 执行命令(控制台源,权限等级 4,无需开作弊)
- 结果写文件 + `Minecraft.getInstance().execute(() -> ...stop())`

### 7.3 实测结果

结果文件 `run/rainjava-autotest-result.txt`:

```
phase=client
server=found
serverRunning=true
cmd_java_errors=1
cmd_java_reload_startup=1
cmd_java_hand_getId=1
SUCCESS=1
```

日志关键行:

| 观察点 | 日志 |
|---|---|
| 映射加载 | `Loaded: 6674 classes, 31004 fields, 54309 methods from /assets/mappings/map/mappings.tsrg` |
| 启动脚本编译 | `Successfully compiled: rainjava.startup.Example (602 bytes)` |
| 热重载 | `RainJava: startup scripts reloaded successfully.` + 再次编译 Example |
| hand 命令路径 | `Error: A player is required to run this command here`(控制台无玩家,符合预期) |
| 干净退出 | `ThreadedAnvilChunkStorage (autotest): All chunks are saved` / `BUILD SUCCESSFUL in 1m 22s` |

---

## 8. ProGuard 反混淆工具

### 8.1 位置与用法

```
python H:\MinecraftMods\RainJava-work\deobf\proguard_deobf.py <混淆.jar> <mapping.txt> <输出.jar>
```

- 实现:纯 Python **原始常量池改写器**(jawa 对 46/69 个真实 Java 17 类无法往返,弃用)
- 两遍解析 mapping:跳过 inline 伪条目(如 `...:64:69 -> <init>`),方法键 = (混淆类, 混淆名, **完整描述符**),以正确处理 `-overloadaggressively`(仅返回类型不同的重载,共 31 组)
- 改名范围:类/父类/接口、字段/方法定义、描述符、泛型签名、注解、InnerClasses/EnclosingMethod/Record、invokedynamic(按接口/参数/绑定接收者解析)、类名字符串常量、资源文件名与 `.properties`/`.xml` 内容
- 附加:从 mapping 的 `# {"fileName":...}` 注释恢复 SourceFile

### 8.2 验证结果(ProGuard 7.3.2 往返)

用仓库自带 ProGuard + `super-obfuscate.pro` 混淆 1.0.0 jar 后再用本工具还原:

| 指标 | 结果 |
|---|---|
| 类条目路径 | **69/69(100%)** |
| 成员标识(名+描述符,`javap -p -s`) | **1037/1037(100%)** |
| 结构有效性 | 99.04%(仅 10 处 `-allowaccessmodification` 访问标志差异,mapping 不记录) |
| 零残留混淆引用 | 是 |
| 合成端到端(内部类/record/枚举/注解/资源) | 100%,反混淆 jar 运行输出与原版逐字节一致 |

### 8.3 真实 mapping 测试

用作者的 `mapping.txt`(1.0.7)套到 1.0.0 jar:运行无崩溃、0 命中(符合预期,版本不匹配)。**拿到 1.0.7 混淆 jar 后可直接还原**。

### 8.4 已知限制

- mapping 必须与目标 jar 同版本
- `-allowaccessmodification` 造成的访问标志放宽、被 ProGuard 删除的 Signature 无法恢复
- `-adaptclassstrings` 对"恰好等于成员名"的字符串同样改写,无法从 mapping 逆向区分

---

## 9. 项目结构说明

```
RainJava-MDK/
├─ build.gradle                  # MDK 配置 + 影子打包 + quickPlay 入口
├─ gradle.properties             # Forge 47.4.10 / official 1.20.1 / mod 元数据
├─ .gitignore
├─ libs/
│  ├─ rainapi-repack-1.0.2.jar   # ECJ + JavaParser(重定位,已影子打包)
│  ├─ mixin-0.8.5-dev.jar        # 分支 Mixin(剥离服务注册,已影子打包)
│  ├─ mixin-0.8.5.jar            # 原始分支(参考/重新生成用)
│  ├─ rainapi-1.0.0.jar          # 旧版 RainAPI(未使用)
│  └─ org.eclipse.*.jar          # 未重定位 ECJ(未使用)
├─ src/main/
│  ├─ java/
│  │  ├─ net/rain/rainjava/      # 主包
│  │  │  ├─ RainJava.java        # @Mod 入口
│  │  │  ├─ core/                # RainJavaCore(目录/脚本编排)、ScriptType
│  │  │  ├─ java/                # 脚本编译器、类加载器、脚本加载器、Mixin 管线
│  │  │  ├─ mixin/               # MixinManager/DynamicMixinLoader/工具(1.0.0 未接线)
│  │  │  ├─ command/             # /java /j 命令
│  │  │  ├─ client/              # 客户端事件、错误界面
│  │  │  ├─ api/                 # ForgeEventBridge(事件转发到脚本总线)
│  │  │  ├─ resources/           # 资源包注入
│  │  │  ├─ logging/             # 脚本日志/错误收集
│  │  │  └─ utils/               # 路径工具
│  │  ├─ net/rain/eventbus/      # 脚本事件总线
│  │  └─ cpw/mods/modlauncher/MixinCore/MixinInfoInjector.java  # 磁盘 Mixin 钩子
│  └─ resources/
│     ├─ META-INF/mods.toml
│     ├─ pack.mcmeta
│     └─ assets/mappings/map/mappings.tsrg   # 9.1MB SRG/Mojmap 映射(运行时用)
└─ TECHNICAL-REPORT.md           # 本文档
```

工作区(`H:\MinecraftMods\RainJava-work\`):

| 路径 | 说明 |
|---|---|
| `RainJava-MDK/` | 工作工程(git 仓库) |
| `deobf/` | 反混淆工具 + README + 验证报告 |
| `decompiled/` | CFR 原始反编译输出 |
| `jar-resources/` | 原 jar 资源提取 |
| `mdk-src/` | Forge MDK 原始骨架 |
| `fix_srg.py` / `strip_rainapi.py` / `strip_mixin.py` | 重建脚本 |
| `parse_log.py` | 编译日志解析(编码容错) |
| `tools/cfr-0.152.jar` | 反编译器 |
| `source-audit.md` | 源码审计报告 |
| `mapping-analysis-report.md` | 映射表分析报告 |
| `collision-check.py` | 映射冲突检查 |

---

## 10. 使用指南

### 10.1 构建

```
cd H:\MinecraftMods\RainJava-work\RainJava-MDK
.\gradlew.bat build
```

产物:`build\libs\rainjava-1.0.0.jar`(自包含,只需要 Forge 1.20.1;不必安装 RainAPI)。

### 10.2 开发运行

```
.\gradlew.bat runClient                       # 普通启动
.\gradlew.bat runClient -PquickPlay=autotest  # 自动进 autotest 存档
.\gradlew.bat runServer --nogui               # 服务端
```

### 10.3 脚本系统(核心功能)

脚本目录(dev 为 `run/RainJava/`,正式版为 `.minecraft/RainJava/`):

```
RainJava/
├─ startup/   游戏初始化时执行一次
├─ server/    每次开服/进世界时执行
├─ client/    客户端初始化时执行
├─ mixins/    (1.0.0 中未接线)
└─ coremod/   (1.0.0 中未接线)
```

规则:

- 任意 `.java` 文件,类中必须定义 `public static void init()`
- 放入文件夹后自动编译(内置 ECJ,无需系统 JDK)并执行
- 编译产物在内存中,不落地

示例:

```java
package rainjava.server;

public class MyScript {
    public static void init() {
        System.out.println("hello from script");
    }
}
```

命令(需要 OP / 权限等级 2):

| 命令 | 作用 |
|---|---|
| `/java reload [startup\|server\|client]` | 热重载脚本(免重启) |
| `/java errors [startup\|server\|client]` | 聊天栏查看错误/警告 |
| `/java hand getId` | 显示手持物品注册 ID |
| `/java hand getClass` | 显示手持物品类名 |
| `/j ...` | `/java` 的别名 |

### 10.4 远程执行与安全模型

**没有内置远程通道**:没有 Web 编辑器、没有远程控制台、没有网络上传 API(与作者描述的后续版本或 Coder 插件不同)。

但需要明确两点:

1. **脚本零沙箱**:脚本以完整 JVM 权限运行,可以读写文件、`Runtime.exec`、开 socket。因此
   "能写 `RainJava/` 目录 = 能在服务器进程里执行任意代码"。请严格限制该目录权限。
2. **自定义远程执行可行**:脚本里可直接使用 `java.net` 起 HTTP/socket 服务,结合 mod 自带的
   `JavaSourceCompiler`(编译)与 `DynamicClassLoader`(加载)实现 RCE 接口。属于用户自行实现的代码,
   务必加鉴权与来源限制。

### 10.5 反混淆

```
python H:\MinecraftMods\RainJava-work\deobf\proguard_deobf.py <混淆.jar> <mapping.txt> <输出.jar>
```

当前 `mapping.txt` 对应 1.0.7;1.0.0 公开 jar 未混淆,不需要处理。

---

## 11. 已知限制与后续工作

### 11.1 dev 环境脚本编译器 classpath 不完整

日志:`[JavaSourceCompiler] Found 0 Minecraft/Forge core jars.`
原因:编译器的类路径扫描针对正式版目录(`.minecraft/libraries` 等),dev 工程里不存在。

- 影响:脚本直接 `import net.minecraft.*`/`net.minecraftforge.*` 会编译失败
- 当前变通:用反射 + 字符串类名(我们的 `AutoTest` 即示例)
- 建议修复:给 `JavaSourceCompiler.buildClassPath()` 追加 `System.getProperty("java.class.path")`
  与 `jdk.module.path`,dev 下即可直接 import MC 类

### 11.2 动态 Mixin / CoreMod 管线未接线(1.0.0 固有问题)

源码中 `DynamicMixinLoader`/`MixinManager` 从未被实例化,`JavaScriptLoader.processMixins()` 为空,
`java.mixins.json` 也没被任何机制注册。即 README 宣传的 `mixins/`、`coremod/` 热注入在 1.0.0 中不可用。
1.0.7(有 mapping 的版本)疑似补齐了这部分,但无 jar 可验证。

### 11.3 其它

- 生产 jar 已通过 `reobfJar`,**尚未在正式(非 dev)客户端实测**
- mod 生成的示例文件中文注释乱码(charset 未指定 UTF-8)
- `NetworkUtils` 静态初始化顺序问题(`registerQuickPacket()` 早于 `init()`),首次使用会 `ExceptionInInitializerError`;1.0.0 启动路径未触发
- 完整 RainAPI library mod 未被本 mod 使用(代码不引用 `net.rain.api`),故未集成

---

## 12. 附录

### 附录 A:关键命令速查

```powershell
# 构建
.\gradlew.bat build

# dev 运行 + 自动测试
.\gradlew.bat runClient -PquickPlay=autotest

# 重新生成服务端世界并存档
.\gradlew.bat runServer --nogui
Copy-Item run\world run\saves\autotest -Recurse -Force

# 反混淆
python H:\MinecraftMods\RainJava-work\deobf\proguard_deobf.py obf.jar mapping.txt deobf.jar

# 重新生成影子依赖(如需)
python H:\MinecraftMods\RainJava-work\strip_rainapi.py
python H:\MinecraftMods\RainJava-work\strip_mixin.py
```

### 附录 B:关键验证记录

| 项 | 数据 |
|---|---|
| 原始发布 jar | 1,835,449 B / 103 条目 / 69 class |
| 重建 jar | 1,834,507 B(dev build 产物) |
| 映射表(SRG/Mojmap tsrg2) | 64,225 成员条目 |
| 1.0.7 ProGuard 映射 | 50 类 / 443 真实方法 / 141 字段 / 132 内联条目 |
| 反向映射冲突 | 真实成员 0 冲突(完整描述符键) |
| 反混淆往返 | 类 69/69、成员 1037/1037、结构 99.04% |
| 自动化测试 | `SUCCESS=1`,3/3 命令返回 1,客户端自动退出 |

### 附录 C:Git 历史

```
d17b92e Remove CFR decompile summary artifact from source tree
56d4c1d Initial commit: RainJava 1.0.0 reconstructed for Forge 1.20.1
```
