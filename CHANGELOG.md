# Changelog

All notable changes to **NeroFactions** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

> **Release prerequisite:** NeroFactions 0.1.0-beta.1 requires **Neroland Core 1.11.0 or
> newer** (it compiles against Core's `SavedDataRecovery`, `ErasureConformance`,
> `PlayerActivity` and link APIs). Until Core 1.11.0 is published, release builds cannot
> resolve their Core dependency from the public package repository — local builds resolve it
> from Maven Local. Ship Core 1.11.0 before tagging this release.

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
  (`telemetryEnabled`, client-local). Current builds ship a **placeholder DSN, so nothing is
  ever sent** — the wiring is inert until a real project DSN lands, and the same opt-out
  will govern it then. Full disclosure in `PRIVACY.md`.

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

[Unreleased]: https://github.com/Neroland/nerofactions/compare/v0.1.0-beta.1...HEAD
[0.1.0-beta.1]: https://github.com/Neroland/nerofactions/compare/v0.0.1-alpha.1...v0.1.0-beta.1
[0.0.1-alpha.1]: https://github.com/Neroland/nerofactions/releases/tag/v0.0.1-alpha.1
