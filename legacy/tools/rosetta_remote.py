#!/usr/bin/env python3
"""Rosetta Remote Bridge client (talks to the RosettaRemote Bukkit plugin).

Examples:
  python rosetta_remote.py --host 1.2.3.4 --token SECRET ping
  python rosetta_remote.py --host 1.2.3.4 --token SECRET console "plugin list"
  python rosetta_remote.py --host 1.2.3.4 --token SECRET console "plugin load mymodule.jar"
  python rosetta_remote.py --host 1.2.3.4 --token SECRET exec script.java
  python rosetta_remote.py --host 1.2.3.4 --token SECRET eval "return Bukkit.getVersion();"
  python rosetta_remote.py --host 1.2.3.4 --token SECRET upload build/libs/M.jar plugins/M.jar
  python rosetta_remote.py --host 1.2.3.4 --token SECRET load plugins/M.jar
  python rosetta_remote.py --host 1.2.3.4 --token SECRET plugins
  python rosetta_remote.py --host 1.2.3.4 --token SECRET tail logs/latest.log 200
  python rosetta_remote.py --host 1.2.3.4 --token SECRET ls plugins
  python rosetta_remote.py --host 1.2.3.4 --token SECRET shell

Environment fallbacks: ROSETTA_REMOTE_HOST / ROSETTA_REMOTE_PORT / ROSETTA_REMOTE_TOKEN
"""
import argparse
import base64
import json
import os
import socket
import sys

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except Exception:  # noqa: BLE001
    pass

DEFAULT_PORT = 48790


def call(host, port, token, cmd, args=None, timeout=180.0):
    request = json.dumps({"token": token, "cmd": cmd, "args": args or {}})
    with socket.create_connection((host, port), timeout=min(timeout, 30.0)) as sock:
        sock.settimeout(timeout)
        sock.sendall((request + "\n").encode("utf-8"))
        buffer = b""
        while not buffer.endswith(b"\n"):
            chunk = sock.recv(65536)
            if not chunk:
                break
            buffer += chunk
    if not buffer.strip():
        raise RuntimeError("empty response from server")
    response = json.loads(buffer.decode("utf-8", "replace"))
    if not response.get("ok"):
        raise RuntimeError(response.get("error") or response)
    return response.get("result")


def print_result(result):
    if isinstance(result, dict) and "text" in result:
        print(result["text"])
    elif isinstance(result, dict) and "output" in result:
        print(json.dumps({k: v for k, v in result.items() if k != "output"}, ensure_ascii=False))
        if result.get("output"):
            print(result["output"])
    else:
        print(json.dumps(result, ensure_ascii=False, indent=2))


def run_once(session, argv):
    cmd = argv[0]
    rest = argv[1:]
    if cmd == "ping":
        print_result(call(**session, cmd="ping"))
    elif cmd == "console":
        print_result(call(**session, cmd="console", args={"command": " ".join(rest)}))
    elif cmd == "eval":
        print_result(call(**session, cmd="exec", args={"code": " ".join(rest)}))
    elif cmd == "exec":
        with open(rest[0], "r", encoding="utf-8") as handle:
            code = handle.read()
        print_result(call(**session, cmd="exec", args={"code": code}))
    elif cmd == "upload":
        local, remote = rest[0], (rest[1] if len(rest) > 1 else "plugins/" + os.path.basename(rest[0]))
        with open(local, "rb") as handle:
            data = base64.b64encode(handle.read()).decode("ascii")
        print_result(call(**session, cmd="upload", args={"path": remote.replace("\\", "/"), "base64": data}, timeout=600.0))
    elif cmd == "load":
        print_result(call(**session, cmd="load", args={"file": rest[0].replace("\\", "/")}))
    elif cmd == "update":
        # update <pluginName> [remoteJar] <localJar>
        if len(rest) < 2:
            raise SystemExit("usage: update <pluginName> [remoteJar] <localJar>")
        if len(rest) == 2:
            name, remote, local = rest[0], "plugins/" + os.path.basename(rest[1]), rest[1]
        else:
            name, remote, local = rest[0], rest[1], rest[2]
        with open(local, "rb") as handle:
            data = base64.b64encode(handle.read()).decode("ascii")
        print_result(call(**session, cmd="update",
                          args={"name": name, "path": remote.replace("\\", "/"), "base64": data}, timeout=600.0))
    elif cmd in ("enable", "disable"):
        print_result(call(**session, cmd=cmd, args={"name": rest[0]}))
    elif cmd == "plugins":
        print_result(call(**session, cmd="plugins"))
    elif cmd == "ls":
        print_result(call(**session, cmd="ls", args={"dir": rest[0].replace("\\", "/")}))
    elif cmd == "tail":
        lines = int(rest[1]) if len(rest) > 1 else 200
        print_result(call(**session, cmd="tail", args={"file": rest[0].replace("\\", "/"), "lines": lines}))
    elif cmd == "read":
        offset = int(rest[1]) if len(rest) > 1 else 0
        maximum = int(rest[2]) if len(rest) > 2 else 65536
        result = call(**session, cmd="read", args={"file": rest[0].replace("\\", "/"), "offset": offset, "max": maximum})
        sys.stdout.write(base64.b64decode(result["base64"]).decode("utf-8", "replace"))
        sys.stdout.write("\n")
    elif cmd == "reflect":
        # reflect <class> <method> [target] [argsJson]
        payload = {"class": rest[0], "method": rest[1]}
        if len(rest) > 2 and rest[2] in ("server", "nms-server", "plugin", "none"):
            if rest[2] != "none":
                payload["target"] = rest[2]
            if len(rest) > 3:
                payload["args"] = json.loads(rest[3])
        elif len(rest) > 2:
            payload["args"] = json.loads(rest[2])
        print_result(call(**session, cmd="reflect", args=payload))
    elif cmd == "shell":
        interactive(session)
    else:
        raise SystemExit("unknown command: " + cmd + " (try: ping, console, exec, eval, upload, load, enable, disable, plugins, ls, tail, read, reflect, shell)")


def interactive(session):
    print("Rosetta remote shell. Type 'help' for commands, 'exit' to quit.")
    while True:
        try:
            line = input("remote> ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            return
        if not line:
            continue
        if line in ("exit", "quit"):
            return
        argv = line.split(" ")
        try:
            run_once(session, argv)
        except Exception as error:  # noqa: BLE001
            print("ERROR:", error)


def main():
    parser = argparse.ArgumentParser(description="Rosetta Remote Bridge client")
    parser.add_argument("--host", default=os.environ.get("ROSETTA_REMOTE_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("ROSETTA_REMOTE_PORT", DEFAULT_PORT)))
    parser.add_argument("--token", default=os.environ.get("ROSETTA_REMOTE_TOKEN", ""))
    parser.add_argument("command", nargs=argparse.REMAINDER)
    options = parser.parse_args()
    if not options.token:
        raise SystemExit("token required (--token or ROSETTA_REMOTE_TOKEN)")
    if not options.command:
        raise SystemExit("no command given (see --help)")
    session = {"host": options.host, "port": options.port, "token": options.token}
    try:
        run_once(session, options.command)
    except Exception as error:  # noqa: BLE001
        raise SystemExit("ERROR: " + str(error))


if __name__ == "__main__":
    main()
