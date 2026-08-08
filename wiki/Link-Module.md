# Link module — what a companion app can see

If your server also runs the separate **NeroLink** bridge mod, a companion app a player has
paired can read that player's own NeroFactions data through Neroland Core's link API.
Without NeroLink installed the module costs one registry entry and does nothing.

- **Module id:** `nerofactions` · **schema version:** `1`
- **Sections:** `standing`, `membership` · **event topic:** `standing`
- **Actions:** **none — by policy, permanently.** Joining or leaving a faction is a
  committal in-world decision (it locks the allegiance slot, arms a cooldown, costs a
  penalty and reshapes what you can craft and trade) and will never be a remote API call;
  reputation is earned through play and can never be written from outside the game.
- **Off switch:** `linkModuleEnabled=false` in `config/nerofactions.properties` — the
  module is simply not registered and companion apps see no NeroFactions data at all.

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
  "schema_version": 1,
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
  "schema_version": 1,
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
  "schema_version": 1,
  "faction": "nerofactions:space_guild",
  "tier": 2,
  "tier_name": "member",
  "rising": true
}
```

The module stores nothing of its own and raises no persistent alerts. A player erased
through Core's erasure hook immediately snapshots as empty. See [Privacy](Privacy).
