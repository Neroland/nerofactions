# Project context for AI coding agents — nerofactions

> Modrinth is temporarily being removed from the upload and is no longer being referenced. Avoid making references to modrinth.

> `CLAUDE.md` and `AGENTS.md` are kept identical; update both together.

## The mod

- **NeroFactions** is the reputation and allegiance mod built on Neroland Core. The source is
  **0.7.0-beta.1**, with seven factions, a Registry, a daily Contract Board, outposts,
  equipment sets, seven signature machines and a requester-scoped schema 2 link module.
  The changelog lists the deferred signature features.
- Mod id: **`nerofactions`**. Package root: `za.co.neroland.nerofactions`.
- Core pin: **1.17.5**. Targets Minecraft **1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10,
  1.21.11, 26.1.2, 26.2 and 26.3**, across Fabric, Forge and NeoForge (27 cells). The development
  and runtime acceptance target is 26.3. Java 25 builds 26.x; 1.21.x targets Java 21 bytecode.

For the current overhaul, continue through Stage 8 without stopping early; record verified progress and only stop when a real execution limit or a required external action prevents further work.

## Working rules

- **Keep responses concise and direct** — minimal verbosity, minimal formatting.
- **POPIA & GDPR**: keep all logging/telemetry/scripts compliant — only public version strings, never
  personal data; minimise data, set retention limits, support export/erasure and opt-out.
- **NEVER commit or push automatically.** Leave changes **staged**; the developer reviews and commits
  with native git (the source of truth).
- **Use relative paths only** — never hard-code machine-specific absolute paths in committed files.
- **Never run commands against production databases.** Treat any DB command as illustrative.

## Repo layout — flattened cross-loader build

- **The build IS the repo root.** `common/` (shared source spliced into every node), `neoforge/`
  (ModDevGradle), `forge/` (ForgeGradle), `fabric/` (Fabric Loom). Root build files: `settings.gradle`,
  `stonecutter.gradle` (the REAL root build script — Stonecutter repoints `buildFileName` here; the root
  `build.gradle` is inert), `gradle.properties`, `gradlew`, `gradle/`.
- **Version/loader axis = Stonecutter.** Each loader×MC is a real node `:<loader>:<mc>`
  (nine Minecraft versions per loader). `common` is
  NOT a node — its source is spliced via `rootProject.ext.commonJava` / `commonResources`. Dependency pins
  live in `gradle.properties` as `*_version_<mc>` keys; `mc_versions` in `gradle.properties`.
- **Version-specific code in `common/`.** Non-active nodes run `common/` through Stonecutter (`stonecutterProcessCommon`), so shared code uses the same `//? if >=26.3 {` blocks as the loader `src/` trees. Keep the files in the vcsVersion state, and never put a `*/` inside a disabled block. Datapack files whose format differs by version go in `common/src/main/resources-<mc>/`, which is merged over `common/src/main/resources` for every node at or above `<mc>` (`mergeCommonResources`).

## Build & verify

- Build the cells with the Gradle wrapper, e.g. `./gradlew :fabric:26.2:build` or selected 26.x cells:
  `:neoforge:26.1.2:build :neoforge:26.2:build :neoforge:26.3:build :forge:26.1.2:build :forge:26.2:build :forge:26.3:build
  :fabric:26.1.2:build :fabric:26.2:build :fabric:26.3:build`.
- Static analysis: `./gradlew :fabric:26.2:ecjCheck` (the VS Code Problems panel, via `tools/ecj.prefs`).
  The task only FAILS on errors.
- A Cowork agent sandbox cannot decompile Minecraft — run builds natively (or via the local gradle MCP)
  on the developer's machine.
- **Verify the cells build before marking a task done.** Never sign off on an uncompiled change.

## Conventions (cross-loader)

- **Resources are HAND-AUTHORED in `common/src/main/resources`** — the multiloader does not run datagen.
  Validate JSON after edits.
- **Platform seams via ServiceLoader (no Architectury).** Put loader-agnostic code in `common/`; ship one
  impl per loader plus a `META-INF/services` entry. Keep `common/` free of `net.neoforged.*` /
  `net.fabricmc.*` / `net.minecraftforge.*` imports.
- Loader entry points: `NeroFactionsFabric` (+ `NeroFactionsFabricClient`), `NeroFactionsForge`,
  `NeroFactionsNeoForge` — each calls `NeroFactionsCommon.init()` during construction.
- NeoForge/Forge debug tasks use `-PnerofactionsDebug`; Fabric Loom honours Gradle `--debug-jvm`.

## IDE (VS Code) run & debug

- Workspace: **`nerofactions.code-workspace`** (single-root `"."`). Import the Stonecutter nodes as **static
  Eclipse projects**: `./gradlew eclipse` (live Buildship/Loom import is disabled —
  `java.import.gradle.enabled=false`). Re-run `./gradlew eclipse` after dependency changes, then reload
  VS Code. Per-node Eclipse project names are `nerofactions-<loader>-<mc>`.
- **Run/Debug** a cell from `tasks.json` / `launch.json`.

## Wiki — keep `wiki/` updated

- This mod has its own **dedicated wiki** in `wiki/` at the repo root: the player- and
  contributor-facing docs for NeroFactions (features, blocks/items, machines, progression, recipes, FAQ).
- **Whenever you add, change, or remove a feature, update `wiki/` in the same change** — treat the
  wiki as part of "done"; code without a matching wiki update is incomplete.
- One page per topic; keep `wiki/Home.md` as the index that links every page, with relative links
  between pages. Validate Markdown via the gradle MCP `markdown_check` (honours `.markdownlint.json`).
- **The wiki is PUBLIC**: `wiki.yml` publishes `wiki/*.md` verbatim to the GitHub wiki with a CI
  guard**. Never reference private planning files (plans/prompts/internal checklists), never use
  `../` links (they break on the published wiki — link repo files by full GitHub URL), and keep the
  tone player/server-admin facing.
- The wiki is **per-mod** — document only NeroFactions here; cross-mod / ecosystem concepts belong in the
  relevant other mod's own wiki.

## DO NOT

- Commit or push automatically — leave changes staged for the developer.
- Hard-code absolute machine paths in committed files.
- Add loader-specific code to `common/` — use the platform seams.

## Translation workflow

English is the source language for new and changed text. Community translations are intended to be contributed through Weblate; other-language translations are not required when implementing a feature. Preserve existing translations. This supersedes older instructions that require translating every change.
