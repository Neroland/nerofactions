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
| `telemetryEnabled` | boolean | `true` | — | Send anonymous, NeroFactions-only crash reports (Sentry, EU servers): stack trace, mod/MC/loader/OS/Java versions, the list of other installed mods, this mod's config and anonymous stability data — **never** IPs, usernames, UUIDs, world data, faction membership, reputation values or chat; file paths are scrubbed of your account name. `false` opts out of all of it; nothing is sent while disabled, and nothing is ever sent before your config choice has loaded. See [Privacy](Privacy). |

## Link module

| Key | Type | Default | Range | Meaning |
| --- | --- | --- | --- | --- |
| `linkModuleEnabled` | boolean | `true` | — | Whether the NeroLink companion module is registered. Snapshots are per-player scoped and never enumerate other players. `false` removes NeroFactions from companion apps entirely. See [Link module](Link-Module). |

## Factions, tiers and membership

| Key | Type | Default | Range | Meaning |
| --- | --- | --- | --- | --- |
| `allowMultipleFactions` | boolean | `false` | — | Whether a player may belong to more than one faction at once. Off (the default) is the single-allegiance rule: joining a second faction is refused until the first is left. |
| `joinCooldownMinutes` | int | `30` | 0–10080 | Real-time minutes a player must wait after leaving a faction before joining any faction again. `0` disables the cooldown. Max one week. |
| `switchPenaltyPoints` | int | `50` | 0–100000 | Reputation points lost with a faction the moment a player leaves it (`0` disables). The remainder then decays per `decayPointsPerDay`. |
| `decayPointsPerDay` | int | `25` | 0–100000 | How many reputation points a left faction's standing moves toward 0 per whole real-time day after leaving. Standing is never wiped or frozen by leaving; it erodes at this rate and stops at 0. `0` disables decay. |
| `enemyBleedRatio` | double | `0.5` | 0.0–1.0 | When a source award grants reputation with a faction, each faction on its enemies list loses `round(award × this ratio)`. Applies only to source awards — never to decay or admin writes. `0.0` disables enemy bleed entirely. |

## Reputation sources — weights and daily caps

Weights multiply the base award; caps are per player, per faction, per source, per
real-time day. Admin grants have neither.

| Key | Type | Default | Range | Meaning |
| --- | --- | --- | --- | --- |
| `questSourceWeight` | double | `1.0` | 0.0–10.0 | Multiplier applied to reputation awarded from quests. |
| `questDailyCap` | int | `300` | 0–1000000 | Max reputation a player can earn per faction per day from quests. |
| `eventSourceWeight` | double | `1.0` | 0.0–10.0 | Multiplier applied to reputation awarded from server events (reserved — nothing fires this source yet). |
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

## Market pricing (with NeroEconomy installed)

These two keys also clamp the trade terminal's own exchange rates, so one pair of knobs
bounds faction pricing everywhere.

| Key | Type | Default | Range | Meaning |
| --- | --- | --- | --- | --- |
| `discountCapPercent` | int | `15` | 0–50 | The largest total market discount (percent) faction standing can ever grant a buyer, regardless of datapack trade multipliers. |
| `surchargeCapPercent` | int | `25` | 0–100 | The total market surcharge (percent) applied when a listing's aligned faction lists one of the buyer's factions as an enemy — this single knob is both the surcharge and its cap. |

## Data retention

| Key | Type | Default | Range | Meaning |
| --- | --- | --- | --- | --- |
| `retentionDays` | int | `365` | 0–3650 | Days of player inactivity (per Neroland Core's shared last-seen record) after which this mod's own daily sweep purges that player's NeroFactions data — standing, memberships, cooldown, decay bookkeeping, accrual counters, reward watermarks and the recovery backups. Complements Core's ecosystem-wide `dataRetentionDays` sweep (opt-in, default 0, driven by `/neroland data purge-inactive`), which already erases NeroFactions data through the shared erasure hook; this key lets faction data expire on its own schedule even when Core's sweep is off. `0` disables this mod's sweep. Players with no Core activity record are never auto-purged (their inactivity cannot be established). |
