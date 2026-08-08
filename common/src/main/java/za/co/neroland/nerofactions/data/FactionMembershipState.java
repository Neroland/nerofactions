package za.co.neroland.nerofactions.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

/**
 * The persistent player↔faction <em>membership</em> store, the sibling of
 * {@link FactionReputationState} (same overworld residency, same {@link SavedDataRecovery} routing,
 * same canonical-emptiness discipline). Reputation answers "how does this faction feel about the
 * player"; this store answers "which faction(s) has the player pledged to, and what is the
 * bookkeeping around joining and leaving".
 *
 * <p>Per player it holds four sections, every one of which empties back to nothing:
 *
 * <ul>
 *   <li><b>memberships</b> — faction id → joined-at (epoch ms). A singleton map under the default
 *       single-allegiance rule; {@code allowMultipleFactions} merely permits more entries.</li>
 *   <li><b>cooldownUntil</b> — epoch ms before which the player may not join any faction again
 *       ({@code 0} = no cooldown). Started by leaving a faction.</li>
 *   <li><b>left</b> — faction id → left-at (epoch ms): the factions the player has walked away
 *       from, kept <em>only</em> while reputation decay still has work to do there. The decay pass
 *       removes an entry the moment standing reaches 0 (and rejoining removes it immediately), so
 *       this section is self-erasing bookkeeping, not a membership history.</li>
 *   <li><b>accrual</b> — faction id → source name → (day stamp, points accrued that day): the
 *       per-source daily-cap counters {@code ReputationSources} clamps against. Rows from an
 *       earlier day are dead weight and are replaced on the first write of a new day.</li>
 *   <li><b>rewarded</b> — faction id → the highest {@code FactionTier} ordinal whose tier rewards
 *       have ever been granted to this player (the {@code RewardGrants} <em>watermark</em>). This
 *       is deliberately a high-water mark, not a mirror of standing: a tier's rewards grant
 *       exactly once ever per player+faction, so decaying below a tier and re-earning it does
 *       <b>not</b> re-grant. Unlike the other sections it never self-erases (idempotency is its
 *       whole job); it is removed only by erasure.</li>
 * </ul>
 *
 * <p><b>Rows empty out.</b> A player whose sections are all empty is removed from the map
 * entirely, so — as with the reputation store — a player the mod has never seen and a player whose
 * faction life has fully wound down are indistinguishable on disk. (A granted-reward watermark
 * keeps a row alive by design — see above.)
 *
 * <p><b>Privacy (POPIA/GDPR).</b> Rows are keyed by player UUID and hold faction ids, epoch-ms
 * timestamps (joined/left/cooldown), integer daily counters and the granted-reward watermark — no
 * names, no coordinates, no chat. The timestamps exist solely to make cooldown and decay
 * computable and each one is deleted as soon as its purpose lapses (see above). Erasure enters through {@link #forgetPlayer(UUID)} and through
 * {@link #eraseFor(MinecraftServer, UUID)}, NeroFactions' registered eraser, which also refreshes
 * the {@link SavedDataRecovery} backup in the same request. Both stores' erasers are registered
 * with Core's {@code PlayerDataErasure}, so <b>one</b> request clears reputation, membership,
 * cooldown, decay bookkeeping and accrual counters together.
 */
public final class FactionMembershipState extends SavedData {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(NeroFactionsCommon.MOD_ID, "membership");

    /** DFU-free: the codec below is the whole format, no DataFixTypes. */
    public static final SavedDataType<FactionMembershipState> TYPE =
            new SavedDataType<>(ID, FactionMembershipState::new, codec(), null);

    /** player UUID → that player's membership row. Rows are pruned the moment they empty. */
    private final Map<UUID, PlayerRow> players = new LinkedHashMap<>();

    public FactionMembershipState() {
    }

    /**
     * The one store, on the overworld so it is always loaded, routed through
     * {@link SavedDataRecovery} so a corrupt {@code nerofactions:membership} file degrades to the
     * last-known-good backup instead of crashing every join/leave/decay pass.
     */
    public static FactionMembershipState get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, FactionMembershipState::new, ID.toString());
    }

    /**
     * POPIA/GDPR erasure entry point registered with Core's {@code PlayerDataErasure} by
     * {@code NeroFactionsData}: purge the player's whole row (memberships, cooldown, decay
     * bookkeeping, accrual counters, granted-reward watermark) and push the anonymised state to the recovery backup in the
     * same request, so the erased rows do not survive in the backup file until the next periodic
     * pass. Runs in the same fan-out as {@link FactionReputationState#eraseFor}, so one erase
     * request clears both stores.
     */
    public static void eraseFor(MinecraftServer server, UUID player) {
        FactionMembershipState state = get(server);
        state.forgetPlayer(player);
        SavedDataRecovery.backupNow(server.overworld(), TYPE, state, ID.toString());
    }

    // --- memberships ----------------------------------------------------------------------------

    /** The factions the player currently belongs to (empty for an unknown player). A copy. */
    public synchronized Set<Identifier> membershipsOf(UUID player) {
        PlayerRow row = players.get(player);
        if (row == null) {
            return Set.of();
        }
        Set<Identifier> out = new LinkedHashSet<>();
        for (String faction : row.memberships.keySet()) {
            Identifier id = Identifier.tryParse(faction);
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }

    public synchronized boolean isMember(UUID player, Identifier faction) {
        PlayerRow row = players.get(player);
        return row != null && faction != null && row.memberships.containsKey(faction.toString());
    }

    /** When the player joined this faction (epoch ms), or {@code 0} if they are not a member. */
    public synchronized long joinedAt(UUID player, Identifier faction) {
        PlayerRow row = players.get(player);
        if (row == null || faction == null) {
            return 0L;
        }
        Long joined = row.memberships.get(faction.toString());
        return joined == null ? 0L : joined;
    }

    /**
     * Records a join at {@code now}. Also removes any pending decay entry for the faction — a
     * member's standing must not decay, and a stale left-at would resume decaying it on the next
     * pass after they leave again anyway.
     */
    public synchronized void recordJoin(UUID player, Identifier faction, long now) {
        if (player == null || faction == null) {
            return;
        }
        PlayerRow row = players.computeIfAbsent(player, ignored -> new PlayerRow());
        row.memberships.put(faction.toString(), now);
        row.left.remove(faction.toString());
        setDirty();
    }

    /**
     * Records a leave at {@code now}: drops the membership, starts decay bookkeeping
     * ({@code leftAt = now}) and arms the join cooldown.
     *
     * @return {@code true} if the player was a member (false = nothing changed)
     */
    public synchronized boolean recordLeave(UUID player, Identifier faction, long now, long cooldownUntil) {
        PlayerRow row = players.get(player);
        if (row == null || faction == null || row.memberships.remove(faction.toString()) == null) {
            return false;
        }
        row.left.put(faction.toString(), now);
        row.cooldownUntil = Math.max(row.cooldownUntil, cooldownUntil);
        setDirty();
        return true;
    }

    // --- cooldown -------------------------------------------------------------------------------

    /** Epoch ms before which the player may not join a faction ({@code 0} = no cooldown). */
    public synchronized long cooldownUntil(UUID player) {
        PlayerRow row = players.get(player);
        return row == null ? 0L : row.cooldownUntil;
    }

    /**
     * Clears an elapsed cooldown so an otherwise-empty row can be pruned. Called opportunistically
     * from the membership layer; keeping a live cooldown is the one reason an otherwise-empty row
     * may persist.
     */
    public synchronized void clearCooldownIfElapsed(UUID player, long now) {
        PlayerRow row = players.get(player);
        if (row != null && row.cooldownUntil != 0L && row.cooldownUntil <= now) {
            row.cooldownUntil = 0L;
            pruneIfEmpty(player, row);
            setDirty();
        }
    }

    // --- decay bookkeeping ----------------------------------------------------------------------

    /** faction id → left-at epoch ms for every faction still pending decay. A copy. */
    public synchronized Map<Identifier, Long> leftFactions(UUID player) {
        PlayerRow row = players.get(player);
        if (row == null) {
            return Map.of();
        }
        Map<Identifier, Long> out = new LinkedHashMap<>();
        row.left.forEach((faction, leftAt) -> {
            Identifier id = Identifier.tryParse(faction);
            if (id != null) {
                out.put(id, leftAt);
            }
        });
        return out;
    }

    /** Every player with decay still pending — the tick pass's worklist. A copy. */
    public synchronized List<UUID> playersWithPendingDecay() {
        List<UUID> out = new ArrayList<>();
        players.forEach((player, row) -> {
            if (!row.left.isEmpty()) {
                out.add(player);
            }
        });
        return out;
    }

    /**
     * Advances the decay bookmark after a pass applied some whole days: moves {@code leftAt}
     * forward to {@code newLeftAt} (so the same days are never applied twice), or removes the entry
     * entirely when {@code done} (standing reached 0 — nothing left to decay).
     */
    public synchronized void advanceDecay(UUID player, Identifier faction, long newLeftAt, boolean done) {
        PlayerRow row = players.get(player);
        if (row == null || faction == null || !row.left.containsKey(faction.toString())) {
            return;
        }
        if (done) {
            row.left.remove(faction.toString());
            pruneIfEmpty(player, row);
        } else {
            row.left.put(faction.toString(), newLeftAt);
        }
        setDirty();
    }

    // --- per-source daily accrual ---------------------------------------------------------------

    /**
     * How many points the player has already accrued from {@code source} toward {@code faction} on
     * {@code dayStamp}. A row stamped with a different (older) day reads 0 — day-roll is implicit.
     */
    public synchronized int accruedToday(UUID player, Identifier faction, String source, long dayStamp) {
        PlayerRow row = players.get(player);
        if (row == null || faction == null || source == null) {
            return 0;
        }
        Map<String, DayAccrual> bySource = row.accrual.get(faction.toString());
        if (bySource == null) {
            return 0;
        }
        DayAccrual accrual = bySource.get(source);
        return accrual == null || accrual.dayStamp() != dayStamp ? 0 : accrual.accrued();
    }

    /**
     * Adds {@code amount} to the player's accrual counter for ({@code faction}, {@code source}) on
     * {@code dayStamp}, replacing any counter from an earlier day (the day-roll reset).
     */
    public synchronized void addAccrued(UUID player, Identifier faction, String source, long dayStamp, int amount) {
        if (player == null || faction == null || source == null || amount == 0) {
            return;
        }
        PlayerRow row = players.computeIfAbsent(player, ignored -> new PlayerRow());
        Map<String, DayAccrual> bySource =
                row.accrual.computeIfAbsent(faction.toString(), ignored -> new LinkedHashMap<>());
        DayAccrual previous = bySource.get(source);
        int base = previous != null && previous.dayStamp() == dayStamp ? previous.accrued() : 0;
        bySource.put(source, new DayAccrual(dayStamp, base + amount));
        setDirty();
    }

    // --- granted-reward watermark ---------------------------------------------------------------

    /**
     * The highest tier ordinal whose rewards this player has ever been granted for this faction
     * ({@code 0} = none; Outsider grants nothing and ordinal 0 never appears as a real watermark).
     */
    public synchronized int rewardWatermark(UUID player, Identifier faction) {
        PlayerRow row = players.get(player);
        if (row == null || faction == null) {
            return 0;
        }
        Integer mark = row.rewarded.get(faction.toString());
        return mark == null ? 0 : mark;
    }

    /**
     * Raises the watermark to {@code tierOrdinal} if that is higher than the current mark.
     * High-water only — a lower value never lowers it (re-crossing after decay must not re-grant).
     *
     * @return {@code true} if the watermark actually rose
     */
    public synchronized boolean raiseRewardWatermark(UUID player, Identifier faction, int tierOrdinal) {
        if (player == null || faction == null || tierOrdinal <= 0) {
            return false;
        }
        PlayerRow row = players.computeIfAbsent(player, ignored -> new PlayerRow());
        Integer current = row.rewarded.get(faction.toString());
        if (current != null && current >= tierOrdinal) {
            return false;
        }
        row.rewarded.put(faction.toString(), tierOrdinal);
        setDirty();
        return true;
    }

    // --- erasure --------------------------------------------------------------------------------

    /**
     * POPIA/GDPR erasure: drop the player's entire row — memberships, cooldown, decay bookkeeping,
     * accrual counters and the granted-reward watermark. Like the reputation store, no tombstone:
     * nothing derived from the erased identity survives, and a returning player simply starts
     * factionless with no cooldown (and, having no watermark, is eligible for tier rewards again —
     * the accepted price of a clean erasure).
     */
    public synchronized void forgetPlayer(UUID player) {
        if (player != null && players.remove(player) != null) {
            setDirty();
        }
    }

    private void pruneIfEmpty(UUID player, PlayerRow row) {
        if (row.memberships.isEmpty() && row.left.isEmpty() && row.accrual.isEmpty()
                && row.rewarded.isEmpty() && row.cooldownUntil == 0L) {
            players.remove(player);
        }
    }

    // --- package-private seams (tests, retention sweep, DSAR export — no reflection) -----------

    synchronized int rowCount() {
        return players.size();
    }

    synchronized boolean hasRow(UUID player) {
        return players.containsKey(player);
    }

    /** Every player with any stored section — the retention sweep's candidate list. A copy. */
    synchronized Set<UUID> knownPlayers() {
        return new LinkedHashSet<>(players.keySet());
    }

    /**
     * Everything stored for one player, as deep copies, for the DSAR export
     * ({@link PlayerDataExport}) — all sections empty (cooldown 0) for an unknown player. Never
     * logged.
     */
    synchronized ExportView exportOf(UUID player) {
        PlayerRow row = players.get(player);
        if (row == null) {
            return new ExportView(Map.of(), Map.of(), Map.of(), Map.of(), 0L);
        }
        Map<String, Map<String, DayAccrual>> accrual = new LinkedHashMap<>();
        row.accrual.forEach((faction, bySource) -> accrual.put(faction, new LinkedHashMap<>(bySource)));
        return new ExportView(new LinkedHashMap<>(row.memberships), new LinkedHashMap<>(row.left),
                accrual, new LinkedHashMap<>(row.rewarded), row.cooldownUntil);
    }

    /** A deep-copied snapshot of one player's row, in store units (epoch ms, tier ordinals). */
    record ExportView(Map<String, Long> memberships, Map<String, Long> left,
            Map<String, Map<String, DayAccrual>> accrual, Map<String, Integer> rewarded,
            long cooldownUntil) {
    }

    // --- in-memory row --------------------------------------------------------------------------

    private static final class PlayerRow {

        /** faction id string → joined-at epoch ms. */
        final Map<String, Long> memberships = new LinkedHashMap<>();

        /** faction id string → left-at epoch ms (pending decay only; self-erasing). */
        final Map<String, Long> left = new LinkedHashMap<>();

        /** faction id string → source name → today's accrual. */
        final Map<String, Map<String, DayAccrual>> accrual = new LinkedHashMap<>();

        /** faction id string → highest tier ordinal ever reward-granted (the high-water mark). */
        final Map<String, Integer> rewarded = new LinkedHashMap<>();

        long cooldownUntil;
    }

    /** Package-visible so {@link PlayerDataExport} can read the export snapshot's accrual rows. */
    record DayAccrual(long dayStamp, int accrued) {
        static final Codec<DayAccrual> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.LONG.fieldOf("day").forGetter(DayAccrual::dayStamp),
                Codec.INT.fieldOf("accrued").forGetter(DayAccrual::accrued)
        ).apply(inst, DayAccrual::new));
    }

    // --- persistence ----------------------------------------------------------------------------

    private record Stamp(String faction, long at) {
        static final Codec<Stamp> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("faction").forGetter(Stamp::faction),
                Codec.LONG.fieldOf("at").forGetter(Stamp::at)
        ).apply(inst, Stamp::new));
    }

    private record AccrualRow(String faction, String source, DayAccrual accrual) {
        static final Codec<AccrualRow> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("faction").forGetter(AccrualRow::faction),
                Codec.STRING.fieldOf("source").forGetter(AccrualRow::source),
                DayAccrual.CODEC.fieldOf("today").forGetter(AccrualRow::accrual)
        ).apply(inst, AccrualRow::new));
    }

    /** One granted-reward watermark: the highest tier ordinal ever granted for one faction. */
    private record Watermark(String faction, int tier) {
        static final Codec<Watermark> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("faction").forGetter(Watermark::faction),
                Codec.INT.fieldOf("tier").forGetter(Watermark::tier)
        ).apply(inst, Watermark::new));
    }

    private record Row(String player, List<Stamp> memberships, List<Stamp> left,
            List<AccrualRow> accrual, List<Watermark> rewarded, long cooldownUntil) {
        static final Codec<Row> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("player").forGetter(Row::player),
                Stamp.CODEC.listOf().optionalFieldOf("memberships", List.of()).forGetter(Row::memberships),
                Stamp.CODEC.listOf().optionalFieldOf("left", List.of()).forGetter(Row::left),
                AccrualRow.CODEC.listOf().optionalFieldOf("accrual", List.of()).forGetter(Row::accrual),
                Watermark.CODEC.listOf().optionalFieldOf("rewarded", List.of()).forGetter(Row::rewarded),
                Codec.LONG.optionalFieldOf("cooldown_until", 0L).forGetter(Row::cooldownUntil)
        ).apply(inst, Row::new));
    }

    private static Codec<FactionMembershipState> codec() {
        return RecordCodecBuilder.create(inst -> inst.group(
                Row.CODEC.listOf().optionalFieldOf("players", List.of())
                        .forGetter(FactionMembershipState::rows)
        ).apply(inst, FactionMembershipState::fromData));
    }

    private synchronized List<Row> rows() {
        List<Row> out = new ArrayList<>();
        players.forEach((uuid, row) -> {
            List<Stamp> memberships = new ArrayList<>();
            row.memberships.forEach((faction, at) -> memberships.add(new Stamp(faction, at)));
            List<Stamp> left = new ArrayList<>();
            row.left.forEach((faction, at) -> left.add(new Stamp(faction, at)));
            List<AccrualRow> accrual = new ArrayList<>();
            row.accrual.forEach((faction, bySource) ->
                    bySource.forEach((source, today) ->
                            accrual.add(new AccrualRow(faction, source, today))));
            List<Watermark> rewarded = new ArrayList<>();
            row.rewarded.forEach((faction, tier) -> rewarded.add(new Watermark(faction, tier)));
            out.add(new Row(uuid.toString(), memberships, left, accrual, rewarded, row.cooldownUntil));
        });
        return out;
    }

    private static FactionMembershipState fromData(List<Row> rows) {
        FactionMembershipState state = new FactionMembershipState();
        for (Row row : rows) {
            UUID player;
            try {
                player = UUID.fromString(row.player());
            } catch (IllegalArgumentException ignored) {
                continue; // skip malformed UUID rows rather than fail the whole store
            }
            PlayerRow built = new PlayerRow();
            // Re-validate on load: a hand-edited file must not smuggle in malformed faction ids.
            for (Stamp stamp : row.memberships()) {
                if (Identifier.tryParse(stamp.faction()) != null) {
                    built.memberships.put(stamp.faction(), stamp.at());
                }
            }
            for (Stamp stamp : row.left()) {
                // A faction the player belongs to cannot also be pending decay.
                if (Identifier.tryParse(stamp.faction()) != null
                        && !built.memberships.containsKey(stamp.faction())) {
                    built.left.put(stamp.faction(), stamp.at());
                }
            }
            for (AccrualRow accrual : row.accrual()) {
                if (Identifier.tryParse(accrual.faction()) != null && !accrual.source().isBlank()) {
                    built.accrual
                            .computeIfAbsent(accrual.faction(), ignored -> new LinkedHashMap<>())
                            .put(accrual.source(), accrual.accrual());
                }
            }
            for (Watermark mark : row.rewarded()) {
                // A watermark of 0 or less is "none" and never stored; clamp hand-edited excesses.
                if (Identifier.tryParse(mark.faction()) != null && mark.tier() > 0) {
                    built.rewarded.put(mark.faction(), mark.tier());
                }
            }
            built.cooldownUntil = Math.max(0L, row.cooldownUntil());
            if (!built.memberships.isEmpty() || !built.left.isEmpty() || !built.accrual.isEmpty()
                    || !built.rewarded.isEmpty() || built.cooldownUntil != 0L) {
                state.players.put(player, built);
            }
        }
        return state;
    }
}
