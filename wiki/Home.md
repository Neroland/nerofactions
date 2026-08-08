# NeroFactions Wiki

Player- and server-admin documentation for **NeroFactions**, the reputation and allegiance
mod of the Neroland sci-fi Minecraft mod ecosystem. Built on **Neroland Core**.

NeroFactions gives your world **seven factions worth picking a side over**. Every player
holds a standing value with each faction, climbing a five-tier ladder — Outsider, Associate,
Member, Trusted, Inner Circle — by questing, fighting and trading in their colours. Standing
is a real economy: it is weighted and daily-capped at the source, it bleeds to your faction's
enemies, and it decays when you walk away. Membership is a commitment — one faction at a
time by default, with a cooldown and a penalty for switching — and it is what lets you trade
at a faction's terminal, while your *standing* decides what you can craft, what rates you
get, and which one-time rewards and progression gates open.

Version 0.1.0-beta.1 keeps its content honest and vanilla-flavoured: faction perks are gated
recipes for vanilla items, cosmetics are pre-styled vanilla banners, and the one block — the
Faction Trade Terminal — uses the vanilla merchant screen. There are no custom textures, no
faction gear items and no per-faction machines in this release; what is documented here is
exactly what ships.

## Contents

- [Factions](Factions) — the seven factions: theme, enemies, trade specialities and each
  one's Inner Circle era gate.
- [Reputation & tiers](Reputation-and-Tiers) — how standing is earned (sources, weights,
  daily caps), the tier ladder, enemy bleed, decay, and the single-allegiance rule.
- [Commands](Commands) — the full `/nerofactions` command tree with permissions.
- [Configuration](Configuration) — every config key, its default and what it does.
- [Trading](Trading) — the Faction Trade Terminal: membership, sneak-cycling, tier-scaled
  offers, earning standing by trading, and buying your faction's banner.
- [Gated recipes](Gated-Recipes) — the seven shipped faction perk recipes, the
  `nerofactions:gated` recipe format for pack authors, and the
  `nerofactions:reputation_tier` event channel for quest authors.
- [Privacy](Privacy) — what is stored, export, erasure and retention, in practical terms.
- [Link module](Link-Module) — what a paired Neroland companion app can see (and what it
  can never do).

## Requirements

- **Minecraft 26.1.2 or 26.2** on **NeoForge**, **MinecraftForge/Forge** or **Fabric**.
- **Neroland Core 1.11.0 or newer** — required, and the only dependency. NeroFactions
  refuses to load against an older Core.
- Everything else is optional: with **NeroQuests** installed, quests can pay reputation and
  gate on tier crossings; with **NeroEconomy** installed, standing tilts market prices.
  Without either, standing is still fully earnable through combat and terminal trading.

## Quick start

1. `/nerofactions factions` — list the seven factions.
2. `/nerofactions join <faction>` — pledge to one (e.g. `/nerofactions join space_guild`).
3. Craft a **Faction Trade Terminal** (iron + emerald + smooth stone — ungated, anyone can
   place one) and sell your faction its speciality goods; kill hostile monsters while a
   member.
4. `/nerofactions standing` — watch your standing and tier climb; tiers unlock recipes,
   rates, rewards and gates.

## See also

- [Privacy & telemetry disclosure (PRIVACY.md)](https://github.com/Neroland/nerofactions/blob/main/PRIVACY.md)
- [Changelog](https://github.com/Neroland/nerofactions/blob/main/CHANGELOG.md)
- [Issues](https://github.com/Neroland/nerofactions/issues)
