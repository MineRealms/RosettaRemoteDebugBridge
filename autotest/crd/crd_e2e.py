"""CRD end-to-end bridge test (test-side artifact; never shipped).

Usage:
  python autotest/crd/crd_e2e.py --token <TOKEN> [--player Dev] [--host 127.0.0.1] [--port 48790]

Response shapes (bridge-level result field):
  session open/close -> session object / {closed:...}
  op                 -> {requestId, ok, result|error}
Covers: list -> session open (ACTION) -> collect_info -> tail_log -> resource_reload
        -> action screenshot/clear_chat -> whitelist negative -> SCRIPT denied
        -> session close -> session cleared.
"""
import argparse
import json
import pathlib
import sys
import time

REPO = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "legacy" / "tools"))
import rosetta_remote as rr  # noqa: E402

PASS = []
FAIL = []


def check(name, condition, detail):
    (PASS if condition else FAIL).append(name)
    print(("PASS " if condition else "FAIL ") + name + " :: " + json.dumps(detail, ensure_ascii=False)[:400])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=48790)
    parser.add_argument("--token", required=True)
    parser.add_argument("--player", default="Dev")
    args = parser.parse_args()
    started = time.time()

    def call(cmd, payload=None):
        return rr.call(args.host, args.port, args.token, cmd, payload or {})

    def op(name, op_args=None):
        return call("clientdebug", {"action": "op", "player": args.player, "op": name,
                                    "args": op_args or {}})

    def is_ok(response):
        return isinstance(response, dict) and response.get("ok") is True

    def payload(response):
        return response.get("result", {}) if isinstance(response, dict) else {}

    # 1. capability list
    listed = call("clientdebug", {"action": "list"})
    clients = {c["name"]: c for c in listed.get("clients", [])}
    entry = clients.get(args.player)
    check("list.has_player", entry is not None, listed)
    check("list.authorized", bool(entry and entry.get("authorized")), entry)

    # 2. open session with ACTION
    opened = call("clientdebug", {"action": "session", "player": args.player,
                                  "operation": "open", "permission": "ACTION"})
    check("session.open", bool(opened.get("sessionId")), opened)
    check("session.permission", opened.get("permission") == "ACTION", opened)
    check("session.client_grant", opened.get("grantedByClient") == "ACTION", opened)

    # 3. collect_info (READ)
    info = op("collect_info")
    inner = payload(info)
    check("op.collect_info", is_ok(info), info)
    check("op.collect_info.fields",
          all(k in inner for k in ("mcVersion", "fps", "memoryUsedMb", "modCount", "logTail")),
          {k: inner.get(k) for k in ("mcVersion", "fps", "modCount")})

    # 4. tail_log (READ)
    tail = op("tail_log", {"lines": 20})
    tail_text = payload(tail).get("text", "")
    check("op.tail_log", is_ok(tail) and len(tail_text) > 0, {"chars": len(tail_text)})

    # 5. resource_reload (RELOAD)
    reload_result = op("resource_reload")
    check("op.resource_reload", is_ok(reload_result), reload_result)

    # 6. ACTION screenshot (+ filesystem evidence on this machine)
    screenshot = op("run_client_action", {"action": "screenshot"})
    time.sleep(1)
    shots = sorted((REPO / "run" / "screenshots").glob("*.png"), key=lambda p: p.stat().st_mtime)
    fresh = shots and (time.time() - shots[-1].stat().st_mtime) < 120
    check("op.action.screenshot", is_ok(screenshot) and bool(fresh),
          {"bridge": screenshot, "fresh": bool(fresh), "file": str(shots[-1]) if shots else None})

    # 7. ACTION clear_chat
    clear = op("run_client_action", {"action": "clear_chat"})
    check("op.action.clear_chat", is_ok(clear), clear)

    # 8. negative: unknown action rejected by whitelist
    bad = op("run_client_action", {"action": "nope"})
    check("op.action.whitelist", isinstance(bad, dict) and bad.get("ok") is False, bad)

    # 9. negative: SCRIPT denied because session permission is ACTION (server-side check)
    try:
        script = op("eval_client_script", {"source": "package rosetta.client; public class T { public static void init() {} }"})
        check("op.script.denied", False, script)
    except Exception as exc:  # bridge returns ok:false -> python client raises
        check("op.script.denied", "requires SCRIPT" in str(exc) or "does not allow" in str(exc), str(exc)[:200])

    # 10. close session
    closed = call("clientdebug", {"action": "session", "player": args.player, "operation": "close"})
    check("session.close", bool(closed.get("closed")), closed)

    # 11. list no longer shows session
    after = call("clientdebug", {"action": "list"})
    after_entry = {c["name"]: c for c in after.get("clients", [])}.get(args.player, {})
    check("list.session_cleared", "session" not in after_entry, after_entry)

    # 12. client-side audit file has fresh entries
    audit = REPO / "run" / "logs" / "Rosetta" / "client-debug.log"
    if audit.exists():
        lines = audit.read_text(encoding="utf-8", errors="replace").splitlines()
        recent = [line for line in lines if "session ready" in line or "op received: collect_info" in line]
        check("client.audit", bool(recent), {"recent": len(recent), "total": len(lines)})
    else:
        check("client.audit", False, "missing " + str(audit))

    print("\n== summary: %d passed, %d failed (%.1fs) ==" % (len(PASS), len(FAIL), time.time() - started))
    return 1 if FAIL else 0


if __name__ == "__main__":
    raise SystemExit(main())
