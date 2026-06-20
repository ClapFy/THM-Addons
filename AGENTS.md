# AGENTS.md

## Project
- Name: THM Addons for Meteor Client
- Language: Java 21
- Platform: Fabric + Meteor Client (Minecraft 1.21.11)
- Build tool: Gradle (`./gradlew`)
- Main addon entry: `src/main/java/xyz/thm/addon/THMAddon.java`

## Goal
Maintain and extend THM Addons with stable module behavior, clean Meteor integration, and safe defaults for PvP/highway utility workflows.

## Working Rules
- Keep changes minimal and targeted to the requested feature/fix.
- Do not remove or revert unrelated user changes.
- Preserve existing package layout under `xyz.thm.addon`.
- Follow current coding style (simple classes, explicit overrides, no unnecessary abstractions).
- Prefer deterministic behavior over implicit magic.
- Do not change world interaction mechanics unless Phillip/developer specifically asks for that mechanic change. This includes movement, placement/digging, packet timing/order, server-state gates, recovery, teleport/snap behavior, reconnect handling, lobby handling, and main-server behavior.

## Planning Output
- Plans should not include a `Testing` or `Test Plan` section unless explicitly requested.
- Implementation summaries may still mention validation commands that were actually run and their outcome.

## Local Checkpoints
- Before meaningful code edits, create or update `local-checkpoints.md` in the repo root with a short dated entry describing the task, intended files, and rollback notes.
- Keep checkpoints lightweight and local. Do not create git commits or tags unless explicitly requested.
- When the working tree matches git checkout again, ignoring `local-checkpoints.md` itself, `local-checkpoints.md` may be cleared so it stays small.

## Build & Validation
- Build:
```bash
./gradlew build
```
- Fast compile check:
```bash
./gradlew compileJava
```

## Theme System Notes
- Custom GUI themes live in `src/main/java/xyz/thm/addon/gui/themes`.
- Themes should:
- extend `MeteorGuiTheme`
- implement `RecolorGuiTheme`
- expose a static singleton `INSTANCE`
- be registered in `THMAddon#onInitialize()` via `GuiThemes.add(...)`
- Theme display name is controlled by `getName()` (see `GuiThemeMixin`).

## Module/HUD Registration Notes
- Register modules in `THMAddon#onInitialize()`.
- Register HUD elements through `Hud.get().register(...)`.
- Keep Baritone-dependent modules behind the existing `BaritoneUtils.IS_AVAILABLE` guard.

## Safety Checks Before Finishing
- Confirm code compiles (`./gradlew compileJava` at minimum).
- Verify new classes are imported or covered by wildcard imports.
- Ensure no broken references in `THMAddon`.

## Expected Change Summary Format
When reporting work:
- List changed files.
- Describe behavior impact.
- Mention validation commands executed and outcome.
