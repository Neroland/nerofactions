package za.co.neroland.nerofactions.progression;

import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerolandcore.progression.ProgressionGates;

/**
 * The personal <b>record gates</b> NeroFactions writes into Neroland Core's progression store:
 * player-scoped milestones ("this player swore into a faction", "this player made their first
 * terminal trade", "this player currently belongs to faction X") that a quest pack — NeroQuests'
 * {@code gate_open} objective, or anything else reading Core's gates — can observe without either
 * side naming the other.
 *
 * <p>These gates are <em>records</em>, not locks. They carry no {@code requires}, nothing in this
 * mod ever checks one to allow or deny anything (membership and standing have their own
 * authoritative state; {@code crafting/GatedRecipe} reads only the inner-circle gates), and the
 * seven {@code <faction>_member} gates simply mirror membership: opened on a successful join,
 * closed on a successful leave. The inner-circle gates ({@code <faction>_inner_circle}) are the
 * other kind — composed, with Core prerequisites, by {@code reputation/TierCrossings}.
 *
 * <p>Every write goes through Core's offline-capable {@code (server, UUID)} overloads (Core 1.15.0),
 * so a join or leave applied to an offline player still records. Writes are guarded: a gate store
 * problem is logged by gate id only and never refuses the join, leave or trade that produced it.
 * Nothing about the player is logged (POPIA/GDPR). The UUID goes only into Core's store, which
 * Core's erasure path already covers.
 */
public final class RecordGates {

    /** {@code nerofactions:sworn_in} — the player has joined a faction at least once. */
    public static final Identifier SWORN_IN = id("sworn_in");

    /** {@code nerofactions:first_terminal_trade} — the player completed a trade at a terminal. */
    public static final Identifier FIRST_TERMINAL_TRADE = id("first_terminal_trade");

    private static final String MEMBER_SUFFIX = "_member";

    private RecordGates() {
    }

    /**
     * {@code nerofactions:<faction_path>_member} for a faction id. Derived from the faction id's
     * path, like the inner-circle gate, so datapack factions from other namespaces compose too when
     * their pack ships the matching gate file.
     */
    public static Identifier memberGate(Identifier faction) {
        return id(faction.getPath() + MEMBER_SUFFIX);
    }

    /** Records a successful join: {@link #SWORN_IN} plus the faction's member gate. */
    public static void onJoined(MinecraftServer server, UUID player, Identifier faction) {
        open(server, player, SWORN_IN);
        open(server, player, memberGate(faction));
    }

    /** Records a successful leave: closes the faction's member gate. {@link #SWORN_IN} stays open. */
    public static void onLeft(MinecraftServer server, UUID player, Identifier faction) {
        close(server, player, memberGate(faction));
    }

    /** Records a completed terminal trade for an online player. */
    public static void onTerminalTrade(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            ProgressionGates.open(player, FIRST_TERMINAL_TRADE);
        } catch (RuntimeException e) {
            warn(FIRST_TERMINAL_TRADE, e);
        }
    }

    private static void open(MinecraftServer server, UUID player, Identifier gate) {
        if (server == null || player == null) {
            return;
        }
        try {
            ProgressionGates.open(server, player, gate);
        } catch (RuntimeException e) {
            warn(gate, e);
        }
    }

    private static void close(MinecraftServer server, UUID player, Identifier gate) {
        if (server == null || player == null) {
            return;
        }
        try {
            ProgressionGates.close(server, player, gate);
        } catch (RuntimeException e) {
            warn(gate, e);
        }
    }

    private static void warn(Identifier gate, RuntimeException e) {
        // Gate id only — never the player (POPIA/GDPR).
        NeroFactionsCommon.LOGGER.warn("[NeroFactions] Record gate {} could not be written.", gate, e);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroFactionsCommon.MOD_ID, path);
    }
}
