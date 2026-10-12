package za.co.neroland.nerofactions.integration.quests;

import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.cosmetics.FactionBanners;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerolandcore.event.CoreEvents;
import za.co.neroland.nerolandcore.progression.GateEvents.GateChange;
import za.co.neroland.nerolandcore.progression.GateScope;
import za.co.neroland.nerolandcore.progression.ProgressionGates;

/**
 * What a quest pack's {@code mod_hook} reward does here. A quest mod opens the record gate below
 * for the player who completed the quest — online or not — through Neroland Core alone (no class of
 * any quest mod is named here); this listens on Core's gate events, applies the effect and closes
 * the gate again to re-arm it. The gate is a record that gates nothing: nothing ever checks it for
 * access.
 *
 * <ul>
 *   <li>{@code nerofactions:hook/banner_pattern} — hands the player the banner of the faction they
 *       currently belong to (the pre-styled vanilla banner of {@link FactionBanners}; the mod ships
 *       no custom banner-pattern items by scope decision). A player who belongs to no faction gets
 *       nothing yet: the gate stays open and the banner is delivered the next time they join the
 *       server as a member ({@link #onJoin}). With multiple allegiances the first faction in the
 *       store's order is used.</li>
 * </ul>
 *
 * <p>While no server is bound the gate also stays open for the join path. Listeners run on the
 * server thread. Nothing about a player is logged (POPIA/GDPR).
 */
public final class QuestHooks {

    /** {@code nerofactions:hook/banner_pattern}. */
    public static final Identifier BANNER_PATTERN =
            Identifier.fromNamespaceAndPath(NeroFactionsCommon.MOD_ID, "hook/banner_pattern");

    private static boolean initialised;

    private QuestHooks() {
    }

    /** Common init: subscribes (once) to Core's gate events for the hook gate. */
    public static synchronized void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        CoreEvents.onProgression(QuestHooks::onChange);
    }

    static void onChange(GateChange change) {
        if (change == null || !change.open() || change.scope() != GateScope.PLAYER
                || !BANNER_PATTERN.equals(change.gate())) {
            return;
        }
        MinecraftServer server = NeroFactionsCommon.REPUTATION_PROVIDER.boundServer();
        if (server == null) {
            return; // the gate stays open; see onJoin
        }
        UUID id;
        try {
            id = UUID.fromString(change.target());
        } catch (IllegalArgumentException | NullPointerException malformed) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null) {
            return; // offline: the gate stays open; see onJoin
        }
        deliver(server, player);
    }

    /** A player joined: delivers a banner whose hook opened while they were away or unaffiliated. */
    public static void onJoin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        boolean open;
        try {
            open = ProgressionGates.isOpen(player, BANNER_PATTERN);
        } catch (RuntimeException failure) {
            return;
        }
        if (open) {
            deliver(server, player);
        }
    }

    /**
     * Gives the player their current faction's banner and closes the hook gate. Unaffiliated
     * players keep the gate open (the reward waits for them to join a faction).
     */
    private static void deliver(MinecraftServer server, ServerPlayer player) {
        try {
            Set<Identifier> factions = FactionMembership.membershipOf(server, player.getUUID());
            if (factions.isEmpty()) {
                return;
            }
            FactionDefinition faction = FactionDefinitions.factionsForServer(server)
                    .get(factions.iterator().next());
            if (faction == null) {
                return; // the faction left the datapack; keep the hook armed for a new allegiance
            }
            ItemStack banner = FactionBanners.build(server.registryAccess(), faction);
            //? if >=26.3 {
            /*player.getInventory().placeItemBackInInventory(banner, net.minecraft.util.Prediction.SERVER_ONLY);
            *///?} else {
            player.getInventory().placeItemBackInInventory(banner);
            //?}
            ProgressionGates.close(player, BANNER_PATTERN);
        } catch (RuntimeException failure) {
            // Hook id only — never the player (POPIA/GDPR).
            NeroFactionsCommon.LOGGER.warn("[NeroFactions] Quest hook {} could not be applied.",
                    BANNER_PATTERN, failure);
        }
    }
}
