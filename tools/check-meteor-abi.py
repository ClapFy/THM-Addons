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
            if name.endswith("WaveyCapesCapeMixin.class") and b"submit(" not in data:
                hits.append(f"{name}: CapeLayer inject must target submit(), not the old render name")
    return hits


def scan_mixin_sources() -> list[str]:
    cape = Path("src/main/java/xyz/thm/addon/mixin/WaveyCapesCapeMixin.java")
    if not cape.exists():
        return []
    text = cape.read_text()
    if 'method = "render"' in text:
        return [f"{cape}: leftover CapeLayer.render inject (26.x uses submit)"]
    return []


def main() -> int:
    libs = Path("build/libs")
    jars = sorted(libs.glob("THM-Addons-*.jar"))
    if not jars:
        print("No THM-Addons jars in build/libs", file=sys.stderr)
        return 1
    failed = False
    for hit in scan_mixin_sources():
        print(f"ABI scan source: FAIL")
        print(f"  {hit}", file=sys.stderr)
        failed = True
    for jar in jars:
        hits = scan_jar(jar)
        print(f"ABI scan {jar.name}: {'OK' if not hits else 'FAIL'}")
        for hit in hits:
            print(f"  {hit}", file=sys.stderr)
            failed = True
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
