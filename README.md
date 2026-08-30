<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# THM Addons for Meteor Client

THM Addons is a Meteor Client addon focused on highway automation, travel utilities, PvP tooling, and quality-of-life HUD widgets for Minecraft 26.1 and 26.2.

## Highlights
- Highway automation and monitoring with dedicated HUD support.
- Utility modules for inventory management, rendering, AFK safety, and performance control.
- PvP-focused modules grouped under a dedicated THM PVP category.
- Optional integrations for Discord webhooks and Rich Presence.
- [More Features](FEATURES.md)

## Requirements
| Minecraft | Meteor Client | Fabric Loader | Java |
| --- | --- | --- | --- |
| `26.1.1` | `26.1.2-SNAPSHOT` | `0.19.3` | `25` |
| `26.1.2` | `26.1.2-SNAPSHOT` | `0.19.3` | `25` |
| `26.2` | `26.2-SNAPSHOT` | `0.19.3` | `25` |

Meteor 26.1.2 is the client used for every 26.1.x jar. There is no separate Meteor 26.1.1 artifact.

## Installation
1. Build the addon for your Minecraft version (see below) or obtain a prebuilt jar.
2. Place the jar in your Minecraft `mods` folder alongside Meteor Client.
3. Launch the game with Fabric.

## Building
Java 25 is required. Gradle downloads it via the Foojay toolchain if it is not installed.

```bash
./gradlew build -Pmc=26.1.1
./gradlew build -Pmc=26.1.2
./gradlew build -Pmc=26.2
```

Or build every target:

```bash
./tools/build-all-mc.sh
```

Jars are created in `build/libs` as `THM-Addons-<mod-version>+<mc>.jar`. Default `-Pmc` is `26.2`.

Copy `secrets.properties.example` to `secrets.properties` for live THM API URLs. Endpoint URLs stay encrypted at build time; the HTTP client is readable source and rejects SSRF, private-network, and oversized payloads.

## Features
A full module-by-module overview is available in `FEATURES.md`.

## Documentation
- `docs/highway-settings.md`
- `docs/highwaybuilder-stats-screenshot-simulation.md`
- `docs/hwymonitor-reconnect-simulation.md`

## Contributing
Issues and pull requests are welcome.

## License
Licensed under the GNU General Public License v3.0. See `LICENSE` for details.

## Credits
Thanks to Stainless and BepHax.
