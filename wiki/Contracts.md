# Contracts

Craft a Contract Board from paper, a book and planks. Join a faction at a Faction Registry,
then use the Board to view three daily contracts for each of your factions. A Registry's
**Contracts** button opens a Board within four blocks, provided it is also within your reach.

Contracts support four objectives:

- **Deliver:** the Board removes matching items from your main inventory. You can deliver in parts.
- **Defeat:** kills are credited to the responsible player, including their projectiles and pets.
- **Craft:** take the required items from a crafting result slot; previewing a recipe does not count.
- **Visit:** enter a qualifying village, ruined portal or shipwreck. No coordinates are recorded.

Use **Claim reward** once the objective is complete. Reputation uses the QUEST source's configured
weight and daily cap, shared with quest rewards. Item rewards are delivered to your inventory,
with overflow dropped at your feet. Credits are paid only when a persistent currency provider is
installed; the Board displays zero credits otherwise. A refused or failed payment is reported
on the Board. A claim is reserved before reward effects, so repeating a request cannot pay twice.

Each player has one reroll token per day, shared across their factions. Only untouched,
unclaimed contracts can be rerolled. Daily assignments follow the Overworld day and are stable
for each player; moving time backwards does not restore assignments or tokens. Offline
assignments expire when the server advances to the next day. Only a lifetime completion count
is retained beyond the current daily state.

The shipped pool contains six contracts per faction. Additional tier-eligible choices become
available as standing rises. Contracts work with Neroland Core alone; no sibling mod is required.

## Datapacks

Place JSON files under `data/<namespace>/nerofactions/contracts/<faction>/<name>.json`.
Fields are `faction`, `type`, `target`, `count`, `reputation`, optional `credits`, optional `item`
and `item_count`, `min_tier`, `max_tier` and `weight`. Item and entity targets may be ids or `#tags`.
Structure visits require a tag and count 1. Tier numbers run from 0 (Outsider) to 4 (Inner Circle).
Invalid entries are reported by `/nerofactions reload-check` and excluded from the pool.

`contractsEnabled=false` disables assignment, objective tracking and rewards. Existing data
continues to support export, erasure and retention. See [Privacy](Privacy).
