# NeroFactions

> Part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core**.

**Source version** `0.7.0-beta.1`.

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
- **Membership** — join and leave at a Faction Registry, with one allegiance by default,
  a join cooldown and a switch penalty. The commands remain available.
- **Unlock gating** — the server-authoritative `nerofactions:gated` recipe wrapper (seven
  shipped faction perk recipes), tier-crossing events on Core's shared threshold bus, and
  seven Inner Circle progression gates composing with Core's Earth→space arc.
- **The Faction Trade Terminal** — one shared block: members sell their faction's speciality
  goods for emeralds (and earn standing doing it), buy a curated vanilla catalogue at
  tier-scaled rates, and pick up their faction's banner at Member+.
- **Tier rewards** — item and banner rewards granted once, ever, per tier via a persisted
  watermark.
- **Optional integrations** — NeroQuests (a shipped quest chapter, `nerofactions:standing`
  "Standing Among the Stars", 47 quests, appears in the quest book when NeroQuests is installed and
  is inert data otherwise; quest rewards pay reputation; quests can gate on tier crossings and on
  NeroFactions' record gates) and NeroEconomy (faction speciality discounts / enemy surcharges on market
  prices, capped by config; faction treasuries with dues and Inner Circle payouts; trade reputation
  from market sales of your faction's specialities; a credit counter on the trade terminal). Both
  are detected at startup and entirely optional.
- **NeroLink schema 2** — a paired companion app can read its own standings, memberships,
  contracts, gear unlocks and outpost visits. It can spend the same daily reroll token as the board.
- **POPIA/GDPR compliance built in** — one erasure path through Core's shared hook
  (conformance-tested), a daily retention sweep, a self-service JSON export command, and no
  action logging anywhere. Anonymous crash reporting is **on by default** and **opt-out**:
  set `telemetryEnabled=false` in `config/nerofactions.properties`. See [PRIVACY.md](PRIVACY.md).

The **Contract Board** offers daily assignments from a pool of 42 contracts.
All seven factions have outposts with Envoys, Guards and patterned banners,
plus four-piece armour sets, loom patterns and matching trims. Negative standing
and Trusted rival membership affect access to faction counters.

Seven signature machines include the Quota Board, Navigation Beacon, Void Altar,
Deconstructor, Atmosphere Processor, Community Workbench and Corporate Contract
Terminal. The seven signature tools include the Union Breaker, Guild Grapple,
Rift Blade, Salvage Scanner, Survey Spade, Settler's Multitool and Executive Stamp.

A few rules worth knowing:

- The **Atmosphere Processor** converts only natural ground (dirt, sand, gravel and
  grass) and never stone or building blocks. Every target is checked through the
  loader's own block-break event first, so claim and protection mods can refuse it,
  and a refused target costs nothing. It runs only on player-triggered pulses.
- The **Executive Stamp** needs a currency provider such as NeroEconomy; without one it
  refuses and keeps your gold.
- The **Settler's Multitool** axe and hoe right-click actions need Free Colonists
  membership at Member standing; anyone else gets shovel behaviour.
- The **Deconstructor** has a short per-player cooldown between uses.
- **Delivery contracts** accept plain items only (no renamed, damaged or modified stacks).
- **Salvager Patchwork** rolls a loot container's own loot table once more on its first
  opening, filling empty slots only. The **Corporate Power Suit** gives 15 armour
  (2/6/5/2) and 0.5 toughness per piece, and the **Authority Environment Suit** makes its
  wearer immune to poison damage.
- Outpost **Guards and Envoys** cannot be carried off in boats or minecarts.

See the [changelog](CHANGELOG.md) for deferred content.

## Requirements

- **Minecraft** 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1.2, 26.2 or 26.3,
  on **NeoForge**, **MinecraftForge/Forge** or **Fabric** (27 build cells).
- **Neroland Core 1.17.5 or newer** — required, loads first. NeroFactions will refuse to
  load against an older Core.
- Java 25 for Minecraft 26.x; Java 21 for Minecraft 1.21.x.

## Quick start

1. Install Neroland Core and NeroFactions.
2. Craft or find a **Faction Registry**. Choose a faction and confirm your pledge.
   Find a **Contract Board** to take daily assignments.
3. Craft and place a **Faction Trade Terminal** (iron + emerald + smooth stone, ungated) and
   sell your faction its speciality goods; fight hostile monsters in your colours.
4. `/nerofactions standing` shows your standing and tier. Tiers unlock the faction's gated
   recipes, better terminal rates, one-time rewards and — at Inner Circle — a Core
   progression gate.

Full documentation lives in the **[wiki](https://github.com/Neroland/nerofactions/wiki)**
(factions, reputation rules, commands, configuration, trading, the gated-recipe format for
pack authors, privacy, and the link module).

## Build targets

- **Minecraft 1.20.5** on Fabric and **1.20.6** on Fabric, Forge and NeoForge.
- **Requires Neroland Core 1.23.0 or newer** (below 2.0).
- Mod id: `nerofactions` · package `za.co.neroland.nerofactions`
- The build is the repo root, with a flattened cross-loader structure driven by Stonecutter:
  - `common/` — shared, loader-agnostic source spliced into every loader node
  - `fabric/` — Fabric Loom · `forge/` — ForgeGradle · `neoforge/` — ModDevGradle
  - `stonecutter.gradle` — the real root build script; `build.gradle` is intentionally inert

```sh
./gradlew :fabric:26.2:build          # one cell
./gradlew :neoforge:26.1.2:build :neoforge:26.2:build :neoforge:26.3:build \
          :forge:26.1.2:build :forge:26.2:build :forge:26.3:build \
          :fabric:26.1.2:build :fabric:26.2:build :fabric:26.3:build   # all nine
```

See [`AGENTS.md`](AGENTS.md) / [`CLAUDE.md`](CLAUDE.md) for agent and contributor context,
[`USING-CORE.md`](USING-CORE.md) for every Core API this mod consumes, and
[`CHANGELOG.md`](CHANGELOG.md) for history and known gaps.
