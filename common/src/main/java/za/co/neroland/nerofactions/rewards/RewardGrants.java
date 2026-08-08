package za.co.neroland.nerofactions.rewards;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.RewardEntry;
import za.co.neroland.nerofactions.cosmetics.FactionBanners;
import za.co.neroland.nerofactions.data.FactionMembershipState;
import za.co.neroland.nerolandcore.event.ThresholdEvents.ThresholdCrossing;

/**
 * The tier-reward granting engine: when a player <b>rises</b> across a tier boundary, the
 * faction's reward table for the newly reached tier pays out — once, ever. Driven from
 * {@code TierCrossings} (the one place every reputation change is already enumerated into
 * boundary crossings), immediately <em>after</em> the crossings are fired on Core's shared bus,
 * so the ecosystem always hears about a milestone before its rewards land.
 *
 * <p><b>Idempotent by watermark.</b> {@link FactionMembershipState} persists, per player per
 * faction, the highest tier ordinal whose rewards were ever granted (the <em>watermark</em> —
 * high-water, never lowered). A rising crossing grants only tiers above the watermark and raises
 * it as each tier pays out, so: decay below a tier and re-earning it does <b>not</b> re-grant;
 * a multi-tier jump grants each newly crossed tier exactly once, in ascending order; and a
 * <b>falling</b> crossing never grants and never touches the watermark. Erasure removes the
 * watermark with the rest of the player's row (a returning erased player is reward-eligible
 * again — the accepted price of a clean erasure; see PRIVACY.md).
 *
 * <p><b>Delivery.</b> {@code "item"} entries become plain vanilla ItemStacks, given with vanilla
 * give behaviour (into the inventory, overflow dropped at the player's feet). {@code "cosmetic"}
 * entries — both kinds, {@code banner_pattern} and {@code trim_material} — resolve to the
 * faction's pre-styled vanilla banner ({@link FactionBanners}; the 0.1.0 cosmetics decision is
 * documented there). One translatable notice is sent per tier granted.
 *
 * <p><b>The player must be online.</b> Rewards write into an inventory, so grants target the
 * connected {@code ServerPlayer}. Rising while offline is impossible today: every rising path
 * (source awards, trades, admin grants) requires an acting player or an operator command aimed
 * at raising standing, and the only offline mutation — decay — moves standing toward 0, firing
 * falling crossings while positive (which never grant) and rising crossings only for
 * <em>negative</em> standing decaying back toward 0, which cannot exceed the Outsider boundary
 * (threshold 0 is the ladder's floor, ordinal 0 never grants). Assert-comment, not code: if a
 * future source ever raises offline standing, the watermark makes the miss harmless — the tier
 * pays out on the player's next rising crossing... which requires first decaying/dropping below
 * and re-earning, so such a source should grant explicitly instead.
 *
 * <p><b>POPIA/GDPR:</b> the only per-player datum this class writes is the watermark described
 * above, stored in {@link FactionMembershipState} (erased with it, backed up with it). Nothing
 * is logged against a player identity.
 */
public final class RewardGrants {

    private RewardGrants() {
    }

    /**
     * Game-side entry point, called by {@code TierCrossings} with the already-enumerated
     * boundary crossings of one reputation change.
     */
    public static void onCrossings(MinecraftServer server, UUID playerId,
            FactionDefinition faction, List<ThresholdCrossing> crossings) {
        if (server == null || playerId == null || faction == null || crossings.isEmpty()) {
            return;
        }
        boolean anyRising = false;
        for (ThresholdCrossing crossing : crossings) {
            anyRising |= crossing.rising();
        }
        if (!anyRising) {
            return; // downward never grants
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            // Rising while offline — see the class javadoc for why this is unreachable today.
            // The watermark stays untouched, so nothing is ever silently consumed.
            return;
        }
        List<FactionTier> granted = grant(FactionMembershipState.get(server), faction, playerId,
                crossings, (tier, entry) -> deliver(server, player, faction, entry));
        for (FactionTier tier : granted) {
            player.sendSystemMessage(Component.translatable(
                    "message.nerofactions.reward.granted", faction.displayName(),
                    Component.translatable(tier.translationKey())));
        }
    }

    /**
     * The deterministic core, parameterised for the plain-JVM tests: walks the rising crossings
     * in the order they fired (ascending), skips every tier at or below the player's watermark,
     * delivers each surviving tier's reward entries through {@code deliver}, and raises the
     * watermark per tier granted.
     *
     * @return the tiers actually granted, in ascending order (empty when nothing was)
     */
    static List<FactionTier> grant(FactionMembershipState state, FactionDefinition faction,
            UUID player, List<ThresholdCrossing> crossings,
            BiConsumer<FactionTier, RewardEntry> deliver) {
        List<FactionTier> granted = new ArrayList<>(crossings.size());
        FactionTier[] tiers = FactionTier.values();
        for (ThresholdCrossing crossing : crossings) {
            if (!crossing.rising()) {
                continue;
            }
            int ordinal = (int) crossing.value();
            if (ordinal <= 0 || ordinal >= tiers.length) {
                continue; // Outsider has no boundary; out-of-band values are not ours
            }
            if (ordinal <= state.rewardWatermark(player, faction.id())) {
                continue; // granted in a previous life: the watermark is a high-water mark
            }
            FactionTier tier = tiers[ordinal];
            for (RewardEntry entry : faction.rewardsFor(tier)) {
                deliver.accept(tier, entry);
            }
            state.raiseRewardWatermark(player, faction.id(), ordinal);
            granted.add(tier);
        }
        return granted;
    }

    /** Delivers one reward entry to an online player with vanilla give behaviour. */
    private static void deliver(MinecraftServer server, ServerPlayer player,
            FactionDefinition faction, RewardEntry entry) {
        ItemStack stack = stackFor(server, faction, entry);
        if (stack.isEmpty()) {
            return;
        }
        // Vanilla give behaviour: fill the inventory, drop the remainder at the player's feet.
        player.getInventory().placeItemBackInInventory(stack);
    }

    /** The ItemStack one reward entry resolves to (empty when it cannot be built). */
    private static ItemStack stackFor(MinecraftServer server, FactionDefinition faction,
            RewardEntry entry) {
        if (RewardEntry.TYPE_COSMETIC.equals(entry.type())) {
            // Both cosmetic kinds resolve to the faction banner in 0.1.0 — see FactionBanners.
            return FactionBanners.build(server.registryAccess(), faction);
        }
        if (RewardEntry.TYPE_ITEM.equals(entry.type()) && entry.item().isPresent()) {
            Identifier itemId = entry.item().get();
            if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
                // Validation prunes unknown items at load; a datapack swap mid-session could
                // still race this. Skipping beats crashing a reputation write.
                NeroFactionsCommon.LOGGER.warn(
                        "[NeroFactions] Reward item {} is not registered; skipping.", itemId);
                return ItemStack.EMPTY;
            }
            Item item = BuiltInRegistries.ITEM.getValue(itemId);
            return new ItemStack(item, Math.max(1, entry.count()));
        }
        return ItemStack.EMPTY;
    }
}
