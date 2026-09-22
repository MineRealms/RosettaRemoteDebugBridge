# P4 验证报告：静态 Mixin 接入 + 资源/数据包注入

> 日期：2026-09-22
> 被测产物：`build/libs/rosetta_remote_debug_bridge-1.0.0.jar`
> SHA256：`76E500C99FEB07878C0CC0C7926F643C51F6B2DAEDBBD0EE896560A38598D4B0`（构建产物与部署到 `run-fast/mods` 的 jar 哈希一致）
> 环境：`projects/mohist/run-fast`（Mohist 1.20.1-ecf8d5ad / Forge 47.4.13 / JDK 17）
> 新桥：`127.0.0.1:48791`（token 落盘 `run-fast/RosettaRemoteDebugBridge/remote-token.txt`）
> 结论：**3/3 验收项通过**（静态 Mixin apply + trigger、数据包 enabled + 内容实际可用、P1-P3 回归）；过程中发现并修复 1 个类加载缺陷（见 §3）

---

## 1. 本次改动

| 文件 | 改动 |
|---|---|
| `build.gradle` | 接入 MixinGradle `0.7.38`：`mixin { add sourceSets.main, 'rosetta_remote_debug_bridge.refmap.json'; config 'rosetta_remote_debug_bridge.mixins.json' }`；依赖 `annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'`。`jar` manifest 自动获得 `MixinConfigs` 属性 |
| `src/main/resources/rosetta_remote_debug_bridge.mixins.json`（新增） | 静态 Mixin 配置：`package=com.rosetta.remotedebugbridge.mixin.staticprobe`、`refmap`、`verbose:true`（用于验收日志可见） |
| `.../mixin/staticprobe/ChickenAiStepProbeMixin.java`（新增） | `@Mixin(Chicken.class)` + `@Inject(method="aiStep", at=@At("HEAD"))`，回调转调 helper |
| `.../mixin/StaticMixinProbe.java`（新增） | 回调实现：节流日志 + `callCount()`；位于**普通包** `com.rosetta.remotedebugbridge.mixin`（非 mixin 配置包，原因见 §3.2） |

构建（按约定命令）：

```
$ $env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot'
$ .\gradlew.bat build --console=plain
Note: SpongePowered MIXIN Annotation Processor Version=0.8.5 (MixinGradle Version=0.7.38)
...
> Task :jar
> Task :reobfJar
BUILD SUCCESSFUL in 37s
```

产物关键内容：

```
META-INF/MANIFEST.MF  ->  MixinConfigs: rosetta_remote_debug_bridge.mixins.json
rosetta_remote_debug_bridge.mixins.json
rosetta_remote_debug_bridge.refmap.json
com/rosetta/remotedebugbridge/mixin/staticprobe/ChickenAiStepProbeMixin.class
com/rosetta/remotedebugbridge/mixin/StaticMixinProbe.class
```

生成的 refmap（标准 FG 流程，AP 自动生成 official→SRG 映射）：

```json
{
  "mappings": {
    "com/rosetta/remotedebugbridge/mixin/staticprobe/ChickenAiStepProbeMixin": {
      "aiStep": "Lnet/minecraft/world/entity/animal/Chicken;m_8107_()V"
    }
  },
  "data": {
    "searge": {
      "com/rosetta/remotedebugbridge/mixin/staticprobe/ChickenAiStepProbeMixin": {
        "aiStep": "Lnet/minecraft/world/entity/animal/Chicken;m_8107_()V"
      }
    }
  }
}
```

目标选型：`net.minecraft.world.entity.animal.Chicken#aiStep`（Chicken 自身声明，SRG `m_8107_`）。已先核对服务端 `forge-1.20.1-47.4.13-universal.jar` 内 `rosetta.mixins.json` 的 16 个既有 Mixin（Cow/Pig/Goat/Animal/Ravager/IceBlock/CampfireBlock/EndCrystal/ItemEntity/MinecartItem/AbstractHurtingProjectile/Interaction/ContainerOpenersCounter + 3 个 dispense 行为），无 Chicken 目标，无注入点冲突。

---

## 2. 验收项与原始证据

控制通道：`python legacy/tools/rosetta_remote.py --host 127.0.0.1 --port 48791 --token <T> ...`

### 2.1 静态 Mixin 应用（console2.log 原始行）

```
[18:23:19 INFO]: Mixing ChickenAiStepProbeMixin from rosetta_remote_debug_bridge.mixins.json into net.minecraft.world.entity.animal.Chicken
```

运行时在已被转换的目标类中确认 handler 存在（桥 `exec`，脚本 `run-fast/RosettaRemoteDebugBridge/server/p4_mixin_probe.java`）：

```
Chicken handler methods=[handler$zza000$rosetta$probeAiStep] | loader=TransformingClassLoader | StaticMixinProbe loader OK=true | recorded aiStep calls=51
```

### 2.2 触发标记

(a) 确定性触发（桥 `exec` 原子 spawn + 反射调用 `m_8107_` + 实体清理，脚本 `.../server/p4_trigger.java`）：

```
[18:23:58 INFO]: [P4-STATIC-MIXIN] Chicken.aiStep (m_8107_) tick #1 entity=Chicken['鸡'/83, l='ServerLevel[fastworld]', x=0.00, y=-60.00, z=0.00] - static mixin is live
```

(b) 自然 ticking 触发（`console "execute positioned 0 -60 0 run summon minecraft:chicken ~ ~ ~"`，出生点区块内实体自然 tick）：

```
[18:24:04 INFO]: [P4-STATIC-MIXIN] Chicken.aiStep (m_8107_) tick #2 entity=Chicken['鸡'/84, ... x=0.50, y=-60.00, z=0.50] - static mixin is live
[18:24:04 INFO]: [P4-STATIC-MIXIN] Chicken.aiStep (m_8107_) tick #3 ...
[18:24:06 INFO]: [P4-STATIC-MIXIN] Chicken.aiStep (m_8107_) tick #19 ...
[18:24:09 INFO]: [P4-STATIC-MIXIN] Chicken.aiStep (m_8107_) tick #22 ...
...
[18:24:45 INFO]: [P4-STATIC-MIXIN] Chicken.aiStep (m_8107_) tick #57 ...
```

说明：无玩家在线时，实体仅在 entity-ticking 区块（世界出生点区块）tick；测试用鸡持续自然 tick 至主动清理。

### 2.3 资源/数据包注入

测试数据文件（运行目录，不进入仓库）：

```
run-fast/RosettaRemoteDebugBridge/data/rosetta_probe/predicates/always_true.json
{"condition":"minecraft:random_chance","chance":1.0}
```

（1.20.1 谓词目录为复数 `predicates`；资源包实现读取 `RosettaRemoteDebugBridge/data/<namespace>/...`）

注册与可见性（console2.log 原始行）：

```
[18:23:29 INFO]: Registering RosettaRemoteDebugBridge data pack
[18:23:29 INFO]: Found namespaces in RosettaRemoteDebugBridge data: [rosetta_probe]
...
> console "datapack list"
[18:24:18 INFO]: 已启用4个数据包：[vanilla（内置）], [mod:forge], [mod:rosetta_remote_debug_bridge], [rosetta_data]
[18:24:18 INFO]: 已无更多可用的数据包
```

内容实际可用性（不只是“被列出”，而是注册表真正加载了包内数据）：

```
> console "execute if predicate rosetta_probe:always_true run say P4_PREDICATE_OK"
[18:24:15 INFO]: [Server] P4_PREDICATE_OK
```

（若谓词未加载，命令返回“未知的谓词：rosetta_probe:always_true”，见 §3.1。）

---

## 3. 失败模式与修复（两次尝试记录）

1. **数据包内容未生效**：初次将文件放在 `data/rosetta_probe/predicate/`（单数）且 PowerShell `Set-Content -Encoding UTF8` 写入 BOM → 首次加载解析失败 + 目录名不匹配，`execute if predicate` 报 `未知的谓词：rosetta_probe:always_true`。修复：改用 `predicates/`（1.20.1 复数目录）+ 无 BOM UTF-8 重写文件。`/reload` 在 Mohist 不重载数据包（Bukkit reload 被拦截），需重启服务器后生效。
2. **静态 Mixin 注入代码运行时报 `NoClassDefFoundError`**：回调引用的 helper 最初与 mixin 类同包（`...mixin.staticprobe`）。异常：
   `java.lang.NoClassDefFoundError: com/rosetta/remotedebugbridge/mixin/staticprobe/StaticMixinProbe at ...Chicken.handler$zza000$rosetta$probeAiStep`
   根因：ModLauncher 将 Mixin 配置声明的 package 排除出常规类加载（同包内普通类 `Class.forName` 亦 CNFE），而 `Chicken` 的 `TransformingClassLoader` 需要从常规包解析被注入代码的外部引用。修复：helper 移至普通包 `com.rosetta.remotedebugbridge.mixin.StaticMixinProbe`，mixin 类保留在配置包。修复后 handler 正常执行（§2.2）。**标准 FG 流程（refmap + reobf）本身无需回退 `remap=false` 方案。**

---

## 4. P1-P3 回归（全部通过）

```
> ping
{"server":"127.0.0.1:48791","bridge":"rosetta-nexus rosetta_remote_debug_bridge","java":"17.0.18",
 "bukkit":true,"adapter":"present=true tracked=1 selfCheck=true selfCheckInstances=1 selfCheckCancels=0 owner=com.mohistmc.plugins.Main",
 "players":0,"plugins":4,"running":true}

> exec "return org.bukkit.Bukkit.getVersion();"
{"result":"1.20.1-ecf8d5ad (MC: 1.20.1)  [0ms]"}

> plugins
[{"name":"Coder","version":"2.5.0-mohist-java17","enabled":true},
 {"name":"RosettaRemote","version":"1.0.0","enabled":true},
 {"name":"ForgeKit","version":"1.0.1","enabled":true},
 {"name":"Mohist","version":"1.20.1","enabled":true}]

> coder {"action":"list"}
{"plugin":"Coder","version":"2.5.0-mohist-java17","apiClass":"dev.codestuff.coder.api.CoderAPI",
 "loader":"org.bukkit.plugin.java.PluginClassLoader@35c83ac0","methods":133,"instance":true}
```

---

## 5. 清理与服务器最终状态

- `kill @e[type=minecraft:chicken]` → `[18:24:46 INFO]: 杀死了鸡`；复查无鸡残留。
- `forceload remove 0 0` → `[18:24:47 INFO]: 已将minecraft:overworld中的区块[0, 0]解除强制加载`；复查 `forceload query` → `未找到强制加载的区块`。
- 服务器保持运行，48791 桥激活；mods 目录为本次产物（哈希一致）。
- 测试脚本仅存于测试服 `run-fast/RosettaRemoteDebugBridge/server/`（`p4_trigger.java`、`p4_mixin_probe.java`），不属于仓库内容。

---

## 6. 遗留 / 未验证项

- 仅在 **Mohist 生产（SRG）环境** 验证；未运行 FG dev/gameTest（refmap 已按标准流程生成，dev 侧理论可用但未实测）。
- `compatibilityLevel: JAVA_17` 会触发 Mixin 提示 `higher than the maximum level supported by this version of mixin (JAVA_13)`（既有服务端 Rosetta 配置同样如此），无功能影响，保留。
- `verbose: true` 保留用于“Mixing … into …”验收日志可见；如后续不需要可在配置中移除。
- `src/main/resources/pack.mcmeta` 描述仍为 `stellar_striker resources`（历史遗留，不在本次范围）。
- 未验证 assets（客户端资源）侧注入；本次只验证 `SERVER_DATA`。
- 未在清理后的运行中再次触发 Mixin（清理动作本身不影响 transform；handler 已加载，最后自然 tick 记录为 18:24:47 的 tick #60）。
