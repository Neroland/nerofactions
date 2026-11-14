# Configuration

NeroFactions uses Neroland Core's config framework. The file is
**`config/nerofactions.properties`**, created on first launch and hot-reloadable with
`/neroland config reload`. Every key below is **server-authoritative** (the server's value
rules, whatever a client has set) except `telemetryEnabled`, which is deliberately
**client-local** — anonymous crash reporting is each player's own choice and a server must
never force it on or off.

## Telemetry

| Key | Type | Default | Range | Meaning |
| --- | --- | --- | --- | --- |
| `telemetryEnabled` | boolean | `true` | — | Send anonymous, NeroFactions-only crash reports (Sentry, EU servers): stack trace, mod/MC/loader/OS/Java versions, the list of other installed mods, this mod's config and anonymous stability data — **never** IPs, usernames, UUIDs, world data, faction membership, reputation values or chat; file paths are scrubbed of your account name. **On by default**; set `false` to opt out of all of it; nothing is sent while disabled, and nothing is ever sent before your config choice has loaded. See [Privacy](Privacy). |

## Link module

| Key | Type | Default | Range | Meaning |
| --- | --- | --- | --- | --- |
| `linkModuleEnabled` | boolean | `true` | — | Whether the NeroLink companion module is registered. Snapshots are per-player scoped and never enumerate other players. `false` removes NeroFactions from companion apps entirely. See [Link module](Link-Module). |
| `linkRerollEnabled` | boolean | `true` | — | Allow NeroLink to spend the player's daily contract reroll. |

## Factions, tiers and membership

| Key | Type | Default | Range | Meaning |
| --- | --- | --- | --- | --- |
| `hostileBelowStanding` | int | `-100` | -1000000–-1 | Standing strictly below this value triggers Guard hostility and trade refusal. |
| `contractsEnabled` | boolean | `true` | — | Enable Board assignments, objective tracking and rewards. Export and erasure remain available when disabled. |
| `registryEnabled` | boolean | `true` | — | Allow Registry membership interactions. Commands remain available when disabled. |
| `allowMultipleFactions` | boolean | `false` | — | Whether a player may belong to more than one faction at once. Off (the default) is the single-allegiance rule: joining a second faction is refused until the first is left. |
| `joinCooldownMinutes` | int | `30` | 0–10080 | Real-time minutes a player must wait after leaving a faction before joining any faction again. `0` disables the cooldown. Max one week. |
| `switchPenaltyPoints` | int | `50` | 0–100000 | Reputation points lost with a faction the moment a player leaves it (`0` disables). The remainder then decays per `decayPointsPerDay`. |
| `decayPointsPerDay` | int | `25` | 0–100000 | How many reputation points a left faction's standing moves toward 0 per whole real-time day after leaving. Standing is never wiped or frozen by leaving; it erodes at this rate and stops at 0. `0` disables decay. |
| `enemyBleedRatio` | double | `0.5` | 0.0–1.0 | When a source award grants reputation with a faction, each faction on its enemies list loses `round(award × this ratio)`. Applies only to source awards — never to decay or admin writes. `0.0` disables enemy bleed entirely. |

## Miner Union Quota Board

- `quotaBoardEnabled` defaults to `true`. Disable it to turn off the board.
- `quotaBaseTonnes` defaults to 64, with an allowed range of 1–819. The player's tier
  multiplies this base by 1 at Outsider through 5 at Inner Circle.

## Outposts

- `outposts.<faction>` defaults to `true` for each shipped faction. Set a
  faction's key to `false` to stop new outposts from generating in ungenerated chunks.
- `outpostVisitsEnabled` defaults to `true`. Disabling it stops recording new
  faction-and-dimension visits. Existing entries remain available for export and erasure.
- A server datapack can override `nerofactions:outpost/<faction>` in the
  `worldgen/structure_set` registry to tune the spacing and separation in chunks.

## Space Guild Navigation Beacon

- `navigationBeaconEnabled` defaults to `true`. Disable it to stop new compass
  bindings and fall protection.
- Fall protection applies to Guild members within 24 blocks of a loaded beacon.
  Binding a stack of compasses uses one compass and needs space for the result.

## Void Cult altar

| Key | Default | Range | Meaning |
| --- | --- | --- | --- |
| `voidAltarEnabled` | `true` | — | Enable rites at the Void Altar. |
| `voidRiteSeconds` | `90` | 15–180 | Duration of a successful rite in seconds. |
| `voidBacklashPercent` | `15` | 0–100 | Percentage chance of a backlash instead of a boon. |

## Salvagers Deconstructor

- `deconstructorEnabled` enables the machine by default.
- `deconstructorYieldPercent` defaults to 50, with an allowed range of 1–75.
  Recovery is rounded down per ingredient. The allowed and denied item tags are
  `nerofactions:deconstructible` and `nerofactions:no_deconstruct`.

## Free Colonists Community Workbench

`communityWorkbenchEnabled` defaults to `true`. Disable it to close the crafting
and storage menus and pause access management. Existing contents stay in the block.

## Terraforming Authority Atmosphere Processor

| Key | Default | Range | Meaning |
| --- | --- | --- | --- |
| `atmosphereProcessorEnabled` | `true` | — | Enable conversion pulses. |
| `atmosphereProcessorRadius` | `6` | 1–8 | Maximum horizontal radius in blocks. |

## Reputation sources — weights and daily caps

Weights multiply the base award; caps are per player, per faction, per source, per
real-time day. Admin grants have neither.

| Key | Type | Default | Range | Meaning |
| --- | --- | --- | --- | --- |
| `questSourceWeight` | double | `1.0` | 0.0–10.0 | Multiplier applied to reputation awarded from quests. |
| `questDailyCap` | int | `300` | 0–1000000 | Max reputation a player can earn per faction per day from quests. |
| `eventSourceWeight` | double | `1.0` | 0.0–10.0 | Multiplier applied to reputation awarded from server events through the public faction API. |
| `eventDailyCap` | int | `300` | 0–1000000 | Max reputation a player can earn per faction per day from server events. |
| `combatSourceWeight` | double | `0.6` | 0.0–10.0 | Multiplier applied to reputation awarded from combat. |
| `combatDailyCap` | int | `150` | 0–1000000 | Max reputation a player can earn per faction per day from combat. |
| `tradeSourceWeight` | double | `0.3` | 0.0–10.0 | Multiplier applied to reputation awarded from trading. |
| `tradeDailyCap` | int | `100` | 0–1000000 | Max reputation a player can earn per faction per day from trading. |

## Internal triggers and trade

| Key | Type | Default | Range | Meaning |
| --- | --- | --- | --- | --- |
| `combatAwardBase` | int | `2` | 0–1000 | Base reputation awarded (before the combat weight and daily cap) with **each faction the player is a member of** when they kill a hostile monster. `0` disables the internal combat trigger entirely. |
| `tradeAwardBase` | int | `2` | 0–100 | Base reputation awarded (before the trade weight and daily cap) with a faction each time the player completes a trade at its trade terminal. `0` disables the terminal's reputation earning entirely. |

## Economy features

| Key | Type | Default | Range | Meaning |
| --- | --- | --- | --- | --- |
| `saleAwardBase` | int | `1` | 0–100 | With NeroEconomy 0.3.0+: base reputation awarded (before the trade weight and daily cap) with each faction the seller belongs to when they sell one of that faction's speciality goods on the market. `0` disables sale reputation. |
| `terminalCreditsPerEmerald` | int | `8` | 0–10000 | With a credit economy installed: credits one emerald of terminal list price is worth on the trade terminal's credit counter, before faction rates. `0` turns the credit counter off; emerald trades are unaffected. |

## Market pricing (with NeroEconomy installed)

These two keys also clamp the trade terminal's own exchange rates, so one pair of knobs
bounds faction pricing everywhere.

| Key | Type | Default | Range | Meaning |
| --- | --- | --- | --- | --- |
| `discountCapPercent` | int | `15` | 0–50 | The largest total market discount (percent) faction standing can ever grant a buyer, regardless of datapack trade multipliers. |
| `surchargeCapPercent` | int | `25` | 0–100 | The total market surcharge (percent) applied when a listing's aligned faction lists one of the buyer's factions as an enemy — this single knob is both the surcharge and its cap. |
| `marketNudgeCapPercent` | int | `5` | 0–15 | Weekly price increase for one rotating metal commodity. Zero disables the nudge. |
| `corporateCounterEnabled` | boolean | `true` | — | Enable Corporate premium deliveries, stamping, redemption and weekly pricing. |

## Data retention

| Key | Type | Default | Range | Meaning |
| --- | --- | --- | --- | --- |
| `retentionDays` | int | `365` | 0–3650 | Days of player inactivity (per Neroland Core's shared last-seen record) after which this mod's own daily sweep purges that player's NeroFactions data — standing, memberships, cooldown, decay bookkeeping, accrual counters, reward watermarks and the recovery backups. Complements Core's ecosystem-wide `dataRetentionDays` sweep (opt-in, default 0, driven by `/neroland data purge-inactive`), which already erases NeroFactions data through the shared erasure hook; this key lets faction data expire on its own schedule even when Core's sweep is off. `0` disables this mod's sweep. Players with no Core activity record are never auto-purged (their inactivity cannot be established). |
