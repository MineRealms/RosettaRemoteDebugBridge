# RosettaRemoteDebugBridge → NeoForge 1.21.1 移植报告

> 分支：`neoforge-1.21.1-port`
> 目标：`Minecraft 1.21.1` / `NeoForge 21.1.235` / `Java 21`
> 产物：`rosetta_remote_debug_bridge-1.0.1.jar`
> 约定：**本阶段不做 Mixin**（静态与动态 Mixin 均暂缓）

---

## 1. 结论

主体功能已在 NeoForge 1.21.1 **端到端通过**：构建、专用服启动、脚本引擎（内存编译 + 热重载）、
NeoForge 事件桥、远程控制面（TCP/ping/console/exec）、CRD 客户端调试（加密会话 + 加密操作）
均验证可用。Mixin 子系统按约定未接入。

---

## 2. 目标与参考

| 项 | 1.20.1 Forge（原） | 1.21.1 NeoForge（现） |
|---|---|---|
| 构建插件 | ForgeGradle `[6.0,6.2)` + MixinGradle 0.7.38 | `net.neoforged.moddev` 2.0.141 |
| Gradle / Java | 8.8 / 17 | 8.11.1 / 21 |
| 映射 | `official` + 运行期 SRG 重混淆 | Mojang 官方名，**无 SRG / 无 reobf** |
| 元数据 | `META-INF/mods.toml`，`loaderVersion="[47,)"` | `META-INF/neoforge.mods.toml`，`loaderVersion="[4,)"` |
| 资源包 | `pack_format` 15 | `pack_format` 34 |

---

## 3. 变更清单

### 3.1 构建与元数据
- `build.gradle`：ModDevGradle；`neoForge { runs/mods }`；删除 `reobfJar`；保留影子依赖
  `zipTree(libs/*.jar)` 与 `exclude 'module-info.class'`。
- `settings.gradle`：仓库改为 `maven.neoforged.net`。
- `gradle.properties`：`neo_version=21.1.235`、`minecraft_version=1.21.1`、`mod_version=1.0.1`。
- `gradle-wrapper.properties`：Gradle 8.11.1。
- `META-INF/mods.toml` → `META-INF/neoforge.mods.toml`（`modLoader="javafml"`，依赖 `neoforge`）。
- `pack.mcmeta`：`pack_format` 34。

### 3.2 入口与事件总线
- `RosettaRemoteDebugBridge`：`FMLJavaModLoadingContext` 已移除 → `@Mod` 构造器注入 `IEventBus`；
  暴露 `MOD_EVENT_BUS` 供脚本 `init(IEventBus)` 使用。
- `MinecraftForge.EVENT_BUS` → `NeoForge.EVENT_BUS`。
- `@EventBusSubscriber` 迁到 `net.neoforged.fml.common`；`Bus.FORGE` → `Bus.GAME`。
- `DistExecutor`（已移除）→ `FMLEnvironment.dist == Dist.CLIENT`。
- 取消判定：`Event.isCanceled()` → `ICancellableEvent.isCanceled()`。

### 3.3 事件桥（`api.ForgeEventBridge`）
见 §4 对账。

### 3.4 网络（`script.util.NetworkUtils`）
- 旧 `SimpleChannel`/`NetworkRegistry`/`IMessage` 全部移除 → 基于
  `CustomPacketPayload` + `StreamCodec` + `RegisterPayloadHandlersEvent`。
- 所有脚本消息统一包装为单个 `RosettaPayload(id, byte[])`，以 `playBidirectional` 注册一次；
  收到后按 id 反查工厂、反序列化、`handleServer/handleClient`。
- 保留脚本 API：`SimplePacket`、`PacketBuilder`、`QuickPacket`、`onServerReceive/onClientReceive`、
  `sendToServer/sendToPlayer/sendToAllPlayers/sendToNearby/sendToDimension`。
- 契约变化：不再提供 `getChannel()`（旧 SimpleChannel 入口）。

### 3.5 注册（`script.util.RegUtils`）
- `ForgeRegistries` → `BuiltInRegistries`（`Registries.BLOCK/ITEM/ENTITY_TYPE`）。
- `RegistryObject` → `DeferredHolder`；`IForgeRegistry` → `Registry`。
- `BlockBehaviour.Properties.copy` → `ofFullCopy`。

### 3.6 资源包（`resources.RosettaResourcePack` / `core.RosettaCore`）
- 实现 1.21.1 `PackResources#location()`（返回 `PackLocationInfo`）。
- `Pack.readMetaAndCreate(PackLocationInfo, ResourcesSupplier, PackType, PackSelectionConfig)`。
- `new ResourceLocation(ns, path)` → `ResourceLocation.fromNamespaceAndPath(ns, path)`。

### 3.7 客户端 GUI
- `TickEvent.ClientTickEvent` → `ClientTickEvent.Pre`；`TickEvent.RenderTickEvent` → `RenderFrameEvent.Pre`。
- `Screen.renderBackground` → 四参重载；`ObjectSelectionList` 构造器去掉 `y1` 并移除 `setRenderBackground`。

### 3.8 顺带修复
- `JavaSourceCompiler.findMinecraftDirectory`：根类路径条目导致 `Path.getFileName()==null` 的 NPE
  （NeoForge dev classpath 触发），已加空值保护。

---

## 4. 事件桥对账

- 原：MOD 总线 6 + 游戏总线 191。
- 现：MOD 总线 6 + 游戏总线 **179**（净减 12）。

**改名/搬家后仍桥接（19）**

| 原 | 现 |
|---|---|
| `TickEvent.ServerTickEvent` | `tick.ServerTickEvent.Pre` |
| `TickEvent.ClientTickEvent` | `client.event.ClientTickEvent.Pre` |
| `TickEvent.LevelTickEvent` | `tick.LevelTickEvent.Post` |
| `TickEvent.PlayerTickEvent` | `tick.PlayerTickEvent.Post` |
| `TickEvent.RenderTickEvent` | `client.event.RenderFrameEvent.Pre` |
| `LivingEvent.LivingTickEvent` | `tick.EntityTickEvent.Post` |
| `LivingAttackEvent` + `LivingHurtEvent` | `LivingIncomingDamageEvent` |
| `LivingDamageEvent`（抽象） | `LivingDamageEvent.Post` |
| `ShieldBlockEvent` | `LivingShieldBlockEvent` |
| `EntityItemPickupEvent` | `ItemEntityPickupEvent.Post` |
| `MobSpawnEvent.FinalizeSpawn` | `FinalizeSpawnEvent` |
| `MobSpawnEvent.AllowDespawn` | `MobDespawnEvent` |
| `SpawnPlacementRegisterEvent` | `RegisterSpawnPlacementsEvent` |
| `SaplingGrowTreeEvent` | `BlockGrowFeatureEvent` |
| `SleepingLocationCheckEvent` | `CanPlayerSleepEvent` |
| `SleepingTimeCheckEvent` | `CanContinueSleepingEvent` |
| `BlockEvent.CropGrowEvent.Pre/Post` | `level.block.CropGrowEvent.Pre/Post` |
| `BlockEvent.CreateFluidSourceEvent` | `level.block.CreateFluidSourceEvent` |

**抽象基类只订阅具体子类**：`GrindstoneEvent`（`OnPlaceItem`/`OnTakeItem`）、
`LivingSwapItemsEvent`（`Hands`）——NeoForge 拒绝注册抽象事件。

**上游已删除、无直接等价物（10，不再转发）**：
`AttachCapabilitiesEvent`、`FillBucketEvent`、`LivingHurtEvent`（并入 `LivingIncomingDamageEvent`）、
`LivingMakeBrainEvent`、`LivingPackSizeEvent`、`LootingLevelEvent`、`PlayerEvent.ItemPickupEvent`、
`PlayerSleepInBedEvent`、`PotionColorCalculationEvent`、`ZombieEvent.SummonAidEvent`。

> 事件表以 NeoForge `21.1.235-universal.jar` 实际类表为准；抽象事件经 `javap` 逐个核对。

---

## 5. Mixin 状态（暂缓）

- 静态：`rosetta_remote_debug_bridge.mixins.json` 当前 `mixins: []`；不再使用 MixinGradle 接线。
- 动态：运行时日志提示 `Dynamic Mixin pipeline unavailable`（与 1.20.1 现状一致）。
- 相关类与 `libs/mixin-0.8.5-dev.jar`、`org.spongepowered.rain.asm` 保留，未接入 NeoForge Mixin 服务；
  后续需针对 NeoForge 的 ModLauncher/Mixin 服务单独移植。

---

## 6. 验证记录（本机 NeoForge 21.1.235 / Java 21.0.8）

| 验证 | 结果 |
|---|---|
| `compileJava` / `jar` / `build` | BUILD SUCCESSFUL |
| 产物元数据 | `META-INF/neoforge.mods.toml`（无旧 `mods.toml`），`RosettaPayload` 存在 |
| 专用服启动 | `Done (2.802s)!`；startup/server 脚本编译执行成功 |
| 远程桥 | `ping` 返回 `{"ok":true,...,"java":"21.0.8","running":true}` |
| 集成客户端 | `runClient` 进世界正常 |
| 脚本引擎 | `exec` 读环境 14ms；写入 `diamonds 0→5` 2ms |
| 热重载 | `console java reload server/client` 成功（注销→重编译→重执行） |
| 事件桥 | 脚本订阅 `ServerTickEvent.Pre` 收到 count 1/41/81/121；`ClientTickEvent.Pre` 1/41/81 |
| CRD | `selftest cryptoRoundtrip=true`；会话建立（READ）；`collect_info`/`tail_log` 加密返回成功；`resourcePacks` 含 `rosetta_assets` |

---

## 7. 已知限制 / 后续

- 不处理 10 个被删事件；如需可用 NeoForge 新机制补回（capabilities、`UseItemOnBlockEvent` 等）。
- CRD 客户端能力在登录 hello 时缓存；改客户端配置需重新 hello（重连/重进世界）。
- `McpToSrgTransformer` 在 1.21.1 恒为惰性（运行期官方名），保留仅作兼容。
- dev 环境日志中的 `Missing data pack mod:forge`、`Found 0 Minecraft/Forge core jars` 等为旧世界残留 /
  dev classpath 特征，属无害告警。
