# Privacy

The practical version of what NeroFactions stores about players and the rights you have
over it. The formal POPIA/GDPR and telemetry disclosure is
[PRIVACY.md](https://github.com/Neroland/nerofactions/blob/main/PRIVACY.md) in the
repository — that page is authoritative; this one is the tour.

## What is stored

NeroFactions keeps seven persistent stores **inside the world save**, keyed by player UUID
only — no names, no IPs, no player coordinates, no chat: reputation, membership, contracts,
Miner Union quota, outpost visits, Community Workbench access and the Corporate ledger.
Contracts and quotas are described in their own sections below.

- **Reputation** — your UUID mapped to an integer standing per faction. A standing of zero
  is not stored at all: a player at neutral and a player the mod has never seen are
  identical on disk.
- **Membership bookkeeping** — the faction(s) you belong to with join timestamps, the time
  you left any faction whose standing is still decaying, your join-cooldown end, per-day
  earning counters (for the daily caps), and the per-faction reward watermark (the highest
  tier whose one-time rewards you already received, so they can never pay twice). Each
  timestamp exists to make one gameplay rule computable and self-deletes when its purpose
  lapses.
- **Outpost visits** — the faction and dimension of an outpost you entered,
  recorded once per pair. Servers can stop recording with `outpostVisitsEnabled=false`.
- **Community Workbench access** — the bench's random ID, owner UUID and up to 16
  invited player UUIDs. The record contains no position. Erasing an owner removes
  the bench's whole access grant: guests lose access and the bench becomes ownerless,
  while its stored items stay in the block. An operator can hand an ownerless bench to
  a Free Colonists member with `/nerofactions workbench recover <x> <y> <z> <player>`.
  Erasing a guest removes their access. Export includes only your owned and invited bench IDs.
- **Corporate limits** — current world day, premium delivery completion and
  stamped writ count. A newer day expires old counters. Export and erasure
  include the counters. The writ itself holds no player identity.
- **Void Altar boons** — active boon timers live in server memory and are cleared
  on expiry, logout or erasure. The export reports the remaining ticks while you are
  online and still eligible for the boon, and 0 otherwise.
- **Session state** — the terminal faction you selected, the terminal you last used,
  companion reroll request IDs and your Deconstructor cooldown are held in server memory
  only. The last-used terminal is forgotten when you log out; all of it is cleared by
  erasure and on restart. The game keeps a short-lived last-attacker reference when a Guard
  defends a resident. These records are separate from crash telemetry.
- **Navigation Beacon** — binding a compass writes a lodestone-style target (the beacon's
  dimension and position) into your own compass item. You trigger it; the mod keeps no record.

Neroland Core's crash-recovery system keeps a last-known-good backup of the same stores,
also inside the world save — and erasure refreshes those backups too.

## No action logging

NeroFactions keeps **no log of player actions** — trades, joins, leaves and reputation
changes are not written to any log, and no log line in the mod carries a player name or
UUID. There is therefore nothing to opt out of; if a future version ever adds action
logging, it will ship with a per-player opt-out in the same release.

## Your data, in-game

- **Export** — `/nerofactions data export` hands you your complete NeroFactions record
  (every store above, plus any remaining Void Altar boon time) as JSON in chat with a click-to-copy component; nothing touches disk. Operators can run
  `/nerofactions data export <player>` (name or UUID) to answer an access request from a
  departed player.
- **Erasure** — Neroland Core's shared erasure hook covers this mod: `/neroland data
  eraseme` (any player, own data) or `/neroland data erase <uuid>` (operator) purges all
  seven stores and their recovery backups, plus the in-memory state above, in one request, alongside your data in every other Neroland mod. Erasure leaves **no
  tombstone** — an erased player who returns simply starts factionless at neutral standing
  (which also clears negative standing and restores one-time reward eligibility; both are
  accepted consequences of a clean erasure). Each store is erased independently, so a
  problem with one does not stop the others. The erasure path is covered by an automated
  conformance test built on Core's erasure-conformance harness.
- **Retention** — NeroFactions runs its own daily inactivity sweep: players who have not
  logged in for `retentionDays` (default **365**, `0` disables) are purged from every store
  automatically
  through the same erasure path, measured against Core's shared last-seen record so no new
  personal data is created. Core's opt-in ecosystem-wide sweep
  (`/neroland data purge-inactive`) reaches this mod too.

## What other mods and apps can see

- Tier changes are announced on Core's in-process event bus naming **the faction and the
  tier, never a player**; nothing on that bus leaves the game process.
- With NeroEconomy installed, your standing is read in memory at the moment a price is
  quoted — nothing new is stored and nothing is logged.
- Treasury dues and payouts and credit-counter purchases are booked on NeroEconomy's ledger
  (exported and erased with your economy data). Treasuries are keyed by faction, never by a
  player. A market sale is used once to award trade reputation and is not kept; the buyer is
  never known.
- A paired companion app can read **your own** standing, membership, contracts, gear and
  outpost visits, receive your tier-change and contract-completion events, and spend your
  daily contract reroll through the [link module](Link-Module).

## Crash telemetry

NeroFactions ships anonymous, NeroFactions-only crash reporting (Sentry,
EU servers): stack trace and version facts only — never IPs, usernames, UUIDs, world data,
faction membership or reputation values. It is **on by default** and opt-out via
`telemetryEnabled=false` in `config/nerofactions.properties`; nothing is sent while
disabled, and nothing is ever sent before your config choice has loaded. Full details in
[PRIVACY.md](https://github.com/Neroland/nerofactions/blob/main/PRIVACY.md).

## In-game standing sync

The server sends the connected player their own faction standings, memberships, tiers, cooldown and contracts,
plus the faction catalogue needed to display them. This snapshot contains no player identifier or
coordinates. The client keeps it in memory and clears it when disconnecting. It is not additional
persistent storage or telemetry.

## Contract state

The server stores today's assignments, progress, claimed markers, daily reroll-token state, a
high-water day and a completion count per player. No visit coordinates are stored. Offline
assignments expire at the server day boundary; inactive rows follow `retentionDays`. Export and
erasure cover the store and its recovery backup. Board snapshots contain only the receiving
player's contracts and are cleared from client memory on disconnect or erasure.

## Miner Union quotas

Quota Board deposits store the player's UUID, the last deposit's week number,
and the tonnes accepted for that week. The record is shared across all boards.
It contains no item history or board coordinates. Export includes the quota.
Core erasure removes the row and refreshes the recovery backup. Inactive
profiles follow the server's `retentionDays` setting.
