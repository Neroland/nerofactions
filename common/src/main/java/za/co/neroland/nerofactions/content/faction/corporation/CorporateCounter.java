package za.co.neroland.nerofactions.content.faction.corporation;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.contracts.ContractService;
import za.co.neroland.nerofactions.crafting.GatedRecipeCheck;
import za.co.neroland.nerofactions.data.CorporateLedger;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerofactions.registry.NeroFactionsItems;
import za.co.neroland.nerofactions.reputation.ReputationSources;
import za.co.neroland.nerolandcore.economy.CoreCurrencies;
import za.co.neroland.nerolandcore.economy.CurrencyApi;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

public final class CorporateCounter {
    private static final net.minecraft.resources.Identifier FACTION = OutpostFaction.NERO_CORPORATION.factionId();
    private static final int WRIT_CREDITS = 20;
    private static final int DELIVERY_CREDITS = 80;
    private static final int STAMP_GOLD = 8;

    private CorporateCounter() {}

    private record Delivery(Item item, int count) {}

    private static Delivery offer(long day) {
        return switch (Math.floorMod(day, 3)) {
            case 0 -> new Delivery(Items.IRON_INGOT, 32);
            case 1 -> new Delivery(Items.GOLD_INGOT, 24);
            default -> new Delivery(Items.COPPER_INGOT, 48);
        };
    }

    private static boolean eligible(ServerPlayer player, ServerLevel level) {
        var server = level.getServer();
        return NeroFactionsConfig.CORPORATE_COUNTER_ENABLED.get() && !player.isSpectator()
                && !player.getAbilities().instabuild && FactionMembership.membershipOf(server, player.getUUID()).contains(FACTION)
                && GatedRecipeCheck.standingMet(FactionDefinitions.factionsForServer(server).get(FACTION),
                        ReputationApi.getReputation(player.getUUID(), FACTION), FactionTier.MEMBER);
    }

    static void deliver(ServerPlayer player, ServerLevel level, ItemStack stack) {
        var server = level.getServer();
        var ledger = CorporateLedger.get(server);
        long day = ledger.currentDay(ContractService.worldDay(server));
        var delivery = offer(day);
        if (!eligible(player, level)) { message(player, "denied"); return; }
        if (ledger.delivered(player.getUUID(), day)) { message(player, "delivered"); return; }
        if (!stack.is(delivery.item()) || stack.getCount() < delivery.count() || !stack.getComponentsPatch().isEmpty()) {
            message(player, "offer", delivery.count(), delivery.item().getDefaultInstance().getHoverName());
            return;
        }
        if (!ledger.reserveDelivery(player.getUUID(), day)) return;
        stack.shrink(delivery.count());
        player.getInventory().setChanged();
        int standing = ReputationSources.award(server, player.getUUID(), FACTION, ReputationSources.Source.QUEST, 12);
        if (CurrencyApi.hasRealProvider() && pay(player, DELIVERY_CREDITS, "corporate_delivery")) {
            message(player, "paid", standing, DELIVERY_CREDITS);
        } else {
            give(player, new ItemStack(Items.EMERALD, 3));
            message(player, "fallback", standing);
        }
    }

    static void stamp(ServerPlayer player, ServerLevel level, InteractionHand hand) {
        ItemStack input = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        var server = level.getServer();
        var outcome = stampOutcome(eligible(player, level), CurrencyApi.hasRealProvider(),
                input.is(Items.GOLD_INGOT) && input.getCount() >= STAMP_GOLD && input.getComponentsPatch().isEmpty(),
                () -> CorporateLedger.get(server).reserveStamp(player.getUUID(), ContractService.worldDay(server)));
        switch (outcome) {
            case DENIED -> message(player, "denied");
            case NO_CURRENCY -> message(player, "stamp_no_currency");
            case BAD_INPUT -> message(player, "stamp_input", STAMP_GOLD);
            case CAPPED -> message(player, "stamp_cap");
            case STAMPED -> {
                input.shrink(STAMP_GOLD);
                player.getInventory().setChanged();
                player.getItemInHand(hand).hurtAndBreak(1, player,
                        hand == InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
                give(player, new ItemStack(NeroFactionsItems.CORPORATE_WRIT.get()));
                message(player, "stamped");
            }
        }
    }

    enum StampOutcome { DENIED, NO_CURRENCY, BAD_INPUT, CAPPED, STAMPED }

    /**
     * Writs are only worth anything through a real currency provider (see {@link #redeem}), so without one the
     * stamp refuses before any gold is taken or a daily stamp is reserved. {@code reserveStamp} runs last and only
     * when every other check passed.
     */
    static StampOutcome stampOutcome(boolean eligible, boolean currencyProvider, boolean inputOk,
            java.util.function.BooleanSupplier reserveStamp) {
        if (!eligible) return StampOutcome.DENIED;
        if (!currencyProvider) return StampOutcome.NO_CURRENCY;
        if (!inputOk) return StampOutcome.BAD_INPUT;
        return reserveStamp.getAsBoolean() ? StampOutcome.STAMPED : StampOutcome.CAPPED;
    }

    public static boolean redeem(ServerPlayer player, ItemStack stack) {
        if (!stack.is(NeroFactionsItems.CORPORATE_WRIT.get())) return false;
        if (!NeroFactionsConfig.CORPORATE_COUNTER_ENABLED.get() || player.isSpectator() || player.getAbilities().instabuild
                || !CurrencyApi.hasRealProvider() || !stack.getComponentsPatch().isEmpty()
                || !pay(player, WRIT_CREDITS, "corporate_writ")) {
            message(player, "writ_denied");
            return true;
        }
        stack.shrink(1);
        player.getInventory().setChanged();
        message(player, "redeemed", WRIT_CREDITS);
        return true;
    }

    private static boolean pay(ServerPlayer player, int credits, String reason) {
        return CurrencyApi.deposit(player.getUUID(), CoreCurrencies.CREDITS, credits, "nerofactions", reason);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        //? if >=26.3 {
        /*player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
        *///?} else {
        player.getInventory().placeItemBackInInventory(stack);
        //?}
    }

    private static void message(ServerPlayer player, String code, Object... args) {
        player.sendSystemMessage(Component.translatable("message.nerofactions.corporate." + code, args));
    }
}
