# autotest — 测试套件

测试脚本**不参与构建**,发布 jar 不含任何测试类。

## 目录

| 路径 | 说明 |
|---|---|
| `server/` | Forge dev 无人值守回归脚本(quickPlay 单人世界) |
| `crd/` | CRD 客户端远程调试端到端脚本与测试模式配置模板 |

测试覆盖与验收清单见 [../docs/TESTING.md](../docs/TESTING.md)。

## 1. Forge dev 无人值守回归(server/)

```powershell
# 生成/重置测试世界
Remove-Item run\world -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item run\saves\autotest -Recurse -Force -ErrorAction SilentlyContinue
.\gradlew.bat runServer --nogui          # 等待启动完成后停服
Copy-Item run\world run\saves\autotest -Recurse -Force
Remove-Item run\saves\autotest\session.lock -Force

# 部署脚本并运行
Copy-Item autotest\server\*.java run\RosettaRemoteDebugBridge\server\ -Force
.\gradlew.bat runClient -PquickPlay=autotest
```

结果写入 `run/rosetta-autotest-result.txt`,末行 `PASS=1` 即通过。

## 2. CRD 端到端(crd/)

1. 复制测试模式配置:`autotest/crd/client-debug.test.toml` →
   `<gamedir>/RosettaRemoteDebugBridge/client-debug.toml`;
2. 启动服务端与客户端(客户端 `gradlew runClient -PquickJoin=<host:port>`);
3. 运行:

```powershell
python autotest/crd/crd_e2e.py --token <TOKEN> --player <PLAYER>
python autotest/crd/crd_script_gate.py --token <TOKEN> --player <PLAYER>
```

`crd_e2e.py` 覆盖会话与 READ/RELOAD/ACTION 全链路;`crd_script_gate.py` 验证 SCRIPT 被客户端确认屏阻塞。
