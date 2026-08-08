# Project context for AI coding agents — nerofactions

> This and `AGENTS.md` are kept identical; update both together.

## The mod

- **NeroFactions** — part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core**.
  **0.1.0-beta.1 prepared** — the reputation and allegiance mod. The Core-wired foundation (platform
  seams, config, telemetry, network channel), the reputation provider (persistent
  `FactionReputationState` bound to Core's `ReputationApi`, erasure-hooked), and the faction layer
  (seven datapack faction definitions with tiers/rewards/enemies/trade, membership with
  single-allegiance + cooldown + switch penalty, real-time reputation decay, weighted/capped
  reputation sources with enemy bleed, `/nerofactions reload-check`), and the unlock-gating layer
  (tier crossings on Core's shared `ThresholdEvents` bus as `nerofactions:reputation_tier`, seven
  player-scoped inner-circle progression gates composing Core's arc, and the server-authoritative
  `nerofactions:gated` recipe condition), and the soft-integration
  layer (feature-detected once at init, no reflection: the NeroQuests contract verified live —
  `neroquests:reputation` rewards + `custom_event` tier gating — plus the internal combat
  reputation trigger so Core-only servers earn standing, the compile-only NeroEconomy
  price-modifier bridge with capped speciality discounts / enemy surcharges, and the NeroEvents
  `Source.EVENT` seam; faction wallets deferred until NeroEconomy persists non-player accounts),
  and the content/presentation layer (the shared vanilla-textured trade terminal block — member-only
  vanilla merchant shops per faction, `Source.TRADE` earning, guarded through the `MenuOpener`
  seam — the once-ever watermark-idempotent tier-reward engine, cosmetics as pre-styled vanilla
  banners per faction, seven themed gated recipes — one per faction — and the full `/nerofactions`
  tree: `standing`/`factions`/`join`/`leave` player-facing plus exact `admin grant/revoke/reset`;
  ADMIN awards no longer enemy-bleed; no custom art/banner patterns/trims/gear items in 0.1.0 by
  scope decision), and the POPIA/GDPR compliance layer (Core's `ErasureConformance` harness run in
  the test suite against both stores — provider-override check included; the single
  `NeroFactionsData.eraseLocal` erasure path also clearing the transient terminal session; the
  mod's own daily `retentionDays` inactivity sweep off Core's shared `PlayerActivity` record,
  default 365; the `/nerofactions data export` DSAR command — full JSON, click-to-copy, no file
  writes, no logging; zero player-identity logging repo-wide, so no action-log opt-out is needed —
  PRIVACY.md states that posture; PRIVACY.md finalised), and the link module (the read-only
  `nerofactions` NeroLink module, schema 1 — requester-scoped `standing` + `membership` snapshots
  read off the live stores with no decay-on-read, **no actions by policy** — allegiance is never
  mutable remotely — and per-player `standing` tier-change events fed after the bus/reward/gate
  chain via `TierCrossings`' single consumer seam; `linkModuleEnabled` kill switch; PRIVACY.md
  covers it) are done, and docs/release prep (Stage 8) shipped: README, CHANGELOG (with the
  full "Not in this release" cut list), USING-CORE.md, the nine-page player/admin wiki and
  honest store descriptions all describe the source as built. **Status: 0.1.0-beta.1
  prepared; runtime verification pending — see `PLAN-0.1.0.md`; wiki written.** Release
  prerequisite: Neroland Core 1.11.0 is pinned but not yet published (resolves from Maven
  Local only), so Core must ship before this repo's CI can build or a release can be tagged.
- Mod id: **`nerofactions`** (matches the registry namespace + every loader manifest). Package root:
  `za.co.neroland.nerofactions`. Author: **Neroland**.
- Version: **0.1.0-beta.1** (bumped for release prep; untagged).
- Targets **MC 26.1.2 AND 26.2** on **NeoForge, MinecraftForge/Forge, and Fabric** → the **"6 cells"**.
  **Java 25.** Mappings = official Mojang names (26.x ships de-obfuscated; no Parchment).

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
  (`:fabric:26.1.2 :fabric:26.2 :neoforge:26.1.2 :neoforge:26.2 :forge:26.1.2 :forge:26.2`). `common` is
  NOT a node — its source is spliced via `rootProject.ext.commonJava` / `commonResources`. Dependency pins
  live in `gradle.properties` as `*_version_<mc>` keys; `mc_versions=26.1.2,26.2`.

## Build & verify

- Build the cells with the Gradle wrapper, e.g. `./gradlew :fabric:26.2:build` or all six:
  `:neoforge:26.1.2:build :neoforge:26.2:build :forge:26.1.2:build :forge:26.2:build
  :fabric:26.1.2:build :fabric:26.2:build`.
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
- **The wiki is PUBLIC**: `wiki.yml` publishes `wiki/*.md` verbatim to the GitHub wiki with **no CI
  guard**. Never reference private planning files (plans/prompts/internal checklists), never use
  `../` links (they break on the published wiki — link repo files by full GitHub URL), and keep the
  tone player/server-admin facing.
- The wiki is **per-mod** — document only NeroFactions here; cross-mod / ecosystem concepts belong in the
  relevant other mod's own wiki.

## DO NOT

- Commit or push automatically — leave changes staged for the developer.
- Hard-code absolute machine paths in committed files.
- Add loader-specific code to `common/` — use the platform seams.
