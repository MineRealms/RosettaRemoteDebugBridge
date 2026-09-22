# 测试与验收

> 适用版本:`rosetta_remote_debug_bridge-1.0.0`
> 相关文档:[README](../README.md) · [使用手册](USAGE.md) · [技术手册](ARCHITECTURE.md)

测试分两层:**Forge dev 无人值守回归**(每次改动可复现)与 **Mohist 实机验收**(真实服务端行为)。

---

## 1. Forge dev 无人值守回归

### 1.1 组成

| 文件 | 作用 |
|---|---|
| `autotest/server/AutoTest.java` | 驱动器:等待服务器就绪 → 执行命令 → 断言 → 写结果文件 → 自动退出客户端 |
| `autotest/server/Listener.java` | `@RosettaEventSubscriber` 监听器(验证热重载不重复注册) |
| `autotest/server/McImportTest.java` | 直接 `import net.minecraft.world.item.ItemStack`(验证脚本编译 classpath 与命名转换) |
| `build.gradle` 的 `-PquickPlay` | 为 runClient 注入 `--quickPlaySingleplayer <world>` |

### 1.2 覆盖点

- 脚本编译与执行(`startup`/`server`);
- MC 导入脚本可编译(`errors_before=0`);
- 网络通道自动初始化(`networkutils=OK`);
- 运行期异常进入错误收集器(`errors_during_temp_throw=1`、`temp_throw_detected=1`);
- 热重载按 ClassLoader 清理监听器(重载前后 `listeners=1`);
- `/java errors`、`/java reload startup`、`/java hand getId` 命令;
- 客户端自动进出、世界正常保存。

### 1.3 运行

```powershell
# 1. 生成测试世界(首次或需要重置时)
Remove-Item run\world -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item run\saves\autotest -Recurse -Force -ErrorAction SilentlyContinue
.\gradlew.bat runServer --nogui          # 等待 "Done"，然后停服
Copy-Item run\world run\saves\autotest -Recurse -Force
Remove-Item run\saves\autotest\session.lock -Force

# 2. 部署测试脚本
Copy-Item autotest\server\*.java run\RosettaRemoteDebugBridge\server\ -Force

# 3. 无人值守运行(自动进世界、执行断言、自动退出)
.\gradlew.bat runClient -PquickPlay=autotest
```

结果写入 `run/rosetta-autotest-result.txt`。

### 1.4 最近结果

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

---

## 2. Mohist 实机验收

### 2.1 环境

- Mohist 1.20.1(Forge 47.x + Bukkit API),工作目录 `run-fast/`;
- 插件:Coder(`2.5.0-mohist-java17`)、ForgeKit(1.0.1,热更新靶)、Mohist;
- 旧 `RosettaRemote.jar` 已移入 `plugins-disabled/`(避免与新桥抢 48790)。

### 2.2 验收矩阵

| 项目 | 方法 | 结果 |
|---|---|---|
| 服务端加载与桥监听 | 启动日志 + `netstat` 仅 `127.0.0.1:48790` | 通过 |
| 远程协议 | `ping` 返回 `bridge=rosetta-nexus ...`、token 校验、审计日志 | 通过 |
| 无 JDK 代码执行 | `exec` 用内嵌 ECJ 编译并返回结果(含 MCP→SRG 转换) | 通过 |
| 控制台 | `console` 执行命令并返回日志增量 | 通过 |
| Bukkit 适配 | 监听器/命令注册、owner 解析、自检监听器(取消 CREEPER 生成)计数 | 通过 |
| 监听器生命周期 | `listener status/cleanup/restore`;reload 时按 ClassLoader 清理 | 通过 |
| Coder 协作 | `coder list/api` 跨 `PluginClassLoader` 调用 CoderAPI(133 方法) | 通过 |
| 插件热更新 | `update` ForgeKit:卸载(含命令双键清理)→ 写盘 → 重载启用 | 通过 |
| 静态 Mixin | `ChickenAiStepProbeMixin` 注入 `Chicken.aiStep` 并自然触发(计数递增) | 通过 |
| 数据包注入 | `rosetta_data` 谓词 `execute if predicate ...` 返回预期标记 | 通过 |
| 脚本三阶段 | STARTUP/SERVER/CLIENT 装载与 `/java reload` 全量/单类型 | 通过 |
| 端口与退役 | 端口统一 48790;旧插件桥退役后无冲突;全量回归 | 通过 |

### 2.3 关键证据(摘要)

- 静态 Mixin:`Mixing ChickenAiStepProbeMixin ... into ...Chicken`,
  自然 tick 下探针计数递增;
- 数据包:谓词命中输出 `P4_PREDICATE_OK`;
- 热更新:卸载摘要 `disabled, listeners, commands:2, manager, loader-closed`,
  重载日志 `Forge runtime bus hooked ... [hot-update proof]`;
- 端口:`netstat` 仅 `127.0.0.1:48790`(旧插件曾监听 `0.0.0.0`)。

---

## 3. 发布前回归清单

- [ ] `.\gradlew.bat build` 通过,产物含 `MixinConfigs` 清单与 refmap;
- [ ] dev quickPlay 回归 `PASS=1`(§1);
- [ ] 服务端启动无 `Duplicate key mixin`、无 `unit.missing`、无未捕获异常;
- [ ] `ping` / `exec` / `console` / `plugins` 基本命令连通;
- [ ] `/java reload server` 后监听器计数稳定(无重复);
- [ ] 静态 Mixin 探针计数在进入世界后递增;
- [ ] 数据包谓词可被 `execute if predicate` 命中;
- [ ] token 文件按预期生成;调试结束的卸载/清凭据流程演练(可选)。

---

## 4. 未覆盖项与已知边界

| 项 | 状态 |
|---|---|
| 纯 Forge(无 Bukkit)服务端 | 代码审查(适配层零硬引用)+ 无 Bukkit 类路径独立探针通过;**未在真实纯 Forge 服实测** |
| 动态 Mixin / CoreMod | 依赖外部 agent;启动日志输出降级警告,脚本与事件不受影响 |
| `update` 热更新异常插件 | 可能出现半卸载状态(静态状态/线程/资源残留),失败时插件处于未启用 |
| 类替换管线 | 仅编译产物,运行期不应用(需 agent/class-transformer) |

---

## 5. 测试工具

| 工具 | 用途 |
|---|---|
| `legacy/tools/rosetta_remote.py` | Python 客户端(默认端口 48790,支持 `ROSETTA_REMOTE_HOST/PORT/TOKEN`) |
| `legacy/tools/dump_file_hashes.ps1` | 目录文件哈希对照(mods 跨机 diff) |
| `legacy/tools/find_module_conflict.ps1` | 排查 JPMS 重复模块提供者导致的 ModLauncher ResolutionException |

---

## 6. CRD(客户端远程调试)测试

### 6.1 测试模式配置

复制 `autotest/crd/client-debug.test.toml` 到 `<gamedir>/RosettaRemoteDebugBridge/client-debug.toml`:

```
allowRemoteDebug = true
requireConfirmPerSession = false
maxPermission = "SCRIPT"
sessionTimeoutMinutes = 30
```

- 该文件仅用于测试环境;**生产默认 `allowRemoteDebug=false`**(由 mod 自动生成);
- SCRIPT 的逐次确认在测试模式下同样保留(自动化只能验证"被 UI 阻塞")。

### 6.2 测试脚本

| 脚本 | 覆盖 |
|---|---|
| `autotest/crd/crd_e2e.py` | 16 项:握手 list、开/关会话、`collect_info`、`tail_log`、`resource_reload`、ACTION(screenshot/clear_chat)、白名单拒绝、SCRIPT 权限拒绝、会话清理、客户端审计 |
| `autotest/crd/crd_script_gate.py` | 5 项:SCRIPT 会话、`eval_client_script` 被确认屏阻塞(socket 超时)、挂起期间 ACTION 截图取证、`pendingRequests>=1`、关会话 |

运行(Mohist 服务端 + `gradlew runClient -PquickJoin=<host:port>` 客户端):

```
python autotest/crd/crd_e2e.py --token <TOKEN> --player Dev
python autotest/crd/crd_script_gate.py --token <TOKEN> --player Dev
```

### 6.3 最近结果

- `crd_e2e.py`:**16 passed / 0 failed**(1.2s);截图落盘 `run/screenshots/`,客户端审计
  `logs/Rosetta/client-debug.log` 有对应条目;
- `crd_script_gate.py`:**5 passed / 0 failed**(SCRIPT 调用被确认屏阻塞,`pendingRequests=1`);
- 加密自检:`CrdCrypto.selfTest()` 独立验证 → `cryptoRoundtrip=true`;
- 边界:SCRIPT 的"执行"必须由玩家点 Accept(设计如此),自动化只能验证到 UI 门。
