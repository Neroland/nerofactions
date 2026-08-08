# Commands

Everything lives under `/nerofactions`. Player-facing subcommands need no permission;
operator subcommands require **gamemaster** permission (permission level 2, the vanilla
`/gamemode` level).

```text
/nerofactions standing                                  players, no permission
/nerofactions factions                                  players, no permission
/nerofactions join <faction>                            players, no permission
/nerofactions leave <faction>                           players, no permission
/nerofactions data export                               players, no permission (own data)
/nerofactions data export <player>                      gamemaster
/nerofactions reload-check                              gamemaster
/nerofactions admin grant <player> <faction> <amount>   gamemaster
/nerofactions admin revoke <player> <faction> <amount>  gamemaster
/nerofactions admin reset <player> [<faction>]          gamemaster
```

## Player commands

### `/nerofactions standing`

Your own standing, decay applied first, one line per faction you hold any standing with or
belong to: value plus tier name. Output goes to you only.

### `/nerofactions factions`

Lists every loaded faction with its id, marking the ones you belong to.

### `/nerofactions join <faction>` · `/nerofactions leave <faction>`

Pledge to / leave a faction. `<faction>` is a faction id and tab-completes from the loaded
set; a bare path is convenient — `space_guild` resolves to `nerofactions:space_guild` when
no other faction matches. Join can be refused (with a clear message) if you already belong
to it, already belong to another faction (single-allegiance rule), or left a faction too
recently (cooldown). Leaving applies the switch penalty and starts decay — see
[Reputation & tiers](Reputation-and-Tiers).

### `/nerofactions data export`

Your complete NeroFactions record — standings, memberships, cooldown, decay bookkeeping,
daily earning counters and reward watermarks — as JSON in chat with a **click-to-copy**
component. Nothing is written to disk and nothing is logged. Accessing your own data needs
no permission. See [Privacy](Privacy).

## Operator commands

### `/nerofactions data export <player>` *(gamemaster)*

The same export for another player — for answering a data-access request, including from a
player who has left the server. `<player>` accepts an online player's name or a raw UUID.

### `/nerofactions reload-check` *(gamemaster)*

Re-reads every faction definition from the current datapacks and reports what loaded and
every entry the loader **dropped** (unusable) or **ignored** (pruned field) — the pack
author's view of a problem without digging through the server log.

### `/nerofactions admin grant|revoke <player> <faction> <amount>` *(gamemaster)*

Adjusts a player's standing by exactly `<amount>` (1–1,000,000) up or down. Admin
adjustments are deliberately **exact**: no source weight, no daily cap, and **no enemy
bleed**. `<player>` accepts an online name or a raw UUID (offline players stay reachable).
Tier crossings caused by an admin grant are real — rewards and gates trigger normally.

### `/nerofactions admin reset <player> [<faction>]` *(gamemaster)*

Sets the player's standing with one faction — or, with no faction given, with every loaded
faction — back to exactly 0.

## Related Core commands

Data-subject commands are provided by Neroland Core and cover NeroFactions automatically:

- `/neroland data eraseme` — any player erases their own data across all Neroland mods.
- `/neroland data erase <uuid>` — operator erasure, including departed players.
- `/neroland data purge-inactive` — operator-driven ecosystem-wide inactivity purge.
- `/neroland config reload` — hot-reloads `config/nerofactions.properties` along with the
  other Neroland configs.
