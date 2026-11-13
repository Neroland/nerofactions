# Link module — what a companion app can see

If your server also runs the separate **NeroLink** bridge mod, a companion app a player has
paired can read that player's own NeroFactions data through Neroland Core's link API.
Without NeroLink installed the module costs one registry entry and does nothing.

- **Module id** `nerofactions`, schema version 2.
- **Sections** `standing`, `membership`, `contracts`, `gear` and `outposts_visited`.
- **Event topics** `standing` and `contract_completed`.
- **Action** `reroll_contract`. Membership changes and standing awards happen in game.
- `linkModuleEnabled=false` disables the module. `linkRerollEnabled=false` disables remote rerolls.

## Scoping — your own data, structurally

Every response is derived from the requesting player's own records and nothing else. There
is no roster, no aggregate, no parameter that could name another player, and the scope is
never widened for operators — an admin's companion app sees the admin's own data and nobody
else's. (Standing is exactly the kind of data a server-wide answer would leak: "who is
Inner Circle with whom" maps a server's social graph.)

Snapshots report **stored** values: the module deliberately does not trigger decay on read,
so a left faction's reported standing can lag the in-game value by up to about a minute
(the background decay pass runs once per real-time minute).

## Section: `standing`

One row per loaded faction the requester has any standing with or membership of — a
faction you have never touched produces no row.

```json
{
  "schema_version": 2,
  "player_online": true,
  "standings": [
    {
      "faction": "nerofactions:space_guild",
      "display_name": "Space Guild",
      "value": 1200,
      "tier": 3,
      "tier_name": "trusted"
    }
  ]
}
```

## Section: `membership`

Current memberships with join timestamps, the join-cooldown end, and the left-faction decay
bookkeeping (all timestamps are epoch milliseconds; `0` means none).

```json
{
  "schema_version": 2,
  "player_online": true,
  "memberships": {
    "nerofactions:space_guild": 1754640000000
  },
  "cooldown_until_ms": 0,
  "left": {
    "nerofactions:salvagers": 1754000000000
  }
}
```

## Event topic: `standing`

Published when the requester's own standing crosses a tier boundary — one event per
boundary, mirroring the in-game tier semantics exactly (`tier` is the ordinal of the tier
on the upper side of the crossed boundary; `rising` says which way). Core routes each event
to the one player it concerns; there are no broadcasts, and the payload carries no player
identifier of any kind.

```json
{
  "schema_version": 2,
  "faction": "nerofactions:space_guild",
  "tier": 2,
  "tier_name": "member",
  "rising": true
}
```

## Contracts and gear

The `contracts` snapshot contains the current server day, the shared reroll
availability, and rows with faction, contract ID, objective type and target, slot,
progress, count, rewards and claim status. The `gear` snapshot reports unlock
state per item and full-set eligibility from the requester's tier and membership.

An online player can send `reroll_contract` with `factionId`, `slot` (0–2) and
`requestId` (UUID). It spends the same token as the board. The player must still
belong to the faction and the contract must be untouched. A successful response
contains the updated contracts snapshot.

## Outpost visits

The `outposts_visited` section lists the requester's discoveries. Each row contains
the faction and dimension IDs. Repeated visits to the same faction in a dimension
produce one row. The companion app lists these discoveries on the Outposts tab.

The `contract_completed` event carries the schema version, faction and contract
ID. Core routes it to the player who claimed it. The new sections and events
contain no coordinates. Core erasure clears the player's records and the
in-memory reroll replay. See [Privacy](Privacy).
