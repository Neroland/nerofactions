# The seven factions

NeroFactions ships seven factions. Each is defined entirely in data — display name, theme,
tier thresholds, reward tables, enemy list, trade specialities and cosmetics — so server
packs can rebalance, re-theme or add factions without touching code (the file format is
described at the end of [Gated recipes](Gated-Recipes)' sibling topics; faction files live
in a datapack under `data/<namespace>/nerofactions/factions/`).

All seven shipped factions use the same ladder: **Outsider 0 · Associate 100 · Member 400 ·
Trusted 1000 · Inner Circle 2500**.

Every faction's **trade specialities** are the item tags its [trade terminal](Trading) buys
from members at improving per-tier rates — and, with NeroEconomy installed, the goods its
standing discounts on the market. **Enemies** matter twice: earning standing with a faction
[bleeds standing](Reputation-and-Tiers) from the factions on its enemy list, and with
NeroEconomy installed a faction surcharges buyers aligned with its enemies.

Reaching a faction's **Inner Circle** opens a Neroland Core progression gate
(`nerofactions:<faction>_inner_circle`) — but only once you have also reached the matching
era of Core's own progression arc. A miner's inner circle expects an industrial power base;
the spacefaring factions expect you to have reached orbit; the colonial-era factions expect
a first off-world colony.

| Faction | Theme | Enemies | Trade specialities | Inner Circle era gate |
| --- | --- | --- | --- | --- |
| **Space Guild** (`nerofactions:space_guild`) | The blue-and-silver spaceflight establishment: charts, launch clearances and the quiet certainty that the void belongs to those who file the paperwork. | Salvagers, Void Cult | Iron ingots (`c:ingots/iron`), redstone dust (`c:dusts/redstone`) | `nerolandcore:reached_orbit` |
| **Miner Union** (`nerofactions:miner_union`) | Organised labour in amber and steel: the hands that actually dig the worlds the corporations sell, and they have not forgotten it. | Nero Corporation | Ores (`c:ores`), ingots (`c:ingots`) | `nerolandcore:industrial_power` |
| **Nero Corporation** (`nerofactions:nero_corporation`) | Corporate capital in black and gold: everything has a price, every price has a margin, and the margin is always theirs. | Miner Union, Free Colonists | Ingots (`c:ingots`), gems (`c:gems`) | `nerolandcore:reached_orbit` |
| **Void Cult** (`nerofactions:void_cult`) | Purple-black devotees of the alien dark between the stars, convinced it is listening — and lately, convinced it has begun to answer. | Space Guild, Terraforming Authority | Amethyst (`c:gems/amethyst`), ender pearls (`c:ender_pearls`) | `nerolandcore:first_colony` |
| **Terraforming Authority** (`nerofactions:terraforming_authority`) | Green-and-white engineers of habitability: give them a dead rock and a century and they will hand back a garden with a rulebook. | Void Cult | Seeds (`c:seeds`), saplings (`minecraft:saplings`) | `nerolandcore:first_colony` |
| **Free Colonists** (`nerofactions:free_colonists`) | Independent settlers under an earthy patchwork of flags: no charter, no landlord, and absolutely no interest in acquiring either. | Nero Corporation | Foods (`c:foods`), planks (`minecraft:planks`) | `nerolandcore:industrial_power` |
| **Salvagers** (`nerofactions:salvagers`) | Rust-and-grey scavengers of wrecks and write-offs: one fleet's tragedy is their quarterly forecast, and finders is the only law that keeps. | Space Guild | Copper ingots (`c:ingots/copper`), nuggets (`c:nuggets`) | `nerolandcore:reached_orbit` |

Notes:

- The enemy graph is directional by design — each faction's list is its own opinion, and a
  datapack may make it asymmetric. The shipped seven happen to dislike each other mutually.
- Each faction has a **banner design** in its palette, sold at its terminal from Member
  tier and granted by some tier reward tables. Reaching Inner Circle awards the faction's
  reusable pattern item. Put it in a loom with a banner and dye to apply that faction's emblem.
- Inner Circle also awards four smithing templates and four matching inlays. Use one pair
  at a smithing table with a piece of trimmable armour. The matching faction pair
  adds its emblem and colours without changing the armour's stats.
- Tier **reward tables** vary per faction (mostly themed vanilla goods, e.g. the Space
  Guild's Trusted spyglass or the Salvagers' Inner Circle anvil) and are granted once, ever,
  per tier — see [Reputation & tiers](Reputation-and-Tiers).
- Each faction ships a **gated perk recipe**. Void Cult standing at Member also unlocks
  the **Rift Blade** recipe. Members at that tier can briefly blind targets with the blade
  and gain two experience points on a lethal hit against a hostile mob. See [Gated recipes](Gated-Recipes).

## Miner Union Quota Board

Use an empty hand to check your weekly quota. Hold ores, ingots or raw metal blocks
when using the board to deposit up to 16 items. Ores and ingots count as one tonne,
and raw blocks count as nine. The quota rises with standing and applies across
all boards. Deposits earn capped trade standing and two credits per tonne if a
credit economy is active.

The Union Breaker is a Member-tier recipe. It clears a 3×3 face of eligible
stone or ore. Trusted members clear a second layer and pay less durability
per block. Sneak to mine just the targeted block.

The four Union Exosuit pieces are Trusted recipes. Eligible members gain Haste I
and take 35% less explosion and fall damage below Y 40 or in dimensions without
skylight. The complete set applies Slowness I in exposed daylight. The pieces
continue to protect as armour below Trusted.

## Faction Registry

Craft a Faction Registry from iron ingots, paper and a book. Use it to browse faction standing and
join or leave through a confirmation button. The screen shows the server's leave penalty and
rejoin cooldown before confirmation. Commands remain available.

Faction definitions support optional `color` (#RRGGBB), `motto`, `icon` and `description_key` fields.
Existing packs may omit them. The Registry lists factions, their enemies, signed standing, tier,
progress to the next tier and the reward entries defined for that tier. Colours, mottos and icons
can be changed through datapacks. Registry textures are original and reproducible with
`tools/gen_textures.py` in the source repository.
