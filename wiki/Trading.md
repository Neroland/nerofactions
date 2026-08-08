# Trading — the Faction Trade Terminal

The **Faction Trade Terminal** is NeroFactions' one block: a shared counter that serves
every faction. Which faction's shop you see is decided by *your* memberships, not by the
block — a base needs only one terminal.

In this release the terminal reuses vanilla textures (a re-dressed lodestone look) and the
vanilla merchant screen — there is deliberately no custom art or custom GUI yet.

## Placing one

The terminal is craftable by anyone, ungated and cheap:

```text
I I I        I = iron ingot
I E I        E = emerald
S S S        S = smooth stone
```

Placing a faction counter takes nobody's permission; **trading at one takes membership**.

## Using it

- **Right-click** — opens your faction's shop (the vanilla trading screen). If you belong
  to no faction you are told how to join instead. If you belong to several (servers with
  `allowMultipleFactions=true`), you get your remembered selection, or your best-standing
  faction the first time.
- **Sneak + right-click** — cycles to your next member faction (multi-faction servers
  only). The selection is remembered in server memory for the session — never saved.
- Pending reputation decay is applied before the shop is built, so the rates you see are
  the standing you actually still have.
- The screen shows no villager level or XP bar — it is a terminal, not a villager. Offers
  have limited uses; close and reopen the terminal to restock.

## What is on the counter

1. **The faction buys its specialities** (your earning loop). For each of the faction's
   speciality tags (see [Factions](Factions)) the terminal buys the tag's vanilla items
   from you for emeralds. The base rate is 4 items per emerald, improved by the faction's
   per-tier multiplier — higher tiers hand over fewer items per emerald. Emeralds
   themselves are never bought, and only vanilla items appear, so the stock works in any
   pack.
2. **A small curated sell-side** of plain vanilla goods for emeralds — bread and torches
   from the start, iron and glass from Member, redstone and ender pearls from Trusted —
   priced by your faction's best rate at your tier: loyalty makes the whole counter
   cheaper.
3. **The faction banner** — from **Member** tier the terminal sells the faction's
   pre-styled vanilla banner (its colours and pattern, named after the faction) for a flat
   **3 emeralds**. Cheap on purpose: it is identity, not loot.

Your **tier scales everything**: more speciality items bought per tag, more curated goods
unlocked, more uses per offer, and better rates through the per-tier multipliers. All rates
are clamped to the server's pricing band (`discountCapPercent` / `surchargeCapPercent` —
see [Configuration](Configuration)).

## Trading earns standing

Every completed trade awards TRADE reputation with the faction — `tradeAwardBase` (default
2) through the trade weight (0.3) and the trade daily cap (100/day per faction). Selling
your faction its speciality goods **is** the gather-and-deliver loop. Set
`tradeAwardBase=0` to make terminals purely commercial.

## For server admins

- One block, no block entity: terminals hold no inventory and no state, so they are free to
  place in multiples and safe to break (they drop themselves; pickaxe required).
- Everything shown is resolved server-side per interaction from live standing — there is
  nothing to desync and no way for a client to assert a better shop.
- The relevant config keys are `tradeAwardBase`, `tradeDailyCap`, `tradeSourceWeight`,
  `discountCapPercent` and `surchargeCapPercent` — see [Configuration](Configuration).
