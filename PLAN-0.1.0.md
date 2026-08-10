# NeroFactions 0.1.0 — runtime verification & release readiness

> **Status 2026-08-08:** Stages 0–7 are implemented and green — all six cells build
> (`:{loader}:{mc}:build`) and the common test suite (including Core's erasure-conformance
> harness) passes on both neoforge test nodes. Docs, wiki and store copy are written and the
> version is bumped to **0.1.0-beta.1**. **Everything below is compile/test-verified only:
> the agent could not launch the game, so NONE of these runtime checks has been performed
> yet.** Nothing ships until this stage passes.

## Prerequisites

- [ ] **Release Neroland Core 1.11.0 first.** This repo pins `nerolandcore_version=1.11.0`,
      which today resolves **from Maven Local only** — CI and any clean machine cannot
      build until Core 1.11.0 is published to GitHub Packages. (For purely local testing,
      `./gradlew publishToMavenLocal` in the Core repo is enough.)
- [ ] Build the cells to test with:
      `./gradlew :neoforge:26.2:build :fabric:26.1.2:build :forge:26.2:build`
      (primary + the accesswidener path + the Forge channel path).
- [ ] Install into a dev/test instance: Neroland Core 1.11.0 + the matching NeroFactions
      jar. Keep NeroQuests and NeroEconomy jars at hand for checks 3–5.
- [ ] For time-based checks (decay, retention), be ready to change the **system clock** or
      accept editing config to small values — both mods measure **real time**, not game
      ticks.

## Runtime checks

Run on `:neoforge:26.2` primarily; spot-check single-player load on `:fabric:26.1.2` and
`:forge:26.2`.

### 1. Single-player load

Create a new world.
Expect in the log: `[NeroFactions] common init`, no errors or warnings from
`za.co.neroland.nerofactions`, and `config/nerofactions.properties` created with every key
documented in `wiki/Configuration.md`. `/nerofactions factions` lists **7** factions.
`/nerofactions reload-check` reports `7 faction(s) loaded` and no validation issues.

### 2. Dedicated server load

Start a dedicated server with Core + NeroFactions; connect with a client that has both.
Expect: clean load, `/nerofactions standing` answers (`You hold no standing with any
faction yet.`), and no client-side crash on opening the creative inventory (the terminal
sits in Core's shared **Neroland** tab).

### 3. Runtime configuration A — Core only

With neither NeroQuests nor NeroEconomy installed, expect BOTH log lines at init:

```text
[NeroFactions] NeroQuests absent - standing is earned through the internal combat trigger (and trade, once the trade terminal ships).
[NeroFactions] NeroEconomy absent - no faction pricing; standing still gates recipes and progression as normal.
```

(The quests-absent line's "once the trade terminal ships" wording is stale — the terminal
did ship — but that is the exact current string.)

### 4. Runtime configuration B — + NeroQuests

Add NeroQuests. Expect:

```text
[NeroFactions] NeroQuests present - quest rewards can pay reputation (neroquests:reputation via Core's ReputationApi) and quest packs can gate on tier crossings (nerofactions:reputation_tier).
```

### 5. Runtime configuration C — + NeroQuests + NeroEconomy

Add NeroEconomy too. Expect the quests-present line plus:

```text
[NeroFactions] NeroEconomy present - faction price modifier registered (speciality discounts / enemy surcharge, capped by config).
```

### 6. Join / leave / cooldown / penalty

1. `/nerofactions join space_guild` → "You have joined Space Guild."
2. `/nerofactions join miner_union` → refused: already belong to a faction
   (single-allegiance).
3. `/nerofactions leave space_guild` → "You have left Space Guild. Your standing there
   will fade over time." `/nerofactions standing` shows Space Guild at **−50** (switch
   penalty; earned nothing yet).
4. `/nerofactions join miner_union` immediately → refused: cooldown. Either wait 30 min,
   set `joinCooldownMinutes=0` + `/neroland config reload`, or roll the clock.

### 7. Standing & tier progression via admin grant

1. `/nerofactions join space_guild` (after the cooldown), then
   `/nerofactions admin grant <yourname> space_guild 150`.
2. Expect: a chat notice "Space Guild honours your new rank: Associate rewards received."
   plus 8 iron ingots **and** the Space Guild banner (blue, patterned, named "Space Guild
   Banner") in your inventory. `/nerofactions standing` → `Space Guild: 150 — Associate`.
3. Grant 900 more (→ 1050): expect **two** notices in order (Member, then Trusted) and both
   tiers' rewards (Member has no table for Space Guild — only Trusted pays: a spyglass + 4
   gold ingots). Verify the multi-boundary jump paid each tier exactly once.

### 8. Gated recipe locked → unlocked (spyglass: Trusted + orbit gate)

1. Below Trusted (reset first: `/nerofactions admin reset <yourname> space_guild`, then
   grant 500): put amethyst shard over copper ingot in a crafting grid → **no result**.
2. Grant to ≥1000 but leave the Core gate shut → still **no result** (the recipe also
   requires `nerolandcore:reached_orbit`).
3. Open the gate: `/neroland gate open "nerolandcore:reached_orbit"` → the same grid now
   yields a spyglass. Also confirm the recipe never appears in the recipe book, before or
   after.
4. Cross-check a standing-only recipe: with Miner Union standing ≥400
   (`admin grant <yourname> miner_union 400`), the discounted jukebox (8 planks around 1
   copper ingot) resolves with no gate involved.

### 9. Terminal trade, TRADE award, daily cap

1. Craft the terminal (3 iron / 1 emerald / 3 smooth stone — ungated), place it, and
   right-click **while not a member of any faction** → chat message telling you to join;
   no screen.
2. As a Space Guild member, right-click → the vanilla merchant screen titled "Space Guild",
   no villager level/XP bar. Verify the buy-side asks iron ingots for emeralds, the
   sell-side sells bread/torches (+ more at higher tiers), and at Member+ a "Space Guild
   Banner" for 3 emeralds.
3. Complete one speciality trade → `/nerofactions standing` shows **+1** (base 2 × trade
   weight 0.3, rounded). Repeat: after **100** trade points in one (real-time UTC) day,
   further trades award nothing until the day rolls.
4. With `allowMultipleFactions=true` and two memberships, sneak-right-click cycles
   factions with a "Terminal switched to …" message.

### 10. Banner reward & watermark no-regrant

1. Take a fresh faction (e.g. Free Colonists), grant to 100 → Associate reward: 12 bread +
   the Free Colonists banner.
2. `/nerofactions admin reset <yourname> free_colonists`, then grant to 100 again →
   standing and tier return, but **no second reward** and no notice (the watermark
   remembers). This is the release-critical idempotence check.

### 11. Decay after leaving (time skip)

1. As a member with, say, 400 standing, `/nerofactions leave <faction>` (standing drops by
   50 to 350).
2. Advance the system clock by 2 days (or wait) → `/nerofactions standing` shows **300**
   (25/day × 2, applied on read). Confirm it keeps stepping daily and stops at 0, after
   which the faction disappears from the export's `left` section.

### 12. Enemy bleed

With Space Guild membership, earn a **source** award (combat kill or terminal trade — not
an admin grant). Expect the award with Space Guild and **−round(award × 0.5)** with
Salvagers and Void Cult (its enemies). Then verify `/nerofactions admin grant … space_guild
100` moves **only** Space Guild — admin never bleeds.

### 13. Threshold event → NeroQuests quest

With NeroQuests installed, add a test quest datapack, e.g.
`data/verifytest/neroquests/quests/test/guild_member.json`:

```json
{
  "title": "quest.verifytest.guild_member.title",
  "description": "quest.verifytest.guild_member.desc",
  "icon": "minecraft:spyglass",
  "objectives": [
    {
      "type": "neroquests:custom_event",
      "channel": "nerofactions:reputation_tier",
      "event_scope": "nerofactions:space_guild",
      "direction": "rising",
      "min_value": 2,
      "audience": "everyone"
    }
  ],
  "rewards": [
    { "type": "neroquests:xp", "amount": 50 }
  ],
  "scope": "player"
}
```

Reset Space Guild standing below 400, then grant across the Member boundary → the quest
objective completes for the online player. (This proves the
`nerofactions:reputation_tier` channel end-to-end.)

### 14. Export before / after erasure, then erasure

1. `/nerofactions data export` → summary line + `[Copy to clipboard]`; the copied JSON
   contains `reputation.standings`, `membership.memberships`, `cooldown_until_ms`, `left`,
   `accrual` and `reward_watermarks` matching the state you built above.
2. `/neroland data eraseme` → then `/nerofactions standing` shows nothing,
   `/nerofactions factions` shows no member marker, and a fresh
   `/nerofactions data export` shows every section **empty** with `cooldown_until_ms: 0`.
3. Operator variant: rebuild some state, then `/neroland data erase <uuid>` from another
   operator — same result. Confirm no log line printed a name or UUID.

### 15. Retention sweep

Set `retentionDays=1` + `/neroland config reload`. Give a **second** account some standing,
then keep it logged out past 1 day (clock skip) while the server stays up (or restart —
the sweep also runs on world load). Expect one log line, anonymous count only:
`[NeroFactions] Retention sweep purged 1 inactive players' faction data.` — and that
player's export is empty on return. Your own (recently active) data must survive. Restore
`retentionDays=365` afterwards.

### 16. Link module (if the NeroLink bridge + app are available)

With NeroLink installed, pair the companion app and confirm: the `nerofactions` module
reports schema 1 with sections `standing` + `membership` and **no actions**; snapshots show
only the paired player's own rows; a tier crossing pushes a `standing` event whose payload
names faction + tier only. Then set `linkModuleEnabled=false`, restart, and confirm the
module is gone (log: "The NeroLink module is disabled by config…").

### 17. Paper-hybrid note

Hybrid servers (Paper/Arclight-style) are **unsupported** ecosystem-wide. The terminal's
menu opening is routed through the guarded `MenuOpener` seam, so on a hybrid that returns a
broken container id the interaction cancels with a polite message instead of crashing — if
one is at hand, that is worth a single click-test, but do not gate the release on it.

## Release readiness (after the checks pass)

- [x] ~~Create the NeroFactions Sentry project and set the real DSN~~ — done: the project
      DSN is compiled in and `PRIVACY.md`/wiki/store copy now describe telemetry as active
      with the opt-out. Still to do: set the `SENTRY_AUTH_TOKEN`-style repo secret if
      `publish.yml`'s Sentry-release step needs it.
- [ ] Confirm `publish.yml` carries the ecosystem conventions (CurseForge direct-curl
      upload, `max-parallel: 1`, 5xx retry loop; Modrinth v3 environment-metadata PATCH).
- [ ] Neroland Core **1.11.0 published** (prerequisite above) and resolvable from CI.
- [ ] Tag `v0.1.0-beta.1`. (The version bump in `gradle.properties` is already done.)
