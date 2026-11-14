[![NeroLink App — Beta](https://img.shields.io/badge/NeroLink_App-Now_in_Beta-60d4e8?style=for-the-badge)](https://nerolandmc.net/nerolink/#beta) [![Explore the Neroland ecosystem](https://img.shields.io/badge/Explore-The_Neroland_Ecosystem-1a5a6c?style=for-the-badge)](https://nerolandmc.net/ecosystem/)

> 📱 **NeroLink App Beta — your Neroland world on your phone.** Check energy, alerts and machines live, claim quest rewards and search your storage without logging in. **[Join the beta at nerolandmc.net →](https://nerolandmc.net/nerolink/#beta)**
>
> 🌌 **Explore the Neroland ecosystem.** See how NeroFactions fits together with the rest of the Nero mods — every mod, wiki and changelog in one place. **[View the ecosystem at nerolandmc.net →](https://nerolandmc.net/ecosystem/)** · [NeroFactions on the website](https://nerolandmc.net/mods/nerofactions/)

---

# NeroFactions

**Pick a side — seven joinable factions, earned reputation, and unlocks that make allegiance a real long-term choice.**

NeroFactions is the **reputation & factions** mod of the Neroland ecosystem — and the lineup's shared reputation provider: every player's standing with every faction is tracked persistently through **Neroland Core**'s reputation API, where the whole ecosystem can read it. Standing climbs a five-tier ladder — Outsider → Associate → Member → Trusted → Inner Circle — and can also fall below zero into a hostile band. Your tier unlocks recipes, gear, machines, trade rates, one-time rewards and progression gates. Joining is a commitment, not a menu pick: you pledge at a **Faction Registry**, one faction at a time (by default), with a cooldown and a standing penalty for switching, and standing that decays after you walk away.

---

## The seven factions

1. **The Space Guild** — the blue-and-silver spaceflight establishment: charts, launch clearances and the quiet certainty that the void belongs to those who file the paperwork. Buys iron and redstone. Rivals: Salvagers, Void Cult.
2. **The Miner Union** — organised labour in amber and steel: the hands that actually dig the worlds the corporations sell. Buys ores and ingots. Rival: Nero Corporation.
3. **The Nero Corporation** — corporate capital in black and gold: everything has a price, every price has a margin, and the margin is always theirs. Buys ingots and gems. Rivals: Miner Union, Free Colonists.
4. **The Void Cult** — purple-black devotees of the alien dark between the stars, convinced it is listening. Buys amethyst and ender pearls. Rivals: Space Guild, Terraforming Authority.
5. **The Terraforming Authority** — green-and-white engineers of habitability: give them a dead rock and a century and they will hand back a garden with a rulebook. Buys seeds and saplings. Rival: Void Cult.
6. **The Free Colonists** — independent settlers under an earthy patchwork of flags: no charter, no landlord, no interest in either. Buys food and planks. Rival: Nero Corporation.
7. **The Salvagers** — rust-and-grey scavengers of wrecks and write-offs: finders is the only law that keeps. Buys copper and nuggets. Rival: Space Guild.

Factions are **datapack objects** — thresholds, rewards, enemies and trade specialities are all data, so packs can rebalance, re-theme or add factions without forking.

## What ships in 0.7.0-beta.1

- ⚖️ **A real reputation economy** — standing is earned through weighted, daily-capped sources (quests, contracts, combat, trading), **bleeds to your faction's enemies** as you climb, and **decays** after you leave. Every knob is server config.
- 📋 **Contract Board** — three daily contracts per faction from a pool of 42 (deliver, defeat, craft and visit objectives), capped standing rewards, credits when an economy is installed, and one daily reroll token. Fully datapack-driven.
- 🏕️ **Seven faction outposts** — naturally generated, each with an Envoy, two Guards, a Faction Registry, a Contract Board, a bound Trade Terminal and a patterned banner. Guards defend their Envoy and turn on visitors whose standing is hostile.
- 🛡️ **Seven faction armour sets** — Union Exosuit, Guild Flight Suit, Void Vestments, Corporate Power Suit, Authority Environment Suit, Colonist Field Gear and Salvager Patchwork, each with a Trusted full-set bonus and a trade-off.
- 🛠️ **Seven signature tools** — Union Breaker, Guild Grapple, Rift Blade, Salvage Scanner, Survey Spade, Settler's Multitool and Executive Stamp.
- ⚙️ **Seven signature machines** — Quota Board, Navigation Beacon, Void Altar, Deconstructor, Atmosphere Processor, Community Workbench and Corporate Contract Terminal. The Atmosphere Processor only converts natural ground and checks every target through the loader's block-break event, so claim and protection mods can refuse it.
- 🏪 **The Faction Trade Terminal** — members sell their faction its speciality goods for emeralds (and earn standing doing it), buy a curated vanilla catalogue at tier-scaled rates, and pick up their faction's banner from Member tier. A terminal can be bound to one faction.
- 🔒 **Gated recipes** — a server-authoritative `nerofactions:gated` recipe wrapper any datapack can use. Fail-closed: below the tier, the grid simply produces nothing.
- 🏆 **Tier rewards and cosmetics** — one-time rewards per tier, plus Inner Circle loom patterns and faction armour trims.
- 🚀 **Progression composition** — reaching a faction's Inner Circle opens a Neroland Core progression gate, era-matched to Core's Earth→space arc.
- 🔗 **Optional integrations, zero hard dependencies** — with **NeroQuests**, a 47-quest "Standing Among the Stars" chapter, quest reputation and tier gates; with **NeroEconomy**, capped market discounts and surcharges, faction treasuries, a credit counter and contract credits. Without them, combat, contracts and trading keep standing fully earnable.
- 📱 **Companion-app ready** — a NeroLink module: your own standings, memberships, contracts, gear unlocks and outpost visits, tier-change and contract-completion alerts, and a remote daily contract reroll. Never anyone else's data.
- 🧱 **Cross-loader** — 27 builds: NeoForge, Forge and Fabric on Minecraft **1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1.2, 26.2 and 26.3**.

**Honest scope notes:** faction territory, claim-API integration, the Void Altar blink rite, unattended Atmosphere Processor conversion and Space Guild launch-fuel discounts are not in this release. The full list is in the [changelog](https://github.com/Neroland/nerofactions/blob/main/CHANGELOG.md).

## Privacy (POPIA / GDPR)

Faction data is stored **inside your world save, keyed by player UUID only** — no names, no IPs, no chat, and **no action logs of any kind**. Every player can export their complete record in-game (`/nerofactions data export`) and erase it through Neroland Core's shared erasure hook (`/neroland data eraseme` — one command clears every Neroland mod, covered by an automated conformance test). An automatic retention sweep purges long-inactive players' data. Full details: [PRIVACY.md](https://github.com/Neroland/nerofactions/blob/main/PRIVACY.md).

## Requirements & compatibility

- **Requires [Neroland Core](https://nerolandmc.net/mods/neroland-core/) 1.17.5 or newer** — install it alongside NeroFactions (it loads first). NeroFactions refuses to load against an older Core.
- Java 25 for Minecraft 26.x; Java 21 for Minecraft 1.21.x.
- **NeroQuests** and **NeroEconomy** are optional and detected automatically. Install NeroFactions on both client and server for the full experience.
- **Modpacks are allowed and encouraged** — any platform, no need to ask. Use the official files and credit *NeroFactions by Neroland* with links to the [CurseForge page](https://www.curseforge.com/minecraft/mc-mods/nerofactions) and the [GitHub repository](https://github.com/Neroland/nerofactions). Full terms: [LICENSE](https://github.com/Neroland/nerofactions/blob/main/LICENSE).

## Links

- 📖 **[Wiki](https://github.com/Neroland/nerofactions/wiki)** — every faction, tier, command, config key and format documented.
- 💬 **[Discord](https://discord.gg/ArPXvYUzJG)** — chat, help, and sneak peeks.
- 🐞 **[Issues](https://github.com/Neroland/nerofactions/issues)** — bug reports and feature requests.
- 🗒️ **[Changelog](https://github.com/Neroland/nerofactions/blob/main/CHANGELOG.md)**
- 🔥 **[NeroFactions on CurseForge](https://www.curseforge.com/minecraft/mc-mods/nerofactions)**

---

> **Telemetry notice:** NeroFactions sends anonymous error reports (stack trace + mod/game
> versions only — never IPs, usernames, UUIDs, or world data) to the developers via Sentry
> (EU servers) so crashes can be fixed. On by default — opt out any time by setting `telemetryEnabled = false`
> in `config/nerofactions.properties`. Full details:
> [PRIVACY.md](https://github.com/Neroland/nerofactions/blob/main/PRIVACY.md).

*Created by Neroland. The project logo was made with the help of AI image tools.*
