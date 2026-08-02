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
#   scripts/preview-shader.sh                        pick from a numbered menu
#   scripts/preview-shader.sh <name>                 interactive window (Esc to quit)
#   scripts/preview-shader.sh <name> --shot out.png   single headless screenshot
#   scripts/preview-shader.sh <name> --shot out.png --time 12   at a specific time
#   scripts/preview-shader.sh <name> --bench 120 --width 960 --height 540   ms/frame
#
# --bench times N draws inside one process. Timing repeated --shot runs instead measures
# context creation and pixel readback, not the shader - see CLAUDE.md.
#
# Omitting <name> works with the flags too: `preview-shader.sh --shot out.png`
# asks which shader, then screenshots it.
#
# Requires: gcc, glfw3, glew (dev packages), and ImageMagick for --shot.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

shaders_dir="src/main/resources/assets/thm-addon/shaders"

# No name given (or the first arg is a flag)? Offer a numbered menu. `select` is
# bash's own builtin for exactly this, so no fzf/dialog/whiptail dependency.
if [ $# -eq 0 ] || [ "${1#-}" != "$1" ]; then
    choices=("$shaders_dir"/*.fsh)
    choices=("${choices[@]##*/}")
    choices=("${choices[@]%.fsh}")

    PS3=$'\nshader number (Ctrl-C to quit): '
    select name in "${choices[@]}"; do
        [ -n "${name:-}" ] && break
        echo "not one of the listed numbers" >&2
    done
    # select falls out of the loop on EOF (e.g. stdin isn't a terminal).
    [ -n "${name:-}" ] || { echo "no shader chosen" >&2; exit 1; }
else
    name="$1"
    shift
fi

shader="$shaders_dir/${name}.fsh"
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
    # ImageMagick 7 renamed `convert` to `magick` and warns on every use of the old name.
    if command -v magick >/dev/null; then magick "${png_out%.png}.ppm" "$png_out"
    else convert "${png_out%.png}.ppm" "$png_out"; fi
    rm -f "${png_out%.png}.ppm"
    echo "wrote $png_out"
fi
