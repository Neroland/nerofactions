# Privacy

The practical version of what NeroFactions stores about players and the rights you have
over it. The formal POPIA/GDPR and telemetry disclosure is
[PRIVACY.md](https://github.com/Neroland/nerofactions/blob/main/PRIVACY.md) in the
repository — that page is authoritative; this one is the tour.

## What is stored

Everything lives **inside the world save**, keyed by player UUID only — no names, no IPs,
no coordinates, no chat:

- **Reputation** — your UUID mapped to an integer standing per faction. A standing of zero
  is not stored at all: a player at neutral and a player the mod has never seen are
  identical on disk.
- **Membership bookkeeping** — the faction(s) you belong to with join timestamps, the time
  you left any faction whose standing is still decaying, your join-cooldown end, per-day
  earning counters (for the daily caps), and the per-faction reward watermark (the highest
  tier whose one-time rewards you already received, so they can never pay twice). Each
  timestamp exists to make one gameplay rule computable and self-deletes when its purpose
  lapses.
- **Not stored:** the trade terminal's remembered faction selection lives only in server
  memory for the session; nothing is ever sent to the developers or any external service.

Neroland Core's crash-recovery system keeps a last-known-good backup of the same stores,
also inside the world save — and erasure refreshes those backups too.

## No action logging

NeroFactions keeps **no log of player actions** — trades, joins, leaves and reputation
changes are not written to any log, and no log line in the mod carries a player name or
UUID. There is therefore nothing to opt out of; if a future version ever adds action
logging, it will ship with a per-player opt-out in the same release.

## Your data, in-game

- **Export** — `/nerofactions data export` hands you your complete NeroFactions record as
  JSON in chat with a click-to-copy component; nothing touches disk. Operators can run
  `/nerofactions data export <player>` (name or UUID) to answer an access request from a
  departed player.
- **Erasure** — Neroland Core's shared erasure hook covers this mod: `/neroland data
  eraseme` (any player, own data) or `/neroland data erase <uuid>` (operator) purges your
  standing, all membership bookkeeping, the recovery backups and the terminal session in
  one request, alongside your data in every other Neroland mod. Erasure leaves **no
  tombstone** — an erased player who returns simply starts factionless at neutral standing
  (which also clears negative standing and restores one-time reward eligibility; both are
  accepted consequences of a clean erasure). This wiring is machine-verified by Core's
  erasure-conformance harness on every build.
- **Retention** — NeroFactions runs its own daily inactivity sweep: players who have not
  logged in for `retentionDays` (default **365**, `0` disables) are purged automatically
  through the same erasure path, measured against Core's shared last-seen record so no new
  personal data is created. Core's opt-in ecosystem-wide sweep
  (`/neroland data purge-inactive`) reaches this mod too.

## What other mods and apps can see

- Tier changes are announced on Core's in-process event bus naming **the faction and the
  tier, never a player**; nothing on that bus leaves the game process.
- With NeroEconomy installed, your standing is read in memory at the moment a price is
  quoted — nothing new is stored and nothing is logged.
- A paired companion app can read **your own** record through the read-only
  [link module](Link-Module) — never anyone else's, and it can change nothing.

## Crash telemetry

NeroFactions is wired for optional, anonymous, NeroFactions-only crash reporting (Sentry,
EU servers): stack trace and version facts only — never IPs, usernames, UUIDs, world data,
faction membership or reputation values. It is opt-out via `telemetryEnabled=false` in
`config/nerofactions.properties`. **Current builds ship a placeholder reporting key, so
nothing is ever sent, ever** — the system only becomes active if a future release
configures a real key, and the same opt-out will govern it then. Full details in
[PRIVACY.md](https://github.com/Neroland/nerofactions/blob/main/PRIVACY.md).
