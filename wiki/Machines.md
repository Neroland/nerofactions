# Faction machines

| Machine | Use |
| --- | --- |
| Miner Union Quota Board | Use with an empty hand for your quota. Use with ores, ingots or raw metal blocks to deposit them for standing and, with a credit provider, credits. |
| Space Guild Navigation Beacon | Use a compass on the block to bind a waypoint. Guild members within 24 blocks are protected from falls. |
| Void Cult Void Altar | Hold at least four amethyst shards and use the altar in Survival with three experience levels. A successful rite grants night vision, reach and Wither protection. A backlash applies Blindness and Weakness. |
| Salvagers Deconstructor | Use an eligible unmodified crafted item in Survival. The machine consumes one complete recipe batch and returns a rounded-down share of each ingredient. |
| Free Colonists Community Workbench | Use the bench for 3×3 crafting. Sneak-use with an empty hand to open shared storage. |
| Terraforming Authority Atmosphere Processor | Use wheat seeds or bone meal to convert one nearby patch of natural ground. Check charge and radius with an empty hand. |
| Corporate Contract Terminal | Use with an empty hand to see today's premium delivery. Use the specified ingot stack to deliver the entire batch. |

See [Configuration](Configuration) for machine switches and limits.

Deconstructor recovery defaults to 50%, rounds down per ingredient, and cannot
exceed 75%. Damaged, enchanted, modified and denied items are refused. The
machine also refuses ambiguous recipes and batches with zero yield. Each player
has a short cooldown (half a second) between uses.

Navigation Beacons grant waypoint and fall protection benefits. Rocket fuel
discounts await a Nerospace launch modifier.

## Community Workbench

The Colonist who places the bench owns it. The owner can invite up to 16 Colonists
using `/nerofactions workbench grant <x> <y> <z> <player>`, or revoke access with
`/nerofactions workbench revoke <x> <y> <z> <player>`. The owner must be within
eight blocks and the target must be online. Access requires active Colonist
membership. A gamemaster can assign an ownerless bench to an online Colonist using
`/nerofactions workbench recover <x> <y> <z> <player>`.

Erasing the owner locks both menus, clears the guest list and leaves the inventory
in the bench. Breaking the block drops its contents like a chest. Use normal base
protection to guard the physical block.

## Atmosphere Processor

Connect a Core-compatible power source. The buffer holds 12,000 NE and accepts
power on every side by default. Authority members at Member standing can spend
one wheat seed or bone meal and 120 NE per successful conversion in Survival.
The machine accepts one pulse per second and grows one ring per four successful
conversions. It only acts in loaded chunks, near its own height and within the
server's configured radius. The user must have building permission at the target.

Only natural ground converts:

| Ground | Wheat seeds | Bone meal |
| --- | --- | --- |
| Dirt | Grass block | Podzol |
| Sand or gravel | Moss block | Moss block |
| Grass block or podzol | Oak sapling on top | — |

Stone, cobblestone, planks, bricks and other building blocks are never converted.
Before changing a block, the processor asks the loader's own block-break event
whether the user may break it there (and the space above, for a sapling). Claim and
protection mods that listen to that event can refuse, and a refused target costs no
energy or reagent. Conversion only happens when a player triggers a pulse; automatic
unattended conversion is deferred.

## Corporate Contract Terminal

Active Corporate Members in Survival can deliver one batch per world day. The
batch must be plain ingots: renamed or otherwise modified stacks are refused. The
offer rotates among iron, gold and copper. Each completed batch awards capped
standing and 80 credits when the credit provider accepts payment. On a Core-only
server or if the provider refuses payment, it pays three emeralds.

The Executive Stamp only works when a currency provider such as NeroEconomy is
installed. Without one, it refuses to stamp and keeps your gold.
