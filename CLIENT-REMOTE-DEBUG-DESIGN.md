# 客户端远程调试（Client Remote Debug）设计文档

> **实现状态:C0–C4 已落地**。代码分层见 `docs/ARCHITECTURE.md` §6.9,使用说明见 `docs/USAGE.md` §3.14。
> 测试与生产分离:发布 jar 不含任何测试/探针类;无人值守脚本在 `autotest/`(探针归档 `autotest/probe/`)。
> 生产自检:`clientdebug selftest`(加密往返)。

> 目标：服务端与客户端都安装本 mod 时，玩家**显式授权**后，服务器可通过加密通道对客户端执行**受限**远程调试：
> 资源包下发/重载（F3+T 等价）、客户端信息采集、受控动作；为后续更丰富的双向能力打底。
> 定位：玩家自愿开启的调试代理，不是隐藏后门。**默认关闭、显式授权、全量审计、随时断开**。

---

## 1. 能力清单（按权限分级）

| 级别 | 能力 | 说明 |
|---|---|---|
| READ（默认允许等级） | `collect_info` | mods 列表、资源包列表、FPS/内存、游戏版本、日志尾部 |
| READ | `tail_log` | 客户端 `logs/latest.log` 增量 |
| RELOAD | `resource_reload` | 等价 F3+T：`Minecraft.reloadResourcePacks()` |
| RELOAD | `push_resource_pack` | 服务器下发资源包 URL + SHA1，客户端加载并启用（复用原版 resource pack 机制） |
| ACTION | `run_client_action` | 白名单动作：截图、打开调试屏、切换语言、强制重连等 |
| SCRIPT（默认禁用） | `eval_client_script` | 客户端侧 ECJ 编译执行（需 SCRIPT 权限 + 每次二次确认） |

明确不做：静默连接、无授权执行、持久化驻留、绕过玩家确认、键盘/剪贴板窃取等。

---

## 2. 授权模型

```
客户端 config: client-remote-debug.toml
  allowRemoteDebug = false            # 总开关，默认关闭
  requireConfirmPerSession = true     # 每次新会话弹窗确认（推荐）
  allowedServers = []                 # 服务器指纹/域名白名单（空=仅手动确认的会话）
  maxPermission = "RELOAD"            # READ / RELOAD / ACTION / SCRIPT
  sessionTimeoutMinutes = 30
```

- 服务器尝试开启会话时：客户端收到 `session_request`（含服务器名、公钥指纹、请求权限）。
- `allowRemoteDebug=false` → 直接拒绝，服务端日志可见"客户端未授权"。
- `requireConfirmPerSession=true` → 弹出确认界面（显示服务器/权限/超时），玩家点同意才建立会话。
- 会话期间 HUD 显示常驻指示（左上角盾牌图标 + 聊天提示），`/crd disconnect` 一键断开。
- 所有指令双端审计：客户端写 `logs/Rosetta/client-debug.log`（时间、来源、指令、结果）。

---

## 3. 加密与身份

- **传输现状**：正版服务器有原版加密；离线模式（本服 online-mode=false）连接是明文，因此必须在应用层加密。
- **方案（应用层 E2E）**：
  1. 服务器启动生成/加载长期身份密钥对（EC P-256，持久化到 `RosettaRemoteDebugBridge/server-identity.key`，公钥指纹可展示给玩家）。
  2. 会话建立时双方 ECDH 交换临时公钥（`session_hello` / `session_accept`），HKDF 派生 AES-256-GCM 会话密钥。
  3. 之后所有 payload 用 AES-GCM 加密（每次消息随机 nonce，序列号防重放）。
  4. 客户端缓存服务器公钥指纹（TOFU），变更时警告并要求重新确认。
- **通道**：复用 Forge `SimpleChannel`（`rosetta_remote_debug_bridge:main`，版本已兼容缺失端）；客户端在登录后发送 `capability_hello`（是否具备客户端调试模块、版本），服务端仅对具备能力的客户端开启会话。
- **权限令牌**：会话建立时服务端签发一次性 `session_token`（内存态），所有信令必须携带；超时/断开即失效。

---

## 4. 组件与数据流

```
服务器侧                                     客户端侧
┌───────────────────────┐                   ┌────────────────────────┐
│ Bridge 命令           │                   │ ClientDebugAgent        │
│  clientdebug ...      │                   │  ├─ 授权检查(config)     │
│  ↓                    │                   │  ├─ 会话确认 UI          │
│ ClientSessionManager  │  Forge Channel    │  ├─ ECDH/AES-GCM        │
│  ├─ ECDH/密钥         │ ⇄  (加密 payload) │  ├─ 能力执行器           │
│  ├─ 审计日志           │                   │  │   ├─ resource_reload  │
│  └─ 权限校验           │                   │  │   ├─ push pack       │
└───────────────────────┘                   │  │   ├─ collect_info     │
                                            │  │   └─ script(禁用默认) │
                                            │  └─ 审计日志            │
                                            └────────────────────────┘
```

服务器侧命令示例（走 Nexus 桥，天然远程可用）：
```
clientdebug list                          # 在线且具备能力的客户端
clientdebug session <player> open RELOAD  # 发起会话（客户端弹确认）
clientdebug <player> exec resource_reload # 加密下发动作
clientdebug <player> collect mods,packs,fps
clientdebug <player> pushpack https://.../pack.zip <sha1>
clientdebug <player> close
```

---

## 5. 安全设计清单（硬要求）

1. 默认关闭；无授权不可连接（服务端侧不重试、不静默）。
2. 会话需玩家确认（可配置为"信任本服务器"免弹窗，但首次必须确认）。
3. 端到端加密（AES-GCM）+ 服务器公钥指纹可见/可核对。
4. 权限分级 + 会话级上限；SCRIPT 级默认禁用并需逐次确认。
5. 速率限制（消息/秒）与超时自动断开；断开后密钥作废。
6. 全量审计（双端本地日志 + 服务器控制台提示）。
7. 客户端随时一键断开；UI 常驻指示，无法隐蔽运行。
8. 不做：隐藏、持久化、绕过确认、键盘/剪贴板/文件批量窃取。

---

## 6. 实施路线

| 阶段 | 内容 | 验收（需真实客户端） |
|---|---|---|
| C0 | 服务端通道骨架 + 能力握手（`capability_hello`）+ `clientdebug list`；客户端空实现仅应答 | 装了 mod 的客户端能被识别；未装/未授权不影响进服 |
| C1 | READ 能力：collect_info / tail_log + 审计日志 | 远程读取客户端信息、日志 |
| C2 | RELOAD：resource_reload（F3+T）+ push_resource_pack | 远程触发资源重载、下发资源包并生效 |
| C3 | 授权与加密完善：确认 UI、ECDH/AES-GCM、白名单、会话超时 | 抓包确认 payload 全加密；拒绝未授权会话 |
| C4 | ACTION 白名单 + （可选）SCRIPT（默认禁用） | 白名单动作执行；SCRIPT 需二次确认 |

**测试要求**：本机起一个带 mod 的客户端（`gradlew runClient`）连到 run-fast 联调；无客户端环境时只能验证服务端编译/启动/握手超时路径。

---

## 7. 与现有资产的关系

- 复用：Nexus 的 SimpleChannel/协议版本策略（`acceptMissingOr`，未装 mod 客户端不受影响）、ECJ 编译链路（服务端）、桥命令体系与审计模式。
- 新增：客户端 source set（ForgeGradle 单 jar 双端）、`ClientDebugAgent`、确认 UI（1.20.1 Screen）、ECDH/AES 工具类。
- displayTest 保持 `IGNORE_ALL_VERSION`：未装 mod 的客户端照常进服，只是不参与远程调试。
