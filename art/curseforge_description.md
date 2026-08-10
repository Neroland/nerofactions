# NeroFactions

**Pick a side — seven joinable factions, earned reputation, and unlocks that make allegiance a real long-term choice.**

NeroFactions is the **reputation & factions** mod of the Neroland ecosystem — and the lineup's shared reputation provider: every player's standing with every faction is tracked persistently through **Neroland Core**'s reputation API, where the whole ecosystem can read it. Standing climbs a five-tier ladder — Outsider → Associate → Member → Trusted → Inner Circle — and your tier is what unlocks recipes, trade rates, one-time rewards and progression gates. Joining is a commitment, not a menu pick: one faction at a time (by default), a cooldown and a standing penalty for switching, and standing that decays after you walk away.

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

## What ships in 0.1.0

- ⚖️ **A real reputation economy** — standing is earned through weighted, daily-capped sources (quests, combat in your faction's colours, trading), **bleeds to your faction's enemies** as you climb, and **decays** after you leave. Every knob is server config.
- 🏪 **The Faction Trade Terminal** — one shared block: members sell their faction its speciality goods for emeralds (and earn standing doing it), buy a curated vanilla catalogue at tier-scaled rates, and pick up their faction's banner from Member tier. Sneak-click cycles factions on multi-allegiance servers.
- 🔒 **Gated recipes** — a server-authoritative `nerofactions:gated` recipe wrapper any datapack can use, plus seven shipped faction perks (a Trusted spyglass discount, a craftable name tag, a salvage-cheap anvil, a craftable grass block and more). Fail-closed: below the tier, the grid simply produces nothing — no client tricks, no auto-crafter bypass.
- 🏆 **Tier rewards** — each faction's reward tables pay out exactly once, ever, per tier reached: themed vanilla goods plus the faction's pre-styled banner in its colours.
- 🚀 **Progression composition** — reaching a faction's Inner Circle opens a Neroland Core progression gate, era-matched to Core's Earth→space arc (industrial power, orbit, first colony).
- 🔗 **Optional integrations, zero hard dependencies** — with **NeroQuests**, quests can pay reputation and gate on tier crossings; with **NeroEconomy**, standing grants capped market discounts and enemy surcharges. Without them, combat and trading keep standing fully earnable.
- 📱 **Companion-app ready** — a read-only NeroLink module: your own standings and tier-change alerts on your phone, never anyone else's, and no remote actions — allegiance is decided in-world, always.
- 🧱 **Cross-loader** — NeoForge, Forge and Fabric on Minecraft **26.1.2** and **26.2**.

**Honest scope notes:** this release ships no custom textures (the terminal reuses vanilla art; cosmetics are pre-styled vanilla banners), no unique faction gear items, no per-faction machines and no custom GUI screens. The full list of deliberate cuts is in the [changelog](https://github.com/Neroland/nerofactions/blob/main/CHANGELOG.md).

## Privacy (POPIA / GDPR)

Reputation and membership are stored **inside your world save, keyed by player UUID only** — no names, no IPs, no chat, and **no action logs of any kind**. Every player can export their complete record in-game (`/nerofactions data export`) and erase it through Neroland Core's shared erasure hook (`/neroland data eraseme` — one command clears every Neroland mod, verified by an automated conformance test on every build). An automatic retention sweep purges long-inactive players' data. Full details: [PRIVACY.md](https://github.com/Neroland/nerofactions/blob/main/PRIVACY.md).

## Requirements & compatibility

- **Requires [Neroland Core](https://modrinth.com/mod/nerolandcore) 1.11.0 or newer** — install it alongside NeroFactions (it loads first). NeroFactions refuses to load against an older Core.
- **NeroQuests** and **NeroEconomy** are optional and detected automatically.
- **Modpacks are allowed and encouraged** — any platform, no need to ask. Use the official files and credit *NeroFactions by Neroland* with links to this page and the [GitHub repository](https://github.com/Neroland/nerofactions). Full terms: [LICENSE](https://github.com/Neroland/nerofactions/blob/main/LICENSE).

## Links

- 📖 **[Wiki](https://github.com/Neroland/nerofactions/wiki)** — every faction, tier, command, config key and format documented.
- 💬 **[Discord](https://discord.gg/ArPXvYUzJG)** — chat, help, and sneak peeks.
- 🐞 **[Issues](https://github.com/Neroland/nerofactions/issues)** — bug reports and feature requests.
- 🗒️ **[Changelog](https://github.com/Neroland/nerofactions/blob/main/CHANGELOG.md)**
- 🟢 **[Also on Modrinth](https://modrinth.com/mod/nerofactions)**

---

> **Telemetry notice:** NeroFactions sends anonymous error reports (stack trace + mod/game
> versions only — never IPs, usernames, UUIDs, or world data) to the developers via Sentry
> (EU servers) so crashes can be fixed. Opt out any time by setting `telemetryEnabled = false`
> in `config/nerofactions.properties`. Full details:
> [PRIVACY.md](https://github.com/Neroland/nerofactions/blob/main/PRIVACY.md).

*Created by Neroland. The project logo was made with the help of AI image tools; this release contains no other custom art — in-game visuals reuse vanilla Minecraft assets.*
