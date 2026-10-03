#!/usr/bin/env python3
"""Prove that the current process tree has no usable network before an offline replay runs.

CI runs this in the same network namespace as the replay, immediately before it. Any reachable
network makes the replay's "no network evidence" claim unproven, so this exits non-zero.
"""
from __future__ import annotations

import socket
import sys

# Public addresses as literals so a missing resolver cannot make an open network look isolated.
PROBE_ADDRESSES = (("1.1.1.1", 443), ("8.8.8.8", 53), ("140.82.112.3", 443))
PROBE_HOSTNAME = "github.com"


def problems(timeout: float = 3.0) -> list[str]:
    found = []
    interfaces = sorted(name for _, name in socket.if_nameindex())
    if interfaces != ["lo"]:
        found.append(f"network interfaces other than loopback are present: {interfaces}")
    for host, port in PROBE_ADDRESSES:
        try:
            socket.create_connection((host, port), timeout=timeout).close()
        except OSError:
            continue
        found.append(f"connected to {host}:{port}")
    try:
        socket.getaddrinfo(PROBE_HOSTNAME, 443)
    except OSError:
        pass
    else:
        found.append(f"resolved {PROBE_HOSTNAME}")
    return found


def main() -> int:
    found = problems()
    if found:
        print("network isolation NOT established:\n- " + "\n- ".join(found), file=sys.stderr)
        return 1
    print("network isolation verified: loopback only, no outbound connection or name resolution")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
