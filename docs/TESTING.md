# 测试与验收

> 测试代码与生产代码严格分离:测试脚本位于 `autotest/`(不参与构建,发布 jar 不含任何测试类)。
> 相关文档:[README](../README.md) · [使用手册](USAGE.md) · [技术手册](ARCHITECTURE.md) · [CRD 设计](CRD-DESIGN.md)

测试分三层:**Forge dev 无人值守回归**、**Mohist 实机验收清单**、**CRD 专项测试**。

---

## 1. Forge dev 无人值守回归

### 1.1 组成

| 文件 | 作用 |
|---|---|
| `autotest/server/AutoTest.java` | 驱动器:等待服务器就绪 → 执行命令 → 断言 → 写结果 → 自动退出客户端 |
| `autotest/server/Listener.java` | `@RosettaEventSubscriber` 监听器(验证热重载不重复注册) |
| `autotest/server/McImportTest.java` | 直接 `import net.minecraft.world.item.ItemStack`(验证脚本编译 classpath 与命名转换) |
| `build.gradle` 的 `-PquickPlay` | 为 runClient 注入 `--quickPlaySingleplayer <world>` |

### 1.2 覆盖点

- 脚本编译与执行(`startup`/`server`);
- MC 导入脚本可编译;
- 网络通道初始化;
- 运行期异常进入错误收集器;
- 热重载按 ClassLoader 清理监听器(数量稳定);
- `/java errors`、`/java reload startup`、`/java hand getId`;
- 客户端自动进出、世界正常保存。

### 1.3 运行

```powershell
# 1. 生成/重置测试世界
Remove-Item run\world -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item run\saves\autotest -Recurse -Force -ErrorAction SilentlyContinue
.\gradlew.bat runServer --nogui          # 等待 "Done"，然后停服
Copy-Item run\world run\saves\autotest -Recurse -Force
Remove-Item run\saves\autotest\session.lock -Force

# 2. 部署测试脚本
Copy-Item autotest\server\*.java run\RosettaRemoteDebugBridge\server\ -Force

# 3. 无人值守运行
.\gradlew.bat runClient -PquickPlay=autotest
```

### 1.4 结果判定

结果文件 `run/rosetta-autotest-result.txt`,末行 `PASS=1` 即通过。关键字段:

| 字段 | 期望 |
|---|---|
| `listeners_before` / `listeners_after` | 相等且 > 0(热重载无重复注册) |
| `errors_before` | 0(MC 导入脚本编译成功) |
| `networkutils` | `OK` |
| `errors_during_temp_throw` / `temp_throw_detected` | 1 / 1(运行期异常被收集) |
| `errors_after_cleanup` | 0 |
| `listener_reload_stable` | 1 |

---

## 2. Mohist 实机验收清单

| 项目 | 验证方式 |
|---|---|
| 服务端加载与桥监听 | 启动日志 + `netstat` 仅 `127.0.0.1:48790` |
| 远程协议 | `ping`、token 校验、审计日志 |
| 无 JDK 代码执行 | `exec` 编译并返回结果(含 MCP→SRG 转换) |
| 控制台 | `console` 执行命令并返回日志增量 |
| Bukkit 适配 | 监听器/命令注册、owner 解析 |
| 监听器生命周期 | `listener status/cleanup/restore`;reload 按 ClassLoader 清理 |
| Coder 协作 | `coder list/api` 跨 `PluginClassLoader` 调用 |
| 插件热更新 | `update`:卸载(含命令双键清理)→ 写盘 → 重载启用 |
| 静态 Mixin | 注入 `Chicken.aiStep` 并自然触发 |
| 数据包注入 | `rosetta_data` 谓词被 `execute if predicate` 命中 |
| 脚本三阶段 | STARTUP/SERVER/CLIENT 装载与 `/java reload` 全量/单类型 |
| 纯 Forge 路径 | 无 Bukkit 类路径下适配层惰性禁用、不崩溃 |
| 端口 | 默认 48790;与旧插件不同时监听 |

---

## 3. CRD(客户端远程调试)测试

### 3.1 测试模式配置

复制 `autotest/crd/client-debug.test.toml` 到 `<gamedir>/RosettaRemoteDebugBridge/client-debug.toml`:

```
allowRemoteDebug = true
requireConfirmPerSession = false
maxPermission = "SCRIPT"
sessionTimeoutMinutes = 30
```

- 该文件仅用于测试环境;**生产默认 `allowRemoteDebug=false`**(由 mod 自动生成);
- SCRIPT 的逐次确认在测试模式下同样保留。

### 3.2 测试脚本

| 脚本 | 覆盖 |
|---|---|
| `autotest/crd/crd_e2e.py` | 会话 list/open/close、`collect_info`、`tail_log`、`resource_reload`、ACTION(screenshot/clear_chat)、白名单拒绝、SCRIPT 权限拒绝、会话清理、客户端审计 |
| `autotest/crd/crd_script_gate.py` | SCRIPT 会话、`eval_client_script` 被确认屏阻塞(socket 超时)、挂起期间 ACTION 截图取证、`pendingRequests>=1`、关会话 |

### 3.3 运行

```powershell
# 服务端(Mohist)+ 客户端(gradlew runClient -PquickJoin=<host:port>)均需运行
python autotest/crd/crd_e2e.py --token <TOKEN> --player Dev
python autotest/crd/crd_script_gate.py --token <TOKEN> --player Dev
```

---

## 4. 发布前回归清单

- [ ] `.\gradlew.bat build` 通过,产物含 `MixinConfigs` 清单与 refmap;
- [ ] 发布 jar 不含测试/探针类(检查条目,`autotest/` 不参与构建);
- [ ] dev quickPlay 回归 `PASS=1`(§1);
- [ ] 服务端启动无 `Duplicate key mixin`、无 `unit.missing`、无未捕获异常;
- [ ] `ping` / `exec` / `console` / `plugins` 基本命令连通;
- [ ] `/java reload server` 后监听器计数稳定;
- [ ] 静态 Mixin 探针计数在进入世界后递增;
- [ ] 数据包谓词可被 `execute if predicate` 命中;
- [ ] CRD:测试模式配置下 `clientdebug list` 可见客户端;关闭配置时显示未授权。

---

## 5. 覆盖边界

- **纯 Forge(无 Bukkit)服务端**:适配层代码零硬引用、无 Bukkit 类路径下惰性禁用;未在真实纯 Forge 服实测;
- **动态 Mixin / CoreMod**:依赖外部 agent;不可用时自动降级,脚本与事件不受影响;
- **CRD SCRIPT 执行**:必须由玩家在确认屏点 Accept(设计如此),自动化验证到 UI 门为止;
- **插件热更新**:异常插件可能出现半卸载状态(静态状态/线程/资源残留),失败时插件处于未启用。

---

## 6. 测试工具

| 工具 | 用途 |
|---|---|
| `legacy/tools/rosetta_remote.py` | Python 桥客户端(默认端口 48790,支持 `ROSETTA_REMOTE_HOST/PORT/TOKEN`) |
| `legacy/tools/dump_file_hashes.ps1` | 目录文件哈希对照(mods 跨机 diff) |
| `legacy/tools/find_module_conflict.ps1` | 排查 JPMS 重复模块提供者导致的 ModLauncher ResolutionException |
