# Fabric 1.20.1 移植说明

分支：`fabric-1.20.1`（从 `master` 拉出，Forge 47.x 版本保持不变）。

目标不是 1:1 复刻 Forge 版本，而是保留"**运行期编译执行 Java + 远程控制面**"这一条
自动化测试最常用的链路；动态 Mixin、CoreMod、Bukkit 适配、CRD 客户端通道全部移除。

## 1. 构建与入口

| 项目 | Forge 分支 | Fabric 分支 |
|---|---|---|
| 构建 | ForgeGradle 6 + MixinGradle | Loom 1.11.8 + Gradle 8.14 |
| 映射 | `official`（开发期 Mojmap） | `loom.officialMojangMappings()`（开发期同为 Mojmap） |
| 元数据 | `META-INF/mods.toml` + mixin json | `fabric.mod.json`（`main` + `client` entrypoint） |
| 入口 | `@Mod` + `FMLJavaModLoadingContext` | `ModInitializer` / `ClientModInitializer` |
| 生命周期 | `ServerStarting/Started/StoppingEvent` | `ServerLifecycleEvents.SERVER_STARTED/STOPPING` |
| 命令 | `RegisterCommandsEvent` | `CommandRegistrationCallback` |
| 游戏目录 | `FMLPaths.GAMEDIR` | `FabricLoader#getGameDir`（统一走 `platform/Platform`） |
| 注册表 | `ForgeRegistries` | `BuiltInRegistries` |
| 事件总线 | Forge `Event` 基类 | 反射读取 `isCanceled()`，无基类要求 |

编译 classpath 发现逻辑（`JavaSourceCompiler`）新增了 Fabric 分支：
遍历 `FabricLoader#getAllMods()` 的 `getOrigin().getPaths()`，其余（mods 目录、
libraries、`.minecraft` 根目录、TCCL URL）保持原样。

## 2. 为什么移除动态 Mixin

Forge 版本依赖 ModLauncher 的 `ITransformationService` / 重定位后的 Mixin 服务
（`org.spongepowered.rain.asm.service.modlauncher`）在运行期注册配置并改写已加载类。
Fabric 使用 Knot 类加载器，没有 ModLauncher，也没有类重转换（retransform）API；
Fabric 自带的 Mixin 服务只接受启动前注册的配置。因此本分支不加载
`libs/mixin-0.8.5-dev.jar`，也不注册任何 mixin 配置。

静态 Mixin 本身仍可用（写进 `fabric.mod.json` 的 `mixins` 即可），只是本模组不需要。

## 3. 已验证行为（2026-10-18，Fabric 1.20.1 / Loom dev 专用服务器）

启动日志：

```
[Server thread/INFO] (RosettaRemoteDebugBridge) [SERVER] Server started - loading server scripts
[Server thread/WARN] (RosettaNexus/Remote) Remote bridge listening on 127.0.0.1:48790 ...
```

通过 TCP 实测：

```
ping    -> {"loader":"fabric","mappings":"named","java":"21.0.8","running":true}
exec    return net.minecraft.SharedConstants.getCurrentVersion().getName();
        -> "1.20.1  [3ms]"
exec    return RosettaRemoteDebugBridge.getServer().getPlayerCount() + " players";
        -> "0 players  [2ms]"
exec    var lvl = getServer().overworld(); lvl.setDayTime(6000L); return "dayTime=" + lvl.getDayTime();
        -> "dayTime=6000  [0ms]"
console java reload server
        -> 编译并执行 run-server/RosettaRemoteDebugBridge/server/Probe.java
           "[Probe] server script loaded, tick=864"
```

即：运行期编译、访问游戏类、读写服务器状态、脚本热重载全部可用。

## 4. 已知限制：生产（intermediary）运行期

Fabric 正式实例的 Minecraft 类名是 intermediary（`net.minecraft.class_310`），
而脚本按 Mojmap 书写。现有 `McpToSrgTransformer` 只重写**成员名**（Forge SRG 不改类名），
不会把 `net.minecraft.client.Minecraft` 改成 `net.minecraft.class_310`，因此
`exec` 在生产运行期会编译失败，并返回一条明确提示。

需要补的工作（二选一）：

1. 在 transformer 里补类名重写：import、类型、泛型、`X.class`、构造器、cast；
2. 或者引入 tiny-remapper：编译前把脚本按 Mojmap 编译，再把字节码整体重映射到 intermediary
   （需要随 jar 打包 1.20.1 的 official→intermediary 映射表）。

当前分支按"Loom dev 运行期（named）"验收，这也是 Fabric 模组开发与自动化回归的常规环境。

## 5. 使用文档与冒烟测试

完整的安装、脚本、命令与自动化测试写法见 [USAGE_FABRIC.md](USAGE_FABRIC.md)。



```bash
./gradlew runServer
python tools/fabric_smoke_test.py --token-file run-server/RosettaRemoteDebugBridge/remote-token.txt
```

脚本会依次执行 `ping`、两次 `exec`（含一次游戏类访问）和一次 `console list`，
任一断言失败则以非 0 退出，可直接接进 CI。
