# Changelog

All notable changes to **NeroFactions** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.5.1-beta.1] - 2026-10-18

### Fixed

- **Block and loot drops work as intended on Minecraft 26.3** (NeoForge, Fabric and Forge). Minecraft
  26.3 changed the loot-table file format and silently ignores the old one, so conditions and drop
  modifiers (explosion survival, door/slab block-state checks, drop counts, random chances) were skipped.
  The 26.3 builds now ship every loot table in the new format. 26.1.2 and 26.2 were not affected.

## [0.5.0-beta.1] - 2026-10-11

A quest chapter for NeroQuests, the personal milestones that drive it, and a faction-banner quest
hook. Requires Neroland Core 1.15.0. NeroQuests stays optional: without it the pack is inert data and
nothing else changes.

### Added

- **NeroQuests quest pack.** Ships a NeroQuests quest pack — chapter `nerofactions:standing`
  "Standing Among the Stars", 19 quests — that appears in the quest book when NeroQuests is
  installed; inert otherwise, no dependency. The chapter follows a player from `/nerofactions join`
  (*Sworn In*) through one hidden membership quest per faction into that faction's Inner Circle
  quest, covers the trade terminal (*Behind the Counter*, *Open for Business*), and keeps two
  server-wide world-log quests on the `nerofactions:reputation_tier` channel (*Word Gets Around*,
  *An Inner Circle Opens*). Completing *Sworn In* hands you your faction's banner through the
  `nerofactions:hook/banner_pattern` quest hook — delivered on your next login if you were offline
  or had not yet joined a faction.
- **Record gates.** NeroFactions now writes player-scoped Neroland Core progression gates as
  records of personal milestones: `nerofactions:sworn_in` (first successful join),
  `nerofactions:first_terminal_trade` (first completed terminal trade) and
  `nerofactions:<faction>_member` for each of the seven factions (opened on join, closed on leave).
  They carry no prerequisites and nothing in the mod reads them for access — membership and
  standing keep their own authoritative state — so quest packs and other Core-aware mods can observe
  them without touching NeroFactions. Written through Core 1.15.0's offline-capable gate API, so a
  join or leave applied while the player is offline still records.
- **Shipped-pack checks.** The test suite now validates the quest pack against the pack contract
  (keys translated, prerequisites in-pack, every referenced gate file shipped, server scope only
  where the chapter allows it) and checks the record and hook gates for the record shape.

### Changed

- **Requires Neroland Core 1.15.0** (was 1.13.0): the offline-capable player gate writes and the
  sourced threshold crossings below.
- Tier crossings on `nerofactions:reputation_tier` are now published with `nerofactions` named as
  the source mod (Core 1.15.0's `ThresholdEvents.fire(sourceModId, crossing)`), so listeners that
  ask who published a crossing get an answer. The crossing itself is unchanged: faction id as scope,
  tier ordinal as value, threshold and direction — never a player.

### Privacy

- The new record gates live in Neroland Core's progression store, keyed by player UUID like the
  existing inner-circle gates, and are covered by Core's export and erasure. No new store, no new
  logging; the quest hook logs its gate id only.

## [0.4.0-beta.1] - 2026-10-11

Faction treasuries, trade reputation from market sales and a credit counter on the trade terminal,
all through NeroEconomy 0.3.0's sibling API. NeroEconomy stays optional: without it NeroFactions plays
exactly as before.

### Added

- **Faction treasuries** (NeroEconomy 0.3.0 or newer). Each faction has a credit treasury held by
  NeroEconomy as a NeroFactions-owned account, keyed by the faction id. `/nerofactions treasury` shows
  the treasuries of your factions, `/nerofactions treasury dues <faction> <amount>` pays credits in
  (members only), and `/nerofactions treasury payout <faction> <player> <amount>` pays a member out.
  Payouts are for the faction's Inner Circle, who cannot pay themselves, and for operators. Refusals
  (not enough credits, unvested starting credits, a spent budget) show NeroEconomy's own message.
- **Trade reputation from market sales** (NeroEconomy 0.3.0 or newer). Selling one of your faction's
  speciality goods on the market earns trade reputation with that faction. Specialities are the
  faction's existing `trade` table (an item tag, or an item id), so packs need no new data. The trade
  weight, daily cap and enemy bleed apply. New config key `saleAwardBase` (default 1, `0` turns it off).
- **Credit counter on the trade terminal** (with any credit economy). Using a terminal now also
  offers a clickable credit counter: the curated goods your tier unlocks, priced in credits at your
  faction's rate. `/nerofactions terminal credits` lists it and `/nerofactions terminal buy <offer>`
  buys a line while you stand at the terminal. You are charged first and get the goods only if the
  charge went through. With NeroEconomy 0.3.0 or newer the charge is booked as a NeroFactions terminal
  purchase; with an older economy it falls back to Neroland Core's currency API. New config key
  `terminalCreditsPerEmerald` (default 8, `0` turns the counter off). The emerald trades are unchanged.

### Changed

- NeroEconomy is now compiled against its published Maven artifact (`0.3.1-beta.1`) instead of a
  sibling jar path, from Maven Local or NeroEconomy's GitHub Packages repository (declared only when
  credentials are present). Still compile-only; nothing of NeroEconomy ships in the jar.
- The loader metadata now names NeroEconomy as an optional dependency from `0.3.0-beta.1`
  (Fabric `suggests`, plus `breaks` for older versions; NeoForge `optional`; Forge not mandatory).
- The `auto-deps` workflow's validation builds can now read GitHub Packages.

### Notes

- **Faction discounts stay capped at the market fee.** NeroEconomy funds a buyer's discount from its
  Reserve only up to the market fee on that sale (2 % by default), so most of a large faction discount
  is not applied. The treasury does not top up the difference: the sale event does not say which
  discount was applied, so it could not be paid exactly.

## [0.3.0-beta.1] - 2026-09-24

EMI compatibility. No gameplay, id, tag or config change.

### Added

- **EMI support.** Faction-gated crafting recipes stay hidden in EMI, as they already are in JEI and the
  recipe book, so locked recipes are not advertised to players who have not earned them. EMI skips them
  because they are never placeable. Every other NeroFactions recipe is vanilla crafting, which EMI shows
  on its own, so nothing needed a plugin. The build now compiles against the community EMI Unofficial
  Port (Unstable), the only EMI build for Minecraft 26.x, and dev clients load it with `-PwithEmi`
  (default runs stay JEI-only). EMI stays optional.

## [0.2.0-beta.1] - 2026-09-20

Minecraft **26.3** support, plus the changes previously listed under *Unreleased*.

> **Release prerequisite:** NeroFactions 0.1.0-beta.1 requires **Neroland Core 1.11.0 or
> newer** (it compiles against Core's `SavedDataRecovery`, `ErasureConformance`,
> `PlayerActivity` and link APIs). Until Core 1.11.0 is published, release builds cannot
> resolve their Core dependency from the public package repository — local builds resolve it
> from Maven Local. Ship Core 1.11.0 before tagging this release.

### Minecraft 26.3

- **Minecraft 26.3** as a new Stonecutter node on every loader — NeoForge `26.3.0.7-beta`,
  Forge `26.3-66.0.2` and Fabric (fabric-api `0.161.0+26.3`, NeoForm `26.3-1`) — built alongside
  26.1.2 and 26.2, so every release now ships **nine** loader × version jars.
- VS Code run/debug configurations (`.vscode/launch.json`, `.vscode/tasks.json`) gain the three
  26.3 cells; the "Build all" task now builds all nine.
- CI (`multiloader.yml`, `publish.yml`) builds, attaches and publishes the 26.3 jars.
- Requires **Neroland Core 1.13.0** (was `1.11.0`) — the first Core release with a 26.3
  build. The loader range still derives from the pin (`[${nerolandcore_version},2.0)`).
- JEI pins moved to the newest published builds on each Minecraft version: `29.40.0.101` (26.1.2), `30.35.0.223` (26.2) and `31.3.0.18` (26.3). Compile-time API only — JEI remains a soft dependency and the shipped jar gains no hard requirement.

### 26.3 port notes

- 26.3 API differences are handled with Stonecutter blocks: `PoseStack#rotate` (was `mulPose`), the new `Prediction` argument on `drop` / `placeItemBackInInventory`, `setPermanentlyInvulnerable`, and similar renames.
- `GatedRecipe` wraps `Recipe.DIRECT_CODEC` on 26.3. The NeroEconomy compile pin is now `0.1.0-alpha.1`, to match its bump.
- Build: the shared `common/` Java source is now preprocessed by Stonecutter for every non-active node (`stonecutterProcessCommon`), so common code can carry `//? if >=26.3 {` blocks, and `common/src/main/resources-<mc>` overlay folders are merged over the shared resources for matching nodes (`mergeCommonResources`). The active node still compiles the raw `common/` folder.
- Build plugins aligned with Neroland Core: ModDevGradle `2.0.147` (the older 2.0.141 cannot set up NeoForge 26.3), ForgeGradle `7.0.40`, Stonecutter `0.9.8`.

## [0.1.0-beta.1] — 2026-08-08

The first playable release: the reputation and allegiance layer of the Neroland ecosystem.
Runtime verification on a real client/server is still pending; all six loader×MC cells build
green and the test suite (including Core's erasure-conformance harness) passes.

### Added

#### Reputation provider (Core-wide)

- **NeroFactions is now the ecosystem's reputation provider**: a persistent, world-saved
  `FactionReputationState` bound to Neroland Core's `ReputationApi` at startup
  (`ReputationApi.hasRealProvider()` is true from mod construction; the running server is
  bound/unbound per world lifecycle). Every sibling mod that reads or writes reputation
  through Core now operates on real, persistent per-player standing.
- The provider's `forgetPlayer` override erases a player's reputation row completely —
  **no tombstone**, no hashed marker; an erased player is indistinguishable from one the mod
  has never seen.
- Both SavedData stores load through Core's `SavedDataRecovery` guard, so a corrupted `.dat`
  recovers from the last-known-good backup instead of crash-looping the server.

#### Factions, tiers and membership

- **Seven datapack-defined factions** (`data/nerofactions/nerofactions/factions/*.json`):
  Space Guild, Miner Union, Nero Corporation, Void Cult, Terraforming Authority,
  Free Colonists and Salvagers — each with a display name, theme line, tier thresholds,
  tier reward tables, an enemy list, per-tier trade multipliers on speciality item tags, and
  a cosmetics block. Datapacks can override, rebalance, re-theme or add factions; malformed
  entries are pruned with a report (`/nerofactions reload-check`), never a crash.
- **Five fixed standing tiers** — Outsider / Associate / Member / Trusted / Inner Circle —
  with data-driven thresholds (all seven shipped factions use 0 / 100 / 400 / 1000 / 2500).
- **Membership**: `/nerofactions join` and `leave`, single allegiance by default
  (`allowMultipleFactions` opt-in), a 30-minute join cooldown after leaving
  (`joinCooldownMinutes`), and a 50-point switch penalty on leaving
  (`switchPenaltyPoints`).
- **Reputation decay**: a left faction's standing erodes toward 0 at `decayPointsPerDay`
  (default 25) per whole real-time day — deterministic, applied both by a periodic pass and
  on read, and the player stops being tracked entirely once standing reaches 0.
- **Weighted, daily-capped reputation sources**: quest (weight 1.0, 300/day), event
  (1.0, 300/day — a reserved seam, nothing fires it yet), combat (0.6, 150/day) and trade
  (0.3, 100/day), all per player per faction per source per real-time day. Admin awards are
  exact: weight 1, no cap, no enemy bleed.
- **Enemy bleed**: a source award to one faction costs `round(award × enemyBleedRatio)`
  (default 0.5, `0.0` disables) with each faction on the target's enemy list — the target's
  own list only, never cascading, never applied to decay or admin writes.
- **The internal combat trigger**: killing a hostile monster awards `combatAwardBase`
  (default 2, before weight/cap) with each faction the player is a *member* of — so a
  Core-only server genuinely earns standing with no sibling mods installed.

#### Tier events and progression gating

- **`nerofactions:reputation_tier`** on Core's shared `ThresholdEvents` bus: one crossing per
  tier boundary crossed, in order; `scope` is the faction id string, `value` is the ordinal
  of the tier on the upper side of the boundary (1 = Associate … 4 = Inner Circle),
  `threshold` is that tier's threshold, `rising` says which way. Consumable by NeroQuests'
  `custom_event` objective (and any other Core consumer) with zero coupling. The payload
  never names a player.
- **Seven inner-circle progression gates** (`data/nerofactions/neroland_gates/`): reaching a
  faction's Inner Circle opens `nerofactions:<faction>_inner_circle`, each composing with
  Core's progression arc by era — `nerolandcore:industrial_power` (Miner Union,
  Free Colonists), `nerolandcore:reached_orbit` (Space Guild, Nero Corporation, Salvagers)
  or `nerolandcore:first_colony` (Void Cult, Terraforming Authority).
- **The `nerofactions:gated` recipe serializer**: wraps any ordinary crafting recipe and
  locks it behind a faction tier (plus an optional Core gate). Server-authoritative and
  **fail-closed** — no attributed player (auto-crafters, player-blind lookups), unknown
  faction or unmet gate means the recipe simply does not resolve; locked recipes are never
  advertised in the recipe book. The crafting player is pinned via two targeted mixins,
  including a `ResultSlot` remainder-lookup guard that closes an infinite-craft dupe.
- **Seven gated vanilla-item recipes**, one per faction (lantern, grass block, jukebox,
  spyglass, name tag, anvil, ender chest) — themed discounts and vanilla-uncraftables as
  tier perks. Two also require a Core arc gate (spyglass → `reached_orbit`,
  ender chest → `first_colony`).

#### Trading and rewards

- **The Faction Trade Terminal** — one shared block (no block entity, cheap ungated vanilla
  recipe). Members only: it opens the vanilla merchant screen for the player's *member*
  faction (sneak-use cycles between factions when multiple are allowed), buys the faction's
  speciality goods for emeralds and sells a small curated vanilla catalogue, with rates,
  offer counts and per-offer uses all scaling with the player's tier. Completed trades award
  TRADE reputation (`tradeAwardBase`, weighted and daily-capped) — selling your faction its
  specialities *is* the gather/deliver loop. Menu opening goes through the ecosystem's
  guarded `MenuOpener` seam.
- **Faction banners**: at Member and above the terminal sells the faction's pre-styled
  vanilla banner (base colour + vanilla pattern layers in the faction's palette, custom
  name) for 3 emeralds.
- **The tier-reward engine**: rising tier crossings grant the faction's reward table for
  each newly reached tier — items with vanilla give behaviour plus the faction banner for
  cosmetic entries — exactly once, ever, via a persisted high-water watermark (decaying
  below a tier and re-earning it never re-grants; falling crossings never grant).

#### Commands

- The `/nerofactions` tree: `standing`, `factions`, `join <faction>`, `leave <faction>`,
  `data export [player]`, `reload-check`, and `admin grant|revoke|reset` (gamemaster
  permission for `reload-check`, `admin` and exporting another player's data; admin player
  arguments accept an online name or a raw UUID so departed players stay reachable).

#### Compliance (POPIA/GDPR)

- **One erasure path** (`NeroFactionsData.eraseLocal`) registered with Core's shared
  `PlayerDataErasure` hook: a single request purges reputation, all membership data
  (memberships, join/leave timestamps, cooldown, daily accrual counters, reward watermarks),
  the recovery backups and the terminal's transient session row. Verified on every build by
  Core's `ErasureConformance` harness in the test suite. Erasure runs via Core's
  `/neroland data eraseme`, `/neroland data erase <uuid>` and `/neroland data purge-inactive`.
- **Retention sweep**: NeroFactions' own daily inactivity sweep purges players inactive for
  `retentionDays` (default 365, `0` disables), measured against Core's shared
  `PlayerActivity` last-seen record — no new personal data is minted.
- **DSAR export**: `/nerofactions data export` — the player's complete record as JSON in a
  click-to-copy chat component; never written to disk, never logged.
- **No action logging exists** — trades, joins, leaves and reputation changes are not
  logged, and no log line carries a player identity — so there is no logging to opt out of.
  See `PRIVACY.md` for the full disclosure.

#### Link module (NeroLink companion)

- The read-only `nerofactions` link module, **schema 1**: `standing` and `membership`
  snapshot sections plus the requester-scoped `standing` tier-change event topic, registered
  with Core's `NeroLinkRegistry`. Structurally scoped to the requesting player's own UUID —
  no roster, no aggregate, no operator widening. **Zero actions, by policy**: allegiance and
  reputation can never be mutated remotely. Snapshots read stored values (never more than
  about a minute behind pending decay). `linkModuleEnabled=false` switches the whole module
  off.

#### Integrations (all optional, feature-detected once at startup, no reflection)

- **NeroQuests** — contract-based through Core seams only: its `neroquests:reputation`
  reward writes standing through Core's `ReputationApi` (deliberately bypassing this mod's
  source weights and daily caps — quests are one-shot, author-priced awards), and its
  `custom_event` objective consumes `nerofactions:reputation_tier` for tier-gated quests.
- **NeroEconomy** — a compile-only price-modifier bridge: with NeroEconomy installed,
  faction standing grants speciality discounts (best single multiplier, never stacked,
  capped by `discountCapPercent`, default 15%) and enemy-graph surcharges (flat
  `surchargeCapPercent`, default 25%). The bridge class loads only when NeroEconomy is
  present; the shipped jars contain no NeroEconomy classes.
- **Telemetry**: opt-out, anonymous, NeroFactions-only Sentry crash reporting
  (`telemetryEnabled`, client-local), **active by default** in official builds — the mod ships
  its own project DSN (EU ingest). Source/fork builds with the placeholder DSN send nothing.
  Full disclosure in `PRIVACY.md`.

#### Documentation

- Player/admin wiki (`wiki/`): factions, reputation and tiers, commands, configuration,
  trading, gated recipes and the tier-event channel, privacy, and the link module.
- `USING-CORE.md` — every Neroland Core API this mod consumes and why.
- `PRIVACY.md` — the full POPIA/GDPR and telemetry disclosure.

### Changed

- Version `0.0.1-alpha.1` → `0.1.0-beta.1`.
- Neroland Core dependency pinned to **1.11.0** (the loader-manifest floor equals the
  compiled Core version, so an older Core refuses to load rather than crashing later).
  **Core 1.11.0 is not yet published** — see the note under *Unreleased*.
- `README.md`, `wiki/` and the store descriptions rewritten to describe the implemented mod
  instead of the design pitch.

### Not in this release (deliberate scope cuts)

Every item below is a conscious 0.1.0 decision, recorded so nobody mistakes it for an
accident — and so store pages and the wiki stay honest:

- **No custom art anywhere.** The trade terminal's model reuses **vanilla lodestone
  textures** via texture references; there is not a single custom texture in the jar. The
  only original asset is the mod logo.
- **No custom banner patterns or armour trims.** Faction cosmetics are **pre-styled vanilla
  banners** (vanilla banner items with vanilla pattern layers in faction colours). The
  faction JSONs' `cosmetics.banner_pattern` / `cosmetics.trim_material` fields are
  validated but **dormant forward references**; both cosmetic reward kinds currently
  resolve to the faction banner.
- **No unique faction gear items.** "Locked gear" in 0.1.0 means the seven gated recipes
  for vanilla items — no faction armour, tools or weapons exist.
- **No per-faction machines and no block entities.** One shared, stateless trade terminal
  block is the entire block roster.
- **Faction wallets deferred.** NeroEconomy currently drops non-player account kinds on
  load, so a faction-held account would silently evaporate on restart; wallets wait until
  NeroEconomy persists them.
- **No defection storyline** — leaving is a cooldown, a penalty and decay, not a quest arc.
- **No team-held allegiance** — membership and standing are per-player only.
- **Gather/deliver triggers folded into terminal trading.** COMBAT is the only kill-style
  internal trigger; there is no block-break or pickup hook (far too noisy). Selling
  speciality goods at the terminal is the gather/deliver loop.
- **No NeroEvents integration.** The EVENT reputation source (weight/cap config included)
  is a reserved seam; NeroEvents is an empty skeleton and nothing fires that source yet.
- **No custom GUI screens.** The terminal uses the vanilla merchant screen; everything else
  is commands and chat.
- **Multi-faction membership is config-opt-in** (`allowMultipleFactions`, default off) and
  largely untested as a balance mode — single allegiance is the designed experience.

## [0.0.1-alpha.1] — 2026-06-27

### Added

- Barebones multiloader skeleton: the six-cell Gradle build (NeoForge / Forge / Fabric ×
  Minecraft 26.1.2 / 26.2), CI workflows, and empty mod entry points. No gameplay content.

[Unreleased]: https://github.com/Neroland/nerofactions/compare/v0.3.0-beta.1...HEAD
[0.3.0-beta.1]: https://github.com/Neroland/nerofactions/compare/v0.2.0-beta.1...v0.3.0-beta.1
[0.2.0-beta.1]: https://github.com/Neroland/nerofactions/releases/tag/v0.2.0-beta.1
[0.1.0-beta.1]: https://github.com/Neroland/nerofactions/compare/v0.0.1-alpha.1...v0.1.0-beta.1
[0.0.1-alpha.1]: https://github.com/Neroland/nerofactions/releases/tag/v0.0.1-alpha.1
