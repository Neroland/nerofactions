# NeroFactions

**Pick a side — seven joinable factions, tracked reputation and long-term choices that reshape a whole playthrough of the Neroland universe.**

NeroFactions is the **reputation & factions** mod of the Neroland ecosystem. Seven opinionated factions — the Space Guild, the Miner Union, the Nero Corporation, the Void Cult, the Terraforming Authority, the Free Colonists and the Salvagers — each bring their own quests, machines, gear, trade terms and rivals. Reputation toward each faction is tracked per player and decides what content unlocks, so a Void Cult run progresses, trades and fights nothing like a Space Guild one. Joining is a long-term commitment, not a menu pick — it is the ecosystem's primary reason to start again with a different allegiance.

Built on **Neroland Core**, so its shared reputation API, progression gates, base machine and upgrade framework, data-erasure hook and `c:` material tags are shared with the rest of the lineup. *(Planned — in design; not yet released.)*

---

## The seven factions

1. **The Space Guild.** The establishment of spaceflight — survey runs, station building and cargo escorts. Navigation beacons, docking computers and launch-assist blocks cut travel costs; high-tier flight suits unlock around `reached_orbit`. Rivals: the Salvagers and the Void Cult.
2. **The Miner Union.** Organised labour for extraction — depth-record contracts and tonnage quotas. Heavy drills, ore processors and a quota board that pays for output; mining exosuits and blast-resistant armour. Rival: the Nero Corporation.
3. **The Nero Corporation.** Corporate capital — profit targets, automated factories, contract terminals and a market-manipulation block. Sleek powered armour and dividends for the loyal. Rivals: the Miner Union and the Free Colonists.
4. **The Void Cult.** Worshippers of the alien dark — rituals at alien ruins, void altars, corruption spreaders and Void Crystal reactors that trade safety for power. Mutating void armour, high risk and reward. Rivals: the Space Guild and the Terraforming Authority.
5. **The Terraforming Authority.** The science of making dead worlds livable — atmosphere processors, soil converters and greenhouse domes. Life-support armour and terraforming blueprints. Rival: the Void Cult.
6. **The Free Colonists.** Independent settlers — modular homesteads, community workbenches and self-sufficiency generators. Versatile settler gear and shared community buffs. Rival: the Nero Corporation.
7. **The Salvagers.** Scavengers of the wreck and the ruin — scrap processors, deconstructors and a salvage scanner. Patchwork armour, scrap multipliers and rare recovered blueprints. Rival: the Space Guild.

## How allegiance works

1. **Reputation is the currency of belonging.** Every player holds a reputation value toward each faction, stored and modified through Core's reputation API. It maps to named tiers — Outsider → Associate → Member → Trusted → Inner Circle — and each tier opens gear, recipes, machines, trade terms and cosmetics.
2. **Earn it, or lose it.** Reputation is earned by completing faction quests, trading, fighting rivals and surviving faction events, and lost by aiding enemies. A single primary allegiance at a time (configurable), with a join cooldown to discourage churn.
3. **Unlock gating.** Faction-only machines, recipes and gear sit behind server-side reputation-tier checks, layered on Core's progression gates so factional unlocks compose with the Earth → space arc — a locked recipe simply will not resolve, a locked machine refuses to run.
4. **Enemy relationships.** Each faction lists rivals; gaining standing with one bleeds standing with its enemies, enemy NPCs turn hostile, and enemy traders refuse or surcharge. This is what makes allegiance a real choice, not collect-them-all.
5. **Datapack-defined.** A faction is a datapack object — id, theme, tier thresholds, reward tables, enemy list, trade modifiers, quests and machines — so packs can re-theme, rebalance or add factions without forking.

## Shared mechanics

- 🎯 **Unique quest lines** — each faction grants its own quests through **NeroQuests** when present; without it, factions fall back to internal kill/gather/deliver triggers so progression still works.
- 🏭 **Faction-only machines** — block-entities on Core's base machine and upgrade-module framework, placement and operation gated by reputation tier, I/O exposed on Core's compat tags.
- 💰 **Trade discounts** — per-faction, per-tier price modifiers applied at faction trade points via **NeroEconomy** and faction wallets when present; vanilla-style barter tables when absent, with a configurable cap.
- 🛡️ **Locked gear** — armour, tools and weapons gated by a `nerofactions:reputation_tier` recipe condition and server-authoritative equip checks.
- 🏆 **Tier rewards** — tier-keyed reward tables grant items, recipes, machine access, abilities and cosmetics; idempotent and re-granted safely on relog, since the server-stored value is the source of truth.
- 🚩 **Cosmetic banners and armour** — purely visual faction livery via banner patterns and armour trims, unlocked by tier, so allegiance is visible on a server with no balance impact.
- ⚔️ **Faction wars** — enemy hostility works standalone; with **NeroEvents** present, faction wars, raids, escorts and cult invasions surface as scheduled server events.

## Privacy (POPIA / GDPR)

NeroFactions is a data-handling mod by nature — reputation and membership are keyed by **player UUID only** (never names, IPs, chat or location history beyond gameplay need). It **minimises what it stores**, keeps standing to a configurable **retention window** with cleanup hooks so operators can purge players inactive beyond a set period, and supports export, reset and erasure on request. Everything routes through **Core's shared data-erasure hook**, so one request clears your NeroFactions standing alongside every other Neroland mod. No player data lands in info-level logs; any action logging is minimised, time-limited and opt-out.

Optional, anonymous **crash telemetry** carries only version strings — mod / MC / loader / OS / Java — never IPs, usernames, UUIDs or world data, and is opt-out at any time.

## Why it fits the ecosystem

- 🧩 **Built on Neroland Core** — one reputation system, one progression arc, one base machine and upgrade framework, and shared `c:` tags. NeroFactions ships in its own creative tab.
- 🔗 **Interoperates, never hard-depends** — it lights up with **NeroQuests** (quest lines), **NeroEconomy** (wallets and pricing) and **NeroEvents** (faction wars) when present, and degrades gracefully when they are absent, still playing as a full faction system on Core alone.
- 🌌 **Ecosystem-aware content** — factions align with Nerospace and Ad Astra space travel, NeroColonies settlements, NeroRuins wreck and alien sites, and Nerotech / NeroPower machines through Core tags, with no hard dependency on any of them.
- 🧱 **Cross-loader** — NeoForge, Forge and Fabric on Minecraft **26.1.2** and **26.2**.

## Requirements & compatibility

- **Requires [Neroland Core](https://modrinth.com/mod/nerolandcore)** — install it alongside NeroFactions (it loads first). NeroFactions is built entirely on Core's reputation, currency, progression-gate, config and machine APIs and will not load without it.
- Conventional `c:` tags on materials and loader-native item/energy capabilities on every machine face, so Create, AE2, Mekanism, Ad Astra and Energized Power interoperate as the 26.x ecosystem fills in — no hard dependency on any of them.
- **Modpacks are allowed and encouraged** — any platform, no need to ask. Use the official files and credit *NeroFactions by Neroland* with links to this page and the [GitHub repository](https://github.com/Neroland/nerofactions). Full terms: [LICENSE](https://github.com/Neroland/nerofactions/blob/main/LICENSE).

## Links

- 📖 **[Wiki](https://github.com/Neroland/nerofactions/wiki)** — every faction, tier and system documented.
- 💬 **[Discord](https://discord.gg/ArPXvYUzJG)** — chat, help, and sneak peeks.
- 🐞 **[Issues](https://github.com/Neroland/nerofactions/issues)** — bug reports and feature requests.
- 🗒️ **[Changelog](https://github.com/Neroland/nerofactions/blob/main/CHANGELOG.md)**
- 🟢 **[Also on Modrinth](https://modrinth.com/mod/nerofactions)**

---

*Created by Neroland. The project logo was made with the help of AI image tools; in-game art is generated by the project's own tooling and refined by hand.*
