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
import za.co.neroland.nerolandcore.reputation.ReputationProvider;

/**
 * The persistent player↔faction reputation store — NeroFactions' implementation of Core's
 * {@link ReputationProvider} contract, stored on the overworld so it is always loaded (the same
 * {@link SavedDataType} codec pattern Core's {@code ProgressionState} uses).
 *
 * <p><b>Canonical zero.</b> A standing of {@code 0} is never stored: writing {@code 0} removes the
 * entry (and the player's whole row once it empties), so "absent" and "neutral" stay one and the
 * same. That keeps erasure provable — an erased player is indistinguishable from a player the mod
 * has never seen — and keeps the store from accreting rows for every player anything ever glanced
 * at through {@code ReputationApi}.
 *
 * <p><b>Privacy (POPIA/GDPR).</b> Rows are keyed by player UUID and hold only per-faction integer
 * standings — no names, no coordinates, no chat, no timestamps. Erasure enters through
 * {@link #forgetPlayer(UUID)} (Core drives it via the bound provider) and through
 * {@link #eraseFor(MinecraftServer, UUID)} (NeroFactions' own registered eraser, which also
 * refreshes the {@link SavedDataRecovery} backup — that backup is a second copy of the same
 * player-keyed rows and an erasure request must reach it in the same request).
 */
public final class FactionReputationState extends SavedData implements ReputationProvider {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(NeroFactionsCommon.MOD_ID, "reputation");

    /** DFU-free: the codec below is the whole format, no DataFixTypes. */
    public static final SavedDataType<FactionReputationState> TYPE =
            new SavedDataType<>(ID, FactionReputationState::new, codec(), null);

    /** player UUID → (faction id string → non-zero standing). */
    private final Map<UUID, Map<String, Integer>> players = new LinkedHashMap<>();

    public FactionReputationState() {
    }

    /**
     * The one store, on the overworld so it is always loaded. Routed through
     * {@link SavedDataRecovery} so a corrupt {@code nerofactions:reputation} file degrades to the
     * last-known-good backup (or a fresh store) instead of crashing the tick loop on every
     * reputation check.
     */
    public static FactionReputationState get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, FactionReputationState::new, ID.toString());
    }

    /**
     * POPIA/GDPR erasure entry point registered with
     * {@link za.co.neroland.nerolandcore.data.PlayerDataErasure} by {@code NeroFactionsData}: purge
     * the player's standings and push the anonymised state to the recovery backup in the same
     * request, so the erased rows do not survive in the backup file until the next periodic pass.
     * (Core's own provider eraser also reaches this store via the bound provider's
     * {@code forgetPlayer}; whichever route runs second finds the row already gone and does
     * nothing, so one erase request does the removal and the backup refresh exactly once.)
     */
    public static void eraseFor(MinecraftServer server, UUID player) {
        FactionReputationState state = get(server);
        state.forgetPlayer(player);
        SavedDataRecovery.backupNow(server.overworld(), TYPE, state, ID.toString());
    }

    @Override
    public synchronized int getReputation(UUID player, Identifier faction) {
        if (player == null || faction == null) {
            return 0;
        }
        Map<String, Integer> row = players.get(player);
        if (row == null) {
            return 0;
        }
        Integer value = row.get(faction.toString());
        return value == null ? 0 : value;
    }

    @Override
    public synchronized void setReputation(UUID player, Identifier faction, int value) {
        if (player == null || faction == null) {
            return;
        }
        String key = faction.toString();
        if (value == 0) {
            // Zeros are kept OUT of the map (see the class javadoc): remove rather than store.
            Map<String, Integer> row = players.get(player);
            if (row != null && row.remove(key) != null) {
                if (row.isEmpty()) {
                    players.remove(player);
                }
                setDirty();
            }
            return;
        }
        Map<String, Integer> row = players.computeIfAbsent(player, ignored -> new LinkedHashMap<>());
        Integer before = row.put(key, value);
        if (before == null || before != value) {
            setDirty();
        }
    }

    /**
     * POPIA/GDPR erasure: drop every standing stored for the player.
     *
     * <p><b>No tombstone — deliberately, unlike NeroEconomy.</b> The economy keeps a
     * salted-pseudonym tombstone because erasure there would otherwise <em>create value</em>: the
     * very next balance read re-opens the account and re-mints the starting balance. Reputation's
     * baseline for an unknown player is {@code 0} and nothing is minted on first read, so an
     * erased player who returns simply starts as an Outsider again. A tombstone here would be a
     * persisted artifact derived from the erased identity with no value-protection benefit —
     * strictly worse under data-minimisation (GDPR Art. 5(1)(c) / POPIA s10). The honest
     * consequence: erasure also clears <em>negative</em> standing, so an erased player sheds any
     * hostility they had earned. That is an accepted cost of the right to erasure, the same
     * posture Core's own progression erasure takes (an erased player's opened gates are gone too).
     */
    @Override
    public synchronized void forgetPlayer(UUID player) {
        if (player != null && players.remove(player) != null) {
            setDirty();
        }
    }

    // --- package-private seams (tests, retention sweep, DSAR export — no reflection) -----------

    synchronized int rowCount() {
        return players.size();
    }

    synchronized boolean hasRow(UUID player) {
        return players.containsKey(player);
    }

    /** Every player with any stored standing — the retention sweep's candidate list. A copy. */
    synchronized Set<UUID> knownPlayers() {
        return new LinkedHashSet<>(players.keySet());
    }

    /**
     * The player's whole row (faction id string → standing) for the DSAR export
     * ({@link PlayerDataExport}) — a copy, empty for an unknown player, never logged.
     */
    synchronized Map<String, Integer> standingsOf(UUID player) {
        Map<String, Integer> row = players.get(player);
        return row == null ? Map.of() : new LinkedHashMap<>(row);
    }

    // --- persistence ----------------------------------------------------------------------------

    private record Standing(String faction, int value) {
        static final Codec<Standing> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("faction").forGetter(Standing::faction),
                Codec.INT.fieldOf("value").forGetter(Standing::value)
        ).apply(inst, Standing::new));
    }

    private record Row(String player, List<Standing> standings) {
        static final Codec<Row> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("player").forGetter(Row::player),
                Standing.CODEC.listOf().fieldOf("standings").forGetter(Row::standings)
        ).apply(inst, Row::new));
    }

    private static Codec<FactionReputationState> codec() {
        return RecordCodecBuilder.create(inst -> inst.group(
                Row.CODEC.listOf().optionalFieldOf("players", List.of())
                        .forGetter(FactionReputationState::rows)
        ).apply(inst, FactionReputationState::fromData));
    }

    private synchronized List<Row> rows() {
        List<Row> out = new ArrayList<>();
        players.forEach((uuid, standings) -> {
            List<Standing> list = new ArrayList<>();
            standings.forEach((faction, value) -> list.add(new Standing(faction, value)));
            out.add(new Row(uuid.toString(), list));
        });
        return out;
    }

    private static FactionReputationState fromData(List<Row> rows) {
        FactionReputationState state = new FactionReputationState();
        for (Row row : rows) {
            UUID player;
            try {
                player = UUID.fromString(row.player());
            } catch (IllegalArgumentException ignored) {
                continue; // skip malformed UUID rows rather than fail the whole store
            }
            Map<String, Integer> standings = new LinkedHashMap<>();
            for (Standing standing : row.standings()) {
                // Re-validate on load: a hand-edited file must not smuggle in a malformed faction
                // id or an explicit zero (absent==0 is canonical, see the class javadoc).
                if (standing.value() != 0 && Identifier.tryParse(standing.faction()) != null) {
                    standings.put(standing.faction(), standing.value());
                }
            }
            if (!standings.isEmpty()) {
                state.players.put(player, standings);
            }
        }
        return state;
    }
}
