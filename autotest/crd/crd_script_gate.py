"""CRD SCRIPT confirmation-gate test (test-side artifact; never shipped).

Verifies that eval_client_script is BLOCKED by the client confirmation screen:
  1. open session with SCRIPT permission
  2. fire eval_client_script with a short socket timeout -> must time out (UI gate)
  3. while it is pending, request an ACTION screenshot (captures the confirm screen)
  4. clientdebug list must show pendingRequests >= 1
  5. close session

Usage: python autotest/crd/crd_script_gate.py --token <TOKEN> [--player Dev]
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

    def call(cmd, payload=None, timeout=30.0):
        return rr.call(args.host, args.port, args.token, cmd, payload or {}, timeout=timeout)

    opened = call("clientdebug", {"action": "session", "player": args.player,
                                  "operation": "open", "permission": "SCRIPT"})
    check("session.open.SCRIPT", opened.get("permission") == "SCRIPT", opened)

    blocked = False
    error_text = ""
    code = ("package rosetta.client; public class GateProbe {"
            " public static void init() { System.out.println(\"GATE-PROBE\"); } }")
    try:
        call("clientdebug", {"action": "op", "player": args.player, "op": "eval_client_script",
                             "args": {"source": code}}, timeout=4.0)
    except Exception as exc:
        blocked = "timed out" in str(exc).lower() or "timeout" in str(exc).lower()
        error_text = str(exc)[:200]
    check("script.blocked_by_confirm", blocked, error_text or "call returned (unexpected)")

    shot = call("clientdebug", {"action": "op", "player": args.player, "op": "run_client_action",
                                "args": {"action": "screenshot"}})
    time.sleep(1)
    shots = sorted((REPO / "run" / "screenshots").glob("*.png"), key=lambda p: p.stat().st_mtime)
    fresh = shots and (time.time() - shots[-1].stat().st_mtime) < 60
    check("screenshot.of_confirm_screen", bool(fresh),
          {"bridge": shot, "file": str(shots[-1]) if shots else None})

    listed = call("clientdebug", {"action": "list"})
    entry = {c["name"]: c for c in listed.get("clients", [])}.get(args.player, {})
    pending = entry.get("session", {}).get("pendingRequests", 0)
    check("session.pending_request", pending and pending >= 1, entry.get("session"))

    closed = call("clientdebug", {"action": "session", "player": args.player, "operation": "close"})
    check("session.close", bool(closed.get("closed")), closed)

    print("\n== summary: %d passed, %d failed ==" % (len(PASS), len(FAIL)))
    return 1 if FAIL else 0


if __name__ == "__main__":
    raise SystemExit(main())
