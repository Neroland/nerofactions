# Neroland Core APIs used by NeroFactions

Every Neroland Core surface this mod actually consumes, verified against the imports in
`common/src` at 0.1.0-beta.1. NeroFactions compiles against **Core 1.11.0** and pins its
loader-manifest floor to that compiled version, because it needs 1.11.0's `SavedDataRecovery`,
`ErasureConformance` and `PlayerActivity` in addition to the older reputation/config/gate/link
APIs.

## Reputation — `za.co.neroland.nerolandcore.reputation`

**Used:** `ReputationApi`, `ReputationProvider`, `ReputationEvents` (+ `ReputationChange`).

NeroFactions is the ecosystem's **reputation provider**, not just a consumer:

- `reputation/ServerReputationProvider` implements `ReputationProvider` over the persistent
  `data/FactionReputationState` and is installed via `ReputationApi.setProvider(...)` in
  `data/NeroFactionsData.init()` at mod construction, so `ReputationApi.hasRealProvider()`
  is true from startup. Loader hooks bind/unbind the running server per world lifecycle.
  Its `forgetPlayer` override removes the player's row entirely (no tombstone); Core's own
  erasure fan-out drives it alongside this mod's registered eraser, and whichever runs
  second is an idempotent no-op.
- Every reputation **write** in the mod goes through the `ReputationApi` statics — source
  awards and enemy bleed (`reputation/ReputationSources`), decay
  (`membership/FactionDecay`), switch penalties (`membership/FactionMembership`), admin
  commands (`command/FactionCommands`) — so Core's `ReputationEvents` fire for every
  mutation without exception.
- `reputation/TierCrossings` subscribes once to `ReputationEvents.onChange` and turns each
  change into tier-boundary crossings; because *all* writes route through the facade, no
  standing change can ever bypass the event/reward/gate pipeline.
- Reads for gameplay decisions (`GatedRecipe`, `TradeTerminalBlock`, `FactionCommands`,
  the link snapshots) use `ReputationApi.getReputation`.

## Data — `za.co.neroland.nerolandcore.data`

**Used:** `SavedDataRecovery`, `PlayerDataErasure`, `PlayerActivity`; in tests additionally
`ErasureConformance` and `PlayerDataEraser`.

- **`SavedDataRecovery`** — both SavedData stores (`data/FactionReputationState`,
  `data/FactionMembershipState`) load through the recovery guard and refresh their
  last-known-good backups, so a corrupt `.dat` recovers at world load instead of
  crash-looping (the ecosystem rule: every `SavedData.get()` routes through the guard).
  Erasure refreshes the backups in the same request so erased rows do not survive there.
- **`PlayerDataErasure`** — `data/NeroFactionsData.init()` registers the single local
  eraser (`eraseLocal`: reputation + membership + transient terminal session) with the
  shared fan-out, before any faction data can exist. One `/neroland data eraseme`,
  `/neroland data erase <uuid>` or Core retention sweep purges this mod along with every
  other Nero mod.
- **`PlayerActivity`** — `data/RetentionSweep` intersects "players we store data for" with
  `PlayerActivity.stalerThan(retentionDays)` for its own daily inactivity purge, so the
  sweep mints no new personal data and players without an activity record are never
  auto-purged.
- **`ErasureConformance` / `PlayerDataEraser`** (test suite) —
  `NeroFactionsErasureConformanceTest` runs Core's conformance harness against mirrored
  registrations of exactly the production erasure path on every build, proving an erasure
  request leaves nothing behind.

## Config — `za.co.neroland.nerolandcore.config`

**Used:** `ConfigSchema`, `ConfigManager`, `ConfigValue`.

`config/NeroFactionsConfig` declares one `ConfigSchema` (file
`config/nerofactions.properties`, hot-reloadable via `/neroland config reload`) and
registers it with `ConfigManager` first in common init, because everything else — including
telemetry's opt-out — reads it. All gameplay keys are server-authoritative; only
`telemetryEnabled` is deliberately client-local (a server must never force crash reporting
on or off).

## Registration — `za.co.neroland.nerolandcore.registry`

**Used:** `RegistrationProvider` (+ `RegistrationProvider.RegistryEntry`), `CoreCreativeTab`.

- `registry/NeroFactionsBlocks` (the trade terminal), `registry/NeroFactionsItems` (its
  block item) and `registry/NeroFactionsRecipes` (the `nerofactions:gated` serializer) all
  register through Core's cross-loader `RegistrationProvider` seam — eager on Fabric,
  attached to the mod bus via `RegistrationProvider.attach` on NeoForge/Forge.
- There is deliberately **no NeroFactions creative tab**: `NeroFactionsItems`
  contributes the terminal to Core's shared Neroland tab via `CoreCreativeTab.add`.

## Progression — `za.co.neroland.nerolandcore.progression`

**Used:** `ProgressionGates`; in tests additionally `Gate` and `GateScope`.

- `reputation/TierCrossings` calls `ProgressionGates.tryOpen(player,
  nerofactions:<faction>_inner_circle)` on a rising crossing into Inner Circle — `tryOpen`
  so the gate stays shut until its Core prerequisites are met.
- `crafting/GatedRecipe` calls `ProgressionGates.isOpen(player, coreGate)` for recipes that
  declare the optional `core_gate` field.
- The mod ships seven gate definitions in **Core's gate datapack format**
  (`data/nerofactions/neroland_gates/*.json`, `scope: "player"`, `requires` naming Core's
  arc gates); `ShippedGateContentTest` parses them with Core's own `Gate` codec and checks
  the `GateScope` so a malformed gate cannot ship.

## Events — `za.co.neroland.nerolandcore.event`

**Used:** `ThresholdEvents` (+ `ThresholdEvents.ThresholdCrossing`).

`reputation/TierCrossings` publishes the mod's one channel,
`nerofactions:reputation_tier` — one `ThresholdCrossing` per tier boundary crossed, scope =
faction id string (never a player, per Core's contract), value = upper-side tier ordinal,
rising/falling both real. `rewards/RewardGrants` consumes the same crossing enumeration so
events and rewards can never drift; NeroQuests' `custom_event` objective consumes the
channel externally with zero coupling.

## Link — `za.co.neroland.nerolandcore.link`

**Used:** `NeroLinkRegistry`, `LinkModuleInfo`, `LinkSnapshotProvider`, `LinkEvent`.

- `link/FactionsLinkModule` registers `LinkModuleInfo("nerofactions", <mod version>,
  schema 1, sections [standing, membership], actions [])` — the empty action list is
  policy, not backlog — guarded by the `linkModuleEnabled` config switch, last in init.
- `link/FactionsLinkSnapshots` implements `LinkSnapshotProvider`: the two sections are
  derived solely from the requesting player's own rows (structural scoping, no roster).
- `link/FactionsLinkEvents` publishes the `standing` topic with `LinkEvent.forPlayer` via
  `NeroLinkRegistry.eventBus()` — routed by Core to the one player concerned, payload free
  of player identifiers.

## What is *not* used

No Core currency, machine/power, fluid/gas, upgrade, material or highlight APIs — 0.1.0 has
no machines and no economy of its own. `LinkAlerts` is deliberately unused (internal API,
and nothing here is urgent enough to be an alert). The NeroQuests and NeroEconomy
integrations import no Core APIs beyond the ones above — the quests contract rides entirely
on `ReputationApi` + `ThresholdEvents`, and the economy bridge is a compile-only adapter to
NeroEconomy's own pricing API.
