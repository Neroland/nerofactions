package za.co.neroland.nerofactions.link;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerofactions.NeroFactionsCommon;

/**
 * The one place the link surfaces resolve their context: which server is running, and is this
 * player online. Package-private on purpose — nothing outside the link package may reach for
 * these answers through it.
 *
 * <p>Core's snapshot API hands over a {@link UUID} and nothing else — no server, no player — so
 * the running server comes from the mod's existing lifecycle seam,
 * {@code ServerReputationProvider.boundServer()}, which each loader's server started/stopped hook
 * already binds and unbinds (there is deliberately no second lifecycle tracker for the link
 * module to drift from). Before the first world is loaded there is no server and every section
 * answers empty, which is the honest result rather than a guess.
 *
 * <h2>The visibility rule</h2>
 *
 * <p>Unlike mods whose link modules must pick <em>which</em> records a requester may see,
 * NeroFactions' scoping is <b>structural</b>: both stores are keyed by player UUID and every
 * section reads exactly the requesting UUID's row, so there is no faction- or player-naming
 * parameter that could be widened, and nothing here takes one. This class still exists so the
 * resolution posture lives in one documented place — and so the one rule its siblings state
 * explicitly is stated here too: <b>never widen for an operator.</b> An operator's powers are a
 * property of a live command source, not of a UUID arriving over a bridge, and a link module that
 * honoured them would turn "I am an admin" into "my phone can read every player's standing".
 *
 * <p>Server thread only. Nothing here reads or stores player data beyond the UUID it is handed.
 */
final class FactionsLinkAccess {

    private FactionsLinkAccess() {
    }

    /** The running server, or {@code null} before the first world load / after shutdown. */
    @Nullable
    static MinecraftServer server() {
        return NeroFactionsCommon.REPUTATION_PROVIDER.boundServer();
    }

    /** Whether this player is online right now. */
    static boolean isOnline(MinecraftServer server, UUID playerId) {
        return playerId != null && server.getPlayerList().getPlayer(playerId) != null;
    }
}
