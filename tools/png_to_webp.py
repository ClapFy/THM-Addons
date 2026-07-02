#!/usr/bin/env python3
"""Converts every .png under a resource root to lossless WebP, verifying a byte-exact
pixel match (including fully-transparent pixels, via WebP's `exact` encode mode) before
deleting the original. Skips any path referenced as a Fabric mod icon (fabric.mod.json's
"icon" field), since Fabric Loader loads that via plain ImageIO and can't read WebP.

Usage:
    python3 tools/png_to_webp.py [--root src/main/resources] [--dry-run] [--keep-originals]

Requires: Pillow with WebP support (pip install pillow).
"""
import argparse
import json
import sys
from pathlib import Path

from PIL import Image


def fabric_icon_paths(resources_root: Path) -> set[Path]:
    mod_json = resources_root / "fabric.mod.json"
    if not mod_json.exists():
        return set()
    data = json.loads(mod_json.read_text(encoding="utf-8"))
    icon = data.get("icon")
    if icon is None:
        return set()
    values = icon.values() if isinstance(icon, dict) else [icon]
    return {(resources_root / v).resolve() for v in values}


def convert_one(png_path: Path, dry_run: bool, keep_original: bool) -> tuple[bool, str]:
    webp_path = png_path.with_suffix(".webp")
    if webp_path.exists():
        return False, f"skip (already exists): {webp_path}"

    original = Image.open(png_path).convert("RGBA")
    original_pixels = list(original.getdata())

    if dry_run:
        return True, f"would convert: {png_path}"

    original.save(webp_path, "WEBP", lossless=True, exact=True, method=6)

    reloaded = Image.open(webp_path).convert("RGBA")
    if list(reloaded.getdata()) != original_pixels:
        webp_path.unlink()
        return False, f"FAILED (pixel mismatch, kept .png): {png_path}"

    before = png_path.stat().st_size
    after = webp_path.stat().st_size
    if not keep_original:
        png_path.unlink()
    pct = 100 * (before - after) / before if before else 0
    return True, f"OK  {png_path.name} -> {webp_path.name}  ({before}B -> {after}B, -{pct:.0f}%)"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", default="src/main/resources", help="resource root to scan (default: src/main/resources)")
    parser.add_argument("--dry-run", action="store_true", help="report what would happen without writing/deleting anything")
    parser.add_argument("--keep-originals", action="store_true", help="convert but don't delete the source .png")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    if not root.is_dir():
        print(f"root not found: {root}", file=sys.stderr)
        return 1

    excluded = fabric_icon_paths(root)
    all_pngs = sorted(root.rglob("*.png"))
    for p in all_pngs:
        if p.resolve() in excluded:
            print(f"skip (fabric.mod.json icon): {p}")

    pngs = [p for p in all_pngs if p.resolve() not in excluded]
    if not pngs:
        print("no convertible .png files found.")
        return 0

    failures = 0
    for png_path in pngs:
        ok, message = convert_one(png_path, args.dry_run, args.keep_originals)
        print(message)
        if not ok and "skip" not in message:
            failures += 1

    if not args.dry_run and failures == 0 and pngs:
        print("\nDone. Since asset filenames changed extension, grep your Java sources for the old")
        print("'.png' identifiers (e.g. Identifier.of(...)) and update them to '.webp'.")

    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
