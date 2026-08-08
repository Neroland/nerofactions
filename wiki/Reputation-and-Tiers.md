# Reputation & tiers

Standing (reputation) is a per-player integer with each faction, stored on the server. It
resolves to a **tier** on that faction's ladder, and the tier is what the rest of the mod
asks about: gated recipes, terminal rates, rewards and progression gates all read your tier.

## The tier ladder

Five tiers, fixed in order; only the thresholds are data-driven. All seven shipped factions
use:

| Tier | Threshold |
| --- | --- |
| Outsider | 0 |
| Associate | 100 |
| Member | 400 |
| Trusted | 1000 |
| Inner Circle | 2500 |

Everyone starts as an Outsider with every faction. Standing can also go **negative** (enemy
bleed, switch penalties); negative standing is still Outsider, it just has further to climb
back.

Reaching a faction's **Inner Circle** additionally opens the Core progression gate
`nerofactions:<faction>_inner_circle`, provided you have also met that faction's era gate on
Core's own arc (see [Factions](Factions)). If the era gate is not met yet, nothing is lost —
the gate opens the next time you cross into the Inner Circle after meeting it, and gated
recipes check your live standing anyway.

## How standing is earned — sources, weights, daily caps

Every way of earning standing is a **source** with a server-configured weight and a
per-faction-per-day cap. An award is `round(base × weight)`, clamped to what is left of that
source's daily cap for that faction; the caps reset with the real-time (UTC) day, and only
points actually applied count against the cap.

| Source | What fires it | Weight (default) | Daily cap (default) |
| --- | --- | --- | --- |
| Quest | A NeroQuests quest whose author added a reputation reward. Quest rewards are one-shot and author-priced, so they deliberately pay their exact stated amount — the weight/cap here applies to any *other* quest-sourced award routed through this mod. | 1.0 | 300 |
| Event | Reserved for future server events — **nothing fires this source yet**; the config exists so server files stay stable. | 1.0 | 300 |
| Combat | Killing a hostile monster awards `combatAwardBase` (default 2) with **each faction you are a member of** — you fight in your colours; bystanders with mere standing earn nothing. | 0.6 | 150 |
| Trade | Each completed trade at your faction's [terminal](Trading) awards `tradeAwardBase` (default 2). | 0.3 | 100 |
| Admin | `/nerofactions admin grant`/`revoke` — always exact: no weight, no cap, **no enemy bleed**. | 1 (fixed) | none |

With the defaults, a combat kill is worth `round(2 × 0.6) = 1` point per member faction and
a terminal trade `round(2 × 0.3) = 1` point — standing is a long game.

## Enemy bleed

When a source award grants standing with a faction, every faction on **its** enemy list
loses `round(award × enemyBleedRatio)` (default 0.5; `0.0` turns bleed off). Bleed follows
the awarded faction's own list only — it never cascades to enemies-of-enemies, never
applies to decay or admin writes, and a negative award symmetrically *pleases* the enemies.
Climbing the Space Guild ladder quietly costs you with the Salvagers and the Void Cult;
choose accordingly.

## Membership — single allegiance, cooldown, penalty

- **One faction at a time** by default. Joining a second is refused until you leave the
  first; a server can allow multiple with `allowMultipleFactions=true`.
- **Leaving costs**: an immediate loss of `switchPenaltyPoints` (default 50) with the
  faction you left, and a join cooldown of `joinCooldownMinutes` (default 30 real-time
  minutes) before you may join any faction again.
- Membership and standing are separate things: **membership** is what the trade terminal
  and the combat trigger check; **standing** is what recipes, rates, rewards and gates
  check. Leaving a faction does not strip your standing — see decay below.

## Decay — standing fades after you leave

Leaving a faction never wipes or freezes your standing. Instead it erodes toward 0 at
`decayPointsPerDay` (default 25) per whole **real-time** day — earned goodwill fades, and
so does the switch-penalty grudge (negative standing decays toward 0 too). Once it reaches
0, the mod stops tracking you for that faction entirely. Decay is applied both by a
background pass and whenever your standing is actually read, so a lapsed member cannot keep
unlocks their eroded standing no longer supports.

## Watching it move

- `/nerofactions standing` — your standing and tier per faction (decay applied first).
- Tier changes are announced to other installed Neroland mods on Core's shared event bus as
  `nerofactions:reputation_tier` — the mechanism quest packs use to gate on "reach Member
  with the Space Guild" (see [Gated recipes](Gated-Recipes) for the channel's exact
  semantics). The announcement names the faction and the tier, never a player.
- **Tier rewards** are granted when you *rise* into a tier — the faction's reward table for
  that tier pays out (items straight into your inventory, plus the faction banner for
  cosmetic entries) with a chat notice. Each tier pays **once, ever**: decaying below and
  re-earning it does not pay again, and falling never grants. A multi-tier jump pays each
  newly crossed tier in order.
