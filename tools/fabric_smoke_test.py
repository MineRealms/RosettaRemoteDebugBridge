#!/usr/bin/env python3
"""Smoke test for the Fabric build of RosettaRemoteDebugBridge.

Usage:
    python tools/fabric_smoke_test.py [--host 127.0.0.1] [--port 48790] [--token TOKEN]
                                      [--token-file run-server/RosettaRemoteDebugBridge/remote-token.txt]

Exit code 0 when every assertion passes, 1 otherwise.
"""
import argparse
import json
import socket
import sys


def call(host, port, token, cmd, args=None, timeout=60):
    with socket.create_connection((host, port), timeout=timeout) as sock:
        sock.sendall((json.dumps({"token": token, "cmd": cmd, "args": args or {}}) + "\n").encode())
        data = b""
        while not data.endswith(b"\n"):
            chunk = sock.recv(65536)
            if not chunk:
                break
            data += chunk
    return json.loads(data.decode("utf-8", "replace"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=48790)
    parser.add_argument("--token")
    parser.add_argument("--token-file")
    options = parser.parse_args()

    token = options.token
    if not token:
        if not options.token_file:
            parser.error("either --token or --token-file is required")
        with open(options.token_file, "r", encoding="utf-8") as handle:
            token = handle.read().strip()

    failures = []

    def check(name, response, predicate):
        if response.get("ok") and predicate(response.get("result")):
            print("PASS", name)
        else:
            failures.append(name)
            print("FAIL", name, json.dumps(response, ensure_ascii=False)[:400])

    ping = call(options.host, options.port, token, "ping")
    check("ping", ping, lambda r: r.get("running") is True and r.get("loader") == "fabric")

    version = call(options.host, options.port, token, "exec", {
        "code": "return net.minecraft.SharedConstants.getCurrentVersion().getName();"
    })
    check("exec/version", version, lambda r: "1.20.1" in r.get("result", ""))

    server = call(options.host, options.port, token, "exec", {
        "code": "return com.rosetta.remotedebugbridge.RosettaRemoteDebugBridge.getServer() != null;"
    })
    check("exec/server", server, lambda r: r.get("result", "").startswith("true"))

    listing = call(options.host, options.port, token, "console", {"command": "list"})
    check("console/list", listing, lambda r: r.get("dispatched") is True)

    if failures:
        print("FAILED:", ", ".join(failures))
        return 1
    print("ALL PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
