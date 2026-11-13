package za.co.neroland.nerofactions.link;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.FactionTiers;
import za.co.neroland.nerofactions.content.faction.FactionArmor;
import za.co.neroland.nerofactions.contracts.ContractService;
import za.co.neroland.nerofactions.data.ContractState;
import za.co.neroland.nerofactions.data.FactionMembershipState;
import za.co.neroland.nerofactions.data.OutpostVisits;
import za.co.neroland.nerolandcore.economy.CurrencyApi;
import za.co.neroland.nerofactions.data.FactionReputationState;
import za.co.neroland.nerolandcore.link.LinkSnapshotProvider;

/**
 * The read half of the link module: the requesting player's own faction life, and nothing else.
 *
 * <h2>Sections</h2>
 *
 * <ul>
 *   <li>{@code standing} — one row per <em>loaded</em> faction the requester has any standing with
 *       or membership of: faction id, display name, the stored standing value, and the resolved
 *       tier (ordinal + JSON name). A faction the requester has never touched produces no row, so
 *       the section is as empty as the player's record is;</li>
 *   <li>{@code membership} — the requester's current memberships with their joined-at timestamps,
 *       the join-cooldown end, and the left-faction decay bookkeeping.</li>
 * </ul>
 *
 * <p>Any other section name yields an empty object, as Core's contract prescribes. No parameters
 * are read: there is nothing safe for one to select (see {@link FactionsLinkAccess}).
 *
 * <h2>Privacy (POPIA/GDPR)</h2>
 *
 * <p>Scoping is <b>structural</b>: every read below is keyed by the requesting {@code playerId}
 * against per-player stores, so another player's standing cannot appear in a response — there is
 * no roster, no aggregate, and no parameter through which one could be asked for. Standing is
 * exactly the kind of data a wider answer would leak (it maps the server's social graph), which is
 * why the one visibility rule is stated where the resolution lives, in {@link FactionsLinkAccess}.
 *
 * <p><b>Read-only, including decay.</b> This path reports <em>stored</em> values and deliberately
 * does <b>not</b> run {@code FactionDecay.apply} the way the mod's own on-read paths do: a
 * snapshot may be requested from the bridge at any moment, and a read that mutates the store is a
 * server-thread hazard waiting for a caller that forgets the contract. The cost is bounded
 * staleness — {@code FactionsTicker} runs the decay pass once per real-time minute, so a left
 * faction's reported standing is never more than a minute behind the value the mod itself would
 * act on.
 *
 * <p>Snapshots resolve loaded faction definitions, so a standing stored for a faction whose
 * datapack has been removed is simply not shown (its tier could not be resolved anyway); the DSAR
 * export ({@code /nerofactions data export}) remains the complete raw record.
 *
 * <p>Server thread only.
 */
public final class FactionsLinkSnapshots implements LinkSnapshotProvider {

    private static final List<String> SECTIONS = List.of(
            FactionsLinkModule.SECTION_STANDING,
            FactionsLinkModule.SECTION_MEMBERSHIP,
            FactionsLinkModule.SECTION_CONTRACTS,
            FactionsLinkModule.SECTION_GEAR,
            FactionsLinkModule.SECTION_OUTPOSTS_VISITED);

    private final Stores stores;

    /** Production: everything resolved from the bound server, per call. */
    public FactionsLinkSnapshots() {
        this(FactionsLinkSnapshots::productionView);
    }

    /** Test seam: the store lookup is injectable so plain-JVM tests can hand in direct stores. */
    FactionsLinkSnapshots(Stores stores) {
        this.stores = stores;
    }

    @Override
    public String moduleId() {
        return FactionsLinkModule.MODULE_ID;
    }

    @Override
    public int schemaVersion() {
        return FactionsLinkModule.SCHEMA_VERSION;
    }

    @Override
    public List<String> sections() {
        return SECTIONS;
    }

    @Override
    public JsonObject snapshot(UUID playerId, String section, Map<String, String> params) {
        if (playerId == null || section == null) {
            return new JsonObject();
        }
        if (!NeroFactionsConfig.LINK_MODULE_ENABLED.get()) {
            return new JsonObject();
        }
        try {
            View view = stores.open();
            if (view == null) {
                return new JsonObject();
            }
            return switch (section) {
                case FactionsLinkModule.SECTION_STANDING -> standing(view, playerId);
                case FactionsLinkModule.SECTION_MEMBERSHIP -> membership(view, playerId);
                case FactionsLinkModule.SECTION_CONTRACTS -> contracts(view, playerId);
                case FactionsLinkModule.SECTION_GEAR -> gear(view, playerId);
                case FactionsLinkModule.SECTION_OUTPOSTS_VISITED -> {
                    JsonObject root = envelope(view, playerId);
                    root.add("outposts_visited", view.outpostsVisited(playerId));
                    yield root;
                }
                // Unknown section: nothing to say.
                default -> new JsonObject();
            };
        } catch (RuntimeException e) {
            // Section name only — never who asked (POPIA/GDPR). A failed snapshot must not
            // propagate into the bridge.
            NeroFactionsCommon.LOGGER.warn(
                    "[NeroFactions] NeroLink snapshot section '{}' failed; returning nothing for it.",
                    section, e);
            return new JsonObject();
        }
    }

    // --- section: standing ------------------------------------------------------

    private static JsonObject standing(View view, UUID playerId) {
        Set<Identifier> memberships = view.memberships(playerId);
        JsonArray rows = new JsonArray();
        for (FactionDefinition faction : view.factions().values()) {
            int value = view.standing(playerId, faction.id());
            if (value == 0 && !memberships.contains(faction.id())) {
                // Canonical zero: no standing and no pledge means no row — the section reveals
                // nothing about factions the requester has no history with.
                continue;
            }
            FactionTier tier = FactionTiers.tierOf(faction, value);
            JsonObject row = new JsonObject();
            row.addProperty("faction", faction.id().toString());
            row.addProperty("display_name", faction.displayName());
            row.addProperty("value", value);
            row.addProperty("tier", tier.ordinal());
            row.addProperty("tier_name", tier.jsonName());
            rows.add(row);
        }
        JsonObject root = envelope(view, playerId);
        root.add("standings", rows);
        return root;
    }

    // --- section: membership ----------------------------------------------------

    private static JsonObject membership(View view, UUID playerId) {
        JsonObject memberships = new JsonObject();
        for (Identifier faction : view.memberships(playerId)) {
            memberships.addProperty(faction.toString(), view.joinedAt(playerId, faction));
        }
        JsonObject left = new JsonObject();
        view.leftFactions(playerId)
                .forEach((faction, leftAt) -> left.addProperty(faction.toString(), leftAt));
        JsonObject root = envelope(view, playerId);
        root.add("memberships", memberships);
        root.addProperty("cooldown_until_ms", view.cooldownUntil(playerId));
        root.add("left", left);
        return root;
    }

    private static JsonObject contracts(View view, UUID playerId) {
        JsonObject root = envelope(view, playerId);
        long day = view.contractDay(playerId);
        boolean current = day != Long.MIN_VALUE && day >= view.worldDay();
        JsonArray rows = new JsonArray();
        if (current && NeroFactionsConfig.CONTRACTS_ENABLED.get()) {
            Set<Identifier> memberships = view.memberships(playerId);
            view.contracts(playerId).stream().filter(entry -> memberships.contains(entry.contract().faction())
                    && view.factions().containsKey(entry.contract().faction())).forEach(entry -> {
                var contract = entry.contract();
                JsonObject row = new JsonObject();
                row.addProperty("faction", contract.faction().toString());
                row.addProperty("contract", contract.id().toString());
                row.addProperty("slot", entry.slot());
                row.addProperty("type", contract.type());
                row.addProperty("target", contract.target());
                row.addProperty("progress", entry.progress());
                row.addProperty("count", contract.count());
                row.addProperty("standing", contract.reputation());
                row.addProperty("credits", CurrencyApi.hasRealProvider() ? contract.credits() : 0);
                row.addProperty("claimed", entry.claimed());
                rows.add(row);
            });
            root.addProperty("day", day);
        }
        root.add("contracts", rows);
        root.addProperty("reroll_available", !rows.isEmpty() && view.rerollAvailable(playerId)
                && NeroFactionsConfig.LINK_REROLL_ENABLED.get());
        return root;
    }

    private static JsonObject gear(View view, UUID playerId) {
        JsonObject root = envelope(view, playerId);
        JsonArray sets = new JsonArray();
        for (var set : FactionArmor.values()) {
            Identifier faction = set.factionId();
            FactionDefinition definition = view.factions().get(faction);
            if (definition == null) {
                continue;
            }
            var tier = FactionTiers.tierOf(definition, view.standing(playerId, faction));
            JsonObject row = new JsonObject();
            row.addProperty("faction", faction.toString());
            row.addProperty("set", "nerofactions:" + set.id());
            row.addProperty("eligible", tier.ordinal() >= FactionTier.TRUSTED.ordinal()
                    && view.memberships(playerId).contains(faction));
            JsonArray pieces = new JsonArray();
            for (String suffix : List.of("helmet", "chestplate", "leggings", "boots")) {
                JsonObject piece = new JsonObject();
                piece.addProperty("item", "nerofactions:" + set.id() + "_" + suffix);
                piece.addProperty("unlocked", tier.ordinal() >= FactionTier.TRUSTED.ordinal());
                pieces.add(piece);
            }
            row.add("pieces", pieces);
            sets.add(row);
        }
        root.add("sets", sets);
        return root;
    }

    // --- helpers ----------------------------------------------------------------

    private static JsonObject envelope(View view, UUID playerId) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", FactionsLinkModule.SCHEMA_VERSION);
        root.addProperty("player_online", view.online(playerId));
        return root;
    }

    // --- the store lookup seam --------------------------------------------------

    /**
     * Where one snapshot call's reads come from. {@link #open()} runs once per call and returns
     * {@code null} when there is nothing to read from (no world loaded), which every section
     * answers as an empty object. Package-private so tests can substitute direct stores; the
     * production path ({@link #productionView()}) is the only other implementation.
     */
    @FunctionalInterface
    interface Stores {
        @Nullable
        View open();
    }

    /** Everything one snapshot call may read, already resolved and requester-keyed. */
    interface View {

        /** The loaded faction definitions, keyed by id. World content, not player data. */
        Map<Identifier, FactionDefinition> factions();

        /** The requester's stored standing with one faction ({@code 0} = none stored). */
        int standing(UUID player, Identifier faction);

        /** The factions the requester currently belongs to. */
        Set<Identifier> memberships(UUID player);

        /** When the requester joined this faction (epoch ms; {@code 0} = not a member). */
        long joinedAt(UUID player, Identifier faction);

        /** Epoch ms before which the requester may not join a faction ({@code 0} = none). */
        long cooldownUntil(UUID player);

        /** faction id → left-at epoch ms for the requester's factions still pending decay. */
        Map<Identifier, Long> leftFactions(UUID player);

        /** Whether the requester is online right now. */
        boolean online(UUID player);

        long worldDay();

        long contractDay(UUID player);

        boolean rerollAvailable(UUID player);

        List<ContractState.Entry> contracts(UUID player);

        JsonArray outpostsVisited(UUID player);
    }

    /** The production view: the bound server's stores and its loaded faction definitions. */
    @Nullable
    private static View productionView() {
        MinecraftServer server = FactionsLinkAccess.server();
        if (server == null) {
            return null;
        }
        FactionReputationState reputation = FactionReputationState.get(server);
        FactionMembershipState membership = FactionMembershipState.get(server);
        Map<Identifier, FactionDefinition> factions = FactionDefinitions.factionsForServer(server);
        return new View() {
            @Override
            public Map<Identifier, FactionDefinition> factions() {
                return factions;
            }

            @Override
            public int standing(UUID player, Identifier faction) {
                return reputation.getReputation(player, faction);
            }

            @Override
            public Set<Identifier> memberships(UUID player) {
                return membership.membershipsOf(player);
            }

            @Override
            public long joinedAt(UUID player, Identifier faction) {
                return membership.joinedAt(player, faction);
            }

            @Override
            public long cooldownUntil(UUID player) {
                return membership.cooldownUntil(player);
            }

            @Override
            public Map<Identifier, Long> leftFactions(UUID player) {
                return membership.leftFactions(player);
            }

            @Override
            public boolean online(UUID player) {
                return FactionsLinkAccess.isOnline(server, player);
            }

            @Override public long worldDay() { return ContractService.worldDay(server); }
            @Override public long contractDay(UUID player) { return ContractState.get(server).day(player); }
            @Override public boolean rerollAvailable(UUID player) { return ContractState.get(server).rerollAvailable(player); }
            @Override public List<ContractState.Entry> contracts(UUID player) { return ContractState.get(server).entries(player); }
            @Override public JsonArray outpostsVisited(UUID player) { return OutpostVisits.get(server).export(player); }
        };
    }
}
