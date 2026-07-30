#!/usr/bin/env bash
#
# This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
# Copyright (c) THM Addons contributors. Credit the devs, keep the link.
# By using this code you agree to the license terms and to keep your repo public.
#

# Preview a THM main-menu background shader outside Minecraft, using a real
# desktop OpenGL 3.3 core context so the .fsh runs completely unmodified.
#
# Usage:
#   scripts/preview-shader.sh <name>                 interactive window (Esc to quit)
#   scripts/preview-shader.sh <name> --shot out.png   single headless screenshot
#   scripts/preview-shader.sh <name> --shot out.png --time 12   at a specific time
#
# Requires: gcc, glfw3, glew (dev packages), and ImageMagick's `convert` for --shot.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

name="${1:?usage: preview-shader.sh <name> [--shot out.png] [--time T]}"
shift || true
shader="src/main/resources/assets/thm-addon/shaders/${name}.fsh"
[ -f "$shader" ] || { echo "no such shader: $shader" >&2; exit 1; }

bin_dir="build/tools"
bin="$bin_dir/shader_preview"
mkdir -p "$bin_dir"
if [ ! -x "$bin" ] || [ tools/shader_preview.c -nt "$bin" ]; then
    echo "building preview harness..." >&2
    gcc -O2 -o "$bin" tools/shader_preview.c -lglfw -lGLEW -lGL
fi

# Translate a trailing --shot out.png into a .ppm the C harness writes, then
# convert with ImageMagick (no PNG writer in the harness itself).
args=("$shader")
png_out=""
while [ $# -gt 0 ]; do
    if [ "$1" = "--shot" ]; then
        png_out="$2"
        args+=(--shot "${png_out%.png}.ppm")
        shift 2
    else
        args+=("$1")
        shift
    fi
done

"$bin" "${args[@]}"

if [ -n "$png_out" ]; then
    convert "${png_out%.png}.ppm" "$png_out"
    rm -f "${png_out%.png}.ppm"
    echo "wrote $png_out"
fi
