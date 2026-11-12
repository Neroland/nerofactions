# Faction API

Use `za.co.neroland.nerofactions.api.NeroFactionsApi` from server-side code when
NeroFactions is installed. Optional consumers should isolate their bridge behind a
mod-presence check and a compile-only dependency. Do not call this API on a client
or a background thread.

| Method | Result |
| --- | --- |
| `factions(server)` | Immutable map of faction ids to `FactionView` records. |
| `membershipOf(server, uuid)` | Immutable set of the player's memberships. |
| `standing(server, uuid, factionId)` | Signed standing, including negative values. |
| `tierOf(server, uuid, factionId)` | Optional `FactionTierView` with the tier id and its threshold. |
| `isEnemy(server, factionA, factionB)` | Whether A lists B as an enemy; the relationship is directional. |
| `awardEvent(server, uuid, factionId, amount, reasonId)` | Primary reputation delta awarded through the configured EVENT weight and daily cap. |

Missing servers, player ids or faction ids return empty collections, an empty
optional, false or zero as appropriate. A known player need not be online:
standing and membership are stored by UUID. An unknown faction's tier is empty;
a known faction at negative standing still resolves to `outsider`.

`FactionView` contains its id, display name, theme, immutable tier list and immutable
enemy list. These views do not expose internal mutable faction definitions.

`awardEvent` takes an `Identifier` reason, such as `neroevents:war_outcome`.
The reason is required but is not currently persisted or emitted. Amounts must be
between 1 and 1,000,000. Awards require the supplied server to be the currently
bound server and the call to run on its server thread. Invalid calls return zero.
Enemy bleed follows the faction's configured enemies. Repeated calls are subject
to the daily cap, but are not deduplicated: callers must make their own event
completion idempotent. No direct standing adjustment is needed for event rewards.

The five existing internal tier constants remain unchanged. Hostility is a separate
band, not an additional tier.
