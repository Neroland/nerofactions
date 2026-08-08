# NeroFactions

> Part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core**.

**Status:** `0.1.0-beta.1` prepared — feature-complete for 0.1.0; runtime verification on a
real client/server is still pending.

NeroFactions is the **reputation and allegiance** mod of the Neroland lineup — and the
ecosystem's reputation provider: it binds a persistent per-player standing store to Neroland
Core's shared `ReputationApi`, so every Nero mod that speaks reputation through Core now
operates on real numbers.

## What it does today

- **Seven datapack-defined factions** — Space Guild (spaceflight establishment), Miner Union
  (organised extraction labour), Nero Corporation (corporate capital), Void Cult (devotees of
  the dark between the stars), Terraforming Authority (engineers of habitability),
  Free Colonists (independent settlers) and Salvagers (scavengers of wrecks) — each with tier
  thresholds, reward tables, enemies and trade specialities, all overridable by datapack.
- **Standing and tiers** — Outsider → Associate → Member → Trusted → Inner Circle
  (0 / 100 / 400 / 1000 / 2500 in the shipped factions), earned through weighted,
  daily-capped sources (quests, combat, trading), bled to enemy factions, and decaying after
  you leave.
- **Membership** — `/nerofactions join`, single allegiance by default, a join cooldown and a
  switch penalty; membership is what lets you trade, standing is what unlocks content.
- **Unlock gating** — the server-authoritative `nerofactions:gated` recipe wrapper (seven
  shipped faction perk recipes), tier-crossing events on Core's shared threshold bus, and
  seven Inner Circle progression gates composing with Core's Earth→space arc.
- **The Faction Trade Terminal** — one shared block: members sell their faction's speciality
  goods for emeralds (and earn standing doing it), buy a curated vanilla catalogue at
  tier-scaled rates, and pick up their faction's banner at Member+.
- **Tier rewards** — item and banner rewards granted once, ever, per tier via a persisted
  watermark.
- **Optional integrations** — NeroQuests (quest rewards pay reputation; quests can gate on
  tier crossings) and NeroEconomy (faction speciality discounts / enemy surcharges on market
  prices, capped by config). Both are detected at startup and entirely optional.
- **A read-only NeroLink module** — a paired companion app can read *its own player's*
  standings and memberships and receive tier-change events; no actions, ever.
- **POPIA/GDPR compliance built in** — one erasure path through Core's shared hook
  (conformance-tested), a daily retention sweep, a self-service JSON export command, and no
  action logging anywhere. See [PRIVACY.md](PRIVACY.md).

Honest scope note: 0.1.0 ships **no custom art** (the terminal reuses vanilla lodestone
textures; cosmetics are pre-styled vanilla banners), no unique gear items, no per-faction
machines and no custom GUIs. The full list of deliberate cuts is in the
[changelog](CHANGELOG.md) under *Not in this release*.

## Requirements

- **Minecraft** 26.1.2 or 26.2, on **NeoForge**, **MinecraftForge/Forge** or **Fabric**
  (the "6 cells").
- **Neroland Core 1.11.0 or newer** — required, loads first. NeroFactions will refuse to
  load against an older Core.
- Java 25 (the requirement of MC 26.x itself).

## Quick start

1. Install Neroland Core and NeroFactions.
2. `/nerofactions factions` — see the seven factions; `/nerofactions join space_guild`
   (or any other) to pledge.
3. Craft and place a **Faction Trade Terminal** (iron + emerald + smooth stone, ungated) and
   sell your faction its speciality goods; fight hostile monsters in your colours.
4. `/nerofactions standing` shows your standing and tier. Tiers unlock the faction's gated
   recipes, better terminal rates, one-time rewards and — at Inner Circle — a Core
   progression gate.

Full documentation lives in the **[wiki](https://github.com/Neroland/nerofactions/wiki)**
(factions, reputation rules, commands, configuration, trading, the gated-recipe format for
pack authors, privacy, and the link module).

## Build targets

- Mod id: `nerofactions` · package `za.co.neroland.nerofactions`
- The build is the repo root, with a flattened cross-loader structure driven by Stonecutter:
  - `common/` — shared, loader-agnostic source spliced into every loader node
  - `fabric/` — Fabric Loom · `forge/` — ForgeGradle · `neoforge/` — ModDevGradle
  - `stonecutter.gradle` — the real root build script; `build.gradle` is intentionally inert

```sh
./gradlew :fabric:26.2:build          # one cell
./gradlew :neoforge:26.1.2:build :neoforge:26.2:build \
          :forge:26.1.2:build :forge:26.2:build \
          :fabric:26.1.2:build :fabric:26.2:build   # all six
```

See [`AGENTS.md`](AGENTS.md) / [`CLAUDE.md`](CLAUDE.md) for agent and contributor context,
[`USING-CORE.md`](USING-CORE.md) for every Core API this mod consumes, and
[`CHANGELOG.md`](CHANGELOG.md) for history and known gaps.
