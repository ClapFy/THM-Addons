#!/usr/bin/env bash
# This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
# Copyright (c) THM Addons contributors. Credit the devs, keep the link.
# By using this code you agree to the license terms and to keep your repo public.
set -euo pipefail
cd "$(dirname "$0")/.."
for mc in 26.1.1 26.1.2 26.2; do
  echo "===== Building Minecraft ${mc} ====="
  ./gradlew --no-daemon build -Pmc="$mc"
done
