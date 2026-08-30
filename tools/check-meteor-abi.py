#!/usr/bin/env python3
"""Fail the build if the addon still calls Meteor APIs that crash on 26.1.2-22."""
from __future__ import annotations

import sys
import zipfile
from pathlib import Path

FORBIDDEN = (
    b"getClientboundPackets",
    b"getServerboundPackets",
    b"getS2CPackets",
    b"getC2SPackets",
)

# Yarn leftovers that already crashed 26.1.1 once.
FORBIDDEN_YARN = (
    b"net/minecraft/util/math/BlockPos",
    b"net/minecraft/util/Formatting",
    b"net/minecraft/text/Text;",
)


def scan_jar(jar: Path) -> list[str]:
    hits: list[str] = []
    with zipfile.ZipFile(jar) as zf:
        for name in zf.namelist():
            if not name.endswith(".class"):
                continue
            data = zf.read(name)
            for needle in FORBIDDEN + FORBIDDEN_YARN:
                if needle in data:
                    hits.append(f"{name}: {needle.decode('ascii')}")
    return hits


def main() -> int:
    libs = Path("build/libs")
    jars = sorted(libs.glob("THM-Addons-*.jar"))
    if not jars:
        print("No THM-Addons jars in build/libs", file=sys.stderr)
        return 1
    failed = False
    for jar in jars:
        hits = scan_jar(jar)
        print(f"ABI scan {jar.name}: {'OK' if not hits else 'FAIL'}")
        for hit in hits:
            print(f"  {hit}", file=sys.stderr)
            failed = True
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
