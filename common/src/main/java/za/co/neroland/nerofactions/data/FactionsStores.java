package za.co.neroland.nerofactions.data;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import za.co.neroland.nerofactions.content.faction.minerunion.QuotaState;
import za.co.neroland.nerofactions.content.faction.salvagers.DeconstructorBlock;
import za.co.neroland.nerofactions.content.faction.voidcult.VoidAltarBlock;
import za.co.neroland.nerofactions.link.FactionsLinkActions;
import za.co.neroland.nerofactions.trade.TerminalSessions;

/**
 * The <b>one</b> list of everything NeroFactions holds per player (POPIA/GDPR). Erasure
 * ({@link NeroFactionsData#eraseLocal}), the inactivity sweep ({@link RetentionSweep}) and the
 * access-request export ({@link PlayerDataExport}) all iterate this list, so a store cannot be
 * erased but not exported, or exported but not swept. {@code NeroFactionsErasureConformanceTest}
 * fails the build if a class that loads through {@code SavedDataRecovery.get} is missing here.
 *
 * <p>{@link #PERSISTENT}: the SavedData stores, each with its known-player roster, its per-player
 * forget, its export section and its live backup refresh. {@link #TRANSIENT}: in-memory,
 * UUID-keyed state that an erasure request must clear too.
 *
 * <p><b>Backup refresh.</b> Each store's {@code SavedDataType} is private to the store, so the live
 * refresh is the store's own {@code eraseFor(server, player)}: the row is already gone by then
 * (the forget is idempotent), and the call rewrites the {@code SavedDataRecovery} backup so the
 * erased row does not survive in the backup file.
 */
public final class FactionsStores {

    private FactionsStores() {
    }

    /** One persistent per-player store. */
    public static final class Persistent<S extends SavedData> {
        private final Class<S> type;
        private final String id;
        private final String exportKey;
        private final Function<MinecraftServer, S> live;
        private final Supplier<S> fresh;
        private final Function<S, Set<UUID>> knownPlayers;
        private final BiConsumer<S, UUID> forget;
        private final BiFunction<S, UUID, ? extends JsonElement> export;
        private final BiConsumer<MinecraftServer, UUID> refreshBackup;

        private Persistent(Class<S> type, String id, String exportKey, Function<MinecraftServer, S> live,
                Supplier<S> fresh, Function<S, Set<UUID>> knownPlayers, BiConsumer<S, UUID> forget,
                BiFunction<S, UUID, ? extends JsonElement> export, BiConsumer<MinecraftServer, UUID> refreshBackup) {
            this.type = type;
            this.id = id;
            this.exportKey = exportKey;
            this.live = live;
            this.fresh = fresh;
            this.knownPlayers = knownPlayers;
            this.forget = forget;
            this.export = export;
            this.refreshBackup = refreshBackup;
        }

        /** The store's SavedData id, used as its conformance subsystem name. */
        public String id() {
            return id;
        }

        /** The top-level key of this store's section in the access-request export. */
        public String exportKey() {
            return exportKey;
        }

        /** The store's class (the build-time completeness check matches against it). */
        public Class<S> type() {
            return type;
        }

        Set<UUID> knownIn(Snapshot stores) {
            return knownPlayers.apply(stores.get(this));
        }

        void forgetIn(Snapshot stores, UUID player) {
            forget.accept(stores.get(this), player);
        }

        JsonElement exportIn(Snapshot stores, UUID player) {
            return export.apply(stores.get(this), player);
        }

        void refreshBackup(MinecraftServer server, UUID player) {
            refreshBackup.accept(server, player);
        }

        private S create(MinecraftServer server) {
            return server == null ? fresh.get() : live.apply(server);
        }
    }

    /**
     * In-memory per-player state. {@code exportKey}/{@code export} are null for state that is not
     * personal data worth exporting (a UI selection, a replay cache).
     */
    public record Transient(String id, BiConsumer<MinecraftServer, UUID> forget, String exportKey,
            BiFunction<MinecraftServer, UUID, ? extends JsonElement> export) {
    }

    /** Refreshes a store's recovery backup after its row was forgotten. Injectable for tests. */
    @FunctionalInterface
    public interface BackupHook {
        void backup(Persistent<?> store, UUID player);
    }

    public static final Persistent<FactionReputationState> REPUTATION = new Persistent<>(
            FactionReputationState.class, FactionReputationState.ID.toString(), "reputation",
            FactionReputationState::get, FactionReputationState::new, FactionReputationState::knownPlayers,
            FactionReputationState::forgetPlayer, PlayerDataExport::reputationSection,
            FactionReputationState::eraseFor);

    public static final Persistent<FactionMembershipState> MEMBERSHIP = new Persistent<>(
            FactionMembershipState.class, FactionMembershipState.ID.toString(), "membership",
            FactionMembershipState::get, FactionMembershipState::new, FactionMembershipState::knownPlayers,
            FactionMembershipState::forgetPlayer, PlayerDataExport::membershipSection,
            FactionMembershipState::eraseFor);

    public static final Persistent<ContractState> CONTRACTS = new Persistent<>(
            ContractState.class, ContractState.ID.toString(), "contracts",
            ContractState::get, ContractState::new, ContractState::knownPlayers,
            ContractState::forget, ContractState::export, ContractState::eraseFor);

    public static final Persistent<QuotaState> MINER_QUOTA = new Persistent<>(
            QuotaState.class, "nerofactions:miner_quota", "miner_quota",
            QuotaState::get, QuotaState::new, QuotaState::knownPlayers,
            QuotaState::forget, QuotaState::export, QuotaState::eraseFor);

    public static final Persistent<OutpostVisits> OUTPOST_VISITS = new Persistent<>(
            OutpostVisits.class, "nerofactions:outpost_visits", "outposts_visited",
            OutpostVisits::get, OutpostVisits::new, OutpostVisits::knownPlayers,
            OutpostVisits::forget, OutpostVisits::export, OutpostVisits::eraseFor);

    public static final Persistent<WorkbenchAccess> WORKBENCH_ACCESS = new Persistent<>(
            WorkbenchAccess.class, "nerofactions:workbench_access", "workbench_access",
            WorkbenchAccess::get, WorkbenchAccess::new, WorkbenchAccess::knownPlayers,
            WorkbenchAccess::forget, WorkbenchAccess::export, WorkbenchAccess::eraseFor);

    public static final Persistent<CorporateLedger> CORPORATE_LEDGER = new Persistent<>(
            CorporateLedger.class, "nerofactions:corporate_ledger", "corporate_daily",
            CorporateLedger::get, CorporateLedger::new, CorporateLedger::knownPlayers,
            CorporateLedger::forget, CorporateLedger::export, CorporateLedger::eraseFor);

    /** Every persistent per-player store, in erasure order. */
    public static final List<Persistent<?>> PERSISTENT = List.of(REPUTATION, MEMBERSHIP, CONTRACTS,
            MINER_QUOTA, OUTPOST_VISITS, WORKBENCH_ACCESS, CORPORATE_LEDGER);

    /** Every in-memory per-player state an erasure request must also clear. */
    public static final List<Transient> TRANSIENT = List.of(
            new Transient("nerofactions:void_altar_boons", VoidAltarBlock::forget,
                    "void_boon_ticks_remaining",
                    (server, player) -> new JsonPrimitive(VoidAltarBlock.remaining(server, player))),
            new Transient("nerofactions:terminal_sessions",
                    (server, player) -> TerminalSessions.clear(player), null, null),
            new Transient("nerofactions:link_replay_cache",
                    (server, player) -> FactionsLinkActions.forget(player), null, null),
            new Transient("nerofactions:deconstructor_cooldowns",
                    (server, player) -> DeconstructorBlock.forget(player), null, null));

    /**
     * The store instances one operation works on, resolved lazily per store so a store that fails
     * to load does not stop the others: the live server's ({@link #live}) or fresh, empty ones
     * ({@link #fresh}, plain-JVM tests).
     */
    public static final class Snapshot {
        private final MinecraftServer server;
        private final Map<Persistent<?>, SavedData> states = new IdentityHashMap<>();

        private Snapshot(MinecraftServer server) {
            this.server = server;
        }

        @SuppressWarnings("unchecked")
        public <S extends SavedData> S get(Persistent<S> store) {
            return (S) states.computeIfAbsent(store, key -> ((Persistent<S>) key).create(server));
        }

        /** Installs a specific instance (tests: a codec round-tripped store). */
        <S extends SavedData> Snapshot with(Persistent<S> store, S state) {
            states.put(store, state);
            return this;
        }
    }

    public static Snapshot live(MinecraftServer server) {
        if (server == null) {
            throw new IllegalArgumentException("live stores need a server");
        }
        return new Snapshot(server);
    }

    static Snapshot fresh() {
        return new Snapshot(null);
    }

    /** Every class registered in {@link #PERSISTENT}, for the build-time completeness check. */
    static List<Class<?>> persistentTypes() {
        List<Class<?>> types = new ArrayList<>();
        PERSISTENT.forEach(store -> types.add(store.type()));
        return types;
    }

    /** Keeps the first failure and attaches later ones as suppressed. */
    static Throwable collect(Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    /** Rethrows {@code failure} unchanged when unchecked; wraps anything else. */
    static RuntimeException rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException(failure);
    }
}
