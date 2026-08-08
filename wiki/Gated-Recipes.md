# Gated recipes & the tier event channel

This page covers the faction perk recipes players unlock, the `nerofactions:gated` recipe
format for datapack authors, and the `nerofactions:reputation_tier` event channel quest
authors can build on.

## The seven shipped perk recipes

Each faction ships one gated crafting recipe — a themed discount or a vanilla-uncraftable —
that unlocks at a standing tier (and, for two of them, only after a Neroland Core
progression milestone):

| Faction | Recipe | Unlocks at | Also requires | The perk |
| --- | --- | --- | --- | --- |
| Free Colonists | **Lantern** | Associate | — | A lantern from a torch and four iron nuggets instead of eight — the lowest bar on purpose; the colonists welcome newcomers. |
| Terraforming Authority | **Grass block** | Member | — | Bone meal over dirt: literally terraforming in a crafting grid (uncraftable in vanilla). |
| Miner Union | **Jukebox** | Member | — | Built around a copper ingot instead of a diamond — union rates on entertainment. |
| Space Guild | **Spyglass** | Trusted | `nerolandcore:reached_orbit` | One copper ingot instead of two — but only for those who have reached orbit. |
| Nero Corporation | **Name tag** | Trusted | — | Print your own from paper, string and a gold nugget (uncraftable in vanilla): corporate branding. |
| Salvagers | **Anvil** | Trusted | — | Reforged from wreck salvage: one iron block and three ingots instead of three blocks and four. |
| Void Cult | **Ender chest** | Trusted | `nerolandcore:first_colony` | Five obsidian and a pearl instead of eight obsidian and an eye — a rite shared only with the established. |

Things worth knowing as a player:

- **Standing gates, membership does not.** A gated recipe checks the tier your reputation
  resolves to — you keep a faction's recipe after leaving it, *until decay drops you below
  the tier*, and joining never grants what your standing has not earned.
- **Locked recipes are invisible.** They never appear in the recipe book and never toast —
  the grid simply will not produce the result until you qualify. Discovery is the faction's
  job, not the book's.
- **Server-authoritative and fail-closed.** All checks run on the server against live
  standing (pending decay applied first). Auto-crafters and other player-blind machines can
  never craft a gated recipe — a recipe that cannot prove who is crafting stays locked.

## The `nerofactions:gated` format (pack authors)

A gated recipe wraps any ordinary crafting recipe and adds who may use it. Place it like any
recipe, under `data/<namespace>/recipe/`:

```json
{
  "type": "nerofactions:gated",
  "faction": "nerofactions:space_guild",
  "tier": "trusted",
  "core_gate": "nerolandcore:reached_orbit",
  "recipe": {
    "type": "minecraft:crafting_shaped",
    "category": "misc",
    "key": {
      "A": "minecraft:amethyst_shard",
      "C": "minecraft:copper_ingot"
    },
    "pattern": [
      "A",
      "C"
    ],
    "result": {
      "id": "minecraft:spyglass",
      "count": 1
    }
  }
}
```

Fields:

- `faction` — the faction whose ladder is checked. Datapack-added factions work too.
- `tier` — one of `outsider`, `associate`, `member`, `trusted`, `inner_circle`.
- `core_gate` *(optional)* — a Neroland Core progression gate that must additionally be
  open for the crafting player.
- `recipe` — any crafting-type recipe (`minecraft:crafting_shaped`,
  `minecraft:crafting_shapeless`, …), exactly as you would write it standalone.

The wrapper parses on every loader unconditionally, so a pack carrying gated recipes never
half-loads; all gating happens at match time, per player, on the server. An unknown
faction, an unmet gate or an unattributed lookup fails closed — the recipe does not
resolve.

## The `nerofactions:reputation_tier` channel (quest authors)

Every tier boundary a player crosses is published on Neroland Core's shared threshold-event
bus, which NeroQuests' `custom_event` objective (and any other Core consumer) can match on.
The payload:

- **`channel`** — always `nerofactions:reputation_tier`.
- **`scope`** — the **faction id as a string** (e.g. `"nerofactions:space_guild"`). The
  scope names the faction whose ladder was crossed — never a player (the shared bus names
  systems, not people).
- **`value`** — the ordinal of the tier on the **upper side** of the crossed boundary:
  `1` = Associate, `2` = Member, `3` = Trusted, `4` = Inner Circle. Rising, that is the
  tier just reached; falling, the tier just lost. (Outsider has no lower boundary and never
  appears.)
- **`threshold`** — that tier's reputation threshold on the faction's own ladder
  (e.g. 2500 for a shipped Inner Circle).
- **`rising`** — `true` when standing rose across the boundary, `false` when it fell.
  Falling crossings are real and routine: decay, enemy bleed and switch penalties all erode
  standing, and each boundary lost fires.

One crossing fires **per boundary crossed**, in the order they are crossed — a grant that
jumps a player from 0 to 1200 fires Associate, Member, Trusted; three crossings, not one.

A NeroQuests objective for "reach Member with the Space Guild":

```json
{
  "type": "neroquests:custom_event",
  "channel": "nerofactions:reputation_tier",
  "event_scope": "nerofactions:space_guild",
  "direction": "rising",
  "min_value": 2,
  "audience": "everyone"
}
```

(`event_scope` filters to one faction; omit it to match any. `min_value`/`max_value` bound
the tier ordinal; `direction` is `rising`, `falling` or `any`. `audience: "everyone"`
credits each online player working on the quest — since the crossing itself names no
player, the quest author chooses who it counts for.)

The reverse direction also flows with NeroQuests installed: a quest reward of type
`neroquests:reputation` pays standing through Core's reputation API. Quest rewards are
one-shot and author-priced, so they deliberately pay exactly their stated amount —
NeroFactions' source weights and daily caps do not apply to them.
