package za.co.neroland.nerofactions.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//? if >=1.20.5 {
import net.minecraft.world.item.trading.ItemCost;
//?}
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.FactionTiers;
import za.co.neroland.nerofactions.content.faction.FactionAccess;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.cosmetics.FactionBanners;
import za.co.neroland.nerofactions.integration.FactionEconomy;
import za.co.neroland.nerofactions.membership.FactionDecay;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerofactions.menu.MenuOpener;
import za.co.neroland.nerofactions.platform.VersionCompat;
import za.co.neroland.nerofactions.trade.TerminalMerchant;
import za.co.neroland.nerofactions.trade.TerminalOffers;
import za.co.neroland.nerofactions.trade.TerminalOffers.OfferSpec;
import za.co.neroland.nerofactions.trade.TerminalSessions;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

/**
 * The faction trade terminal — <b>one shared block for every faction</b> (deliberate 0.1.0 scope:
 * no per-faction machines). A plain {@link Block}, no block entity: the terminal is stateless and
 * everything it shows is resolved per interaction from server state.
 *
 * <p><b>Membership finally matters:</b> the terminal serves the player's <em>member</em> factions
 * only — trading is the first concrete perk of joining. A non-member is told (translatably) to
 * join and how. Standing then sets the <em>quality</em> of the shop: the player's tier with the
 * chosen faction scales rates, offer counts and uses (see {@code TerminalOffers}).
 *
 * <p><b>Use flow (server side only):</b> pending decay is applied first
 * ({@link FactionDecay#apply}) so a lapsed tier cannot keep yesterday's rates; the player's member
 * factions are resolved; the shop shown is their remembered selection (or their best-standing
 * faction), and <b>sneak-use cycles</b> to the next member faction — the selection is a transient
 * in-memory convenience ({@link TerminalSessions}), never stored.
 *
 * <p><b>Menu opening is guarded.</b> {@code Merchant.openTradingScreen} calls
 * {@code player.openMenu} internally with no way to interpose, so this block does not use it:
 * it opens the vanilla {@link MerchantMenu} itself through {@link MenuOpener} (the ecosystem's
 * guarded {@code openMenu} seam) and then sends the offer list exactly the way vanilla's
 * {@code openTradingScreen} would — the same guard semantics, without the unguarded call.
 */
public class TradeTerminalBlock extends Block {

    public static final EnumProperty<OutpostFaction> FACTION = EnumProperty.create("faction", OutpostFaction.class);

    public TradeTerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACTION, OutpostFaction.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACTION);
    }

    //? if >=1.21.2 {
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(za.co.neroland.nerofactions.registry.NeroFactionsItems.CORPORATE_WRIT.get())) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (player instanceof ServerPlayer online) {
            za.co.neroland.nerofactions.content.faction.corporation.CorporateCounter.redeem(online, stack);
        }
        return InteractionResult.SUCCESS;
    }
    //?} else if >=1.20.5 {
    /*@Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(za.co.neroland.nerofactions.registry.NeroFactionsItems.CORPORATE_WRIT.get())) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (player instanceof ServerPlayer online) {
            za.co.neroland.nerofactions.content.faction.corporation.CorporateCounter.redeem(online, stack);
        }
        return net.minecraft.world.ItemInteractionResult.SUCCESS;
    }
    *///?} else {
    /*private InteractionResult useHeldItem(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(za.co.neroland.nerofactions.registry.NeroFactionsItems.CORPORATE_WRIT.get())) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer online) {
            za.co.neroland.nerofactions.content.faction.corporation.CorporateCounter.redeem(online, stack);
        }
        return InteractionResult.SUCCESS;
    }
    *///?}

    //? if >=1.20.5 {
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
    //?} else {
    /*@Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty()) {
            InteractionResult handled = useHeldItem(held, state, level, pos, player, hand, hit);
            if (handled != InteractionResult.PASS) {
                return handled;
            }
        }
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
    *///?}
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return InteractionResult.SUCCESS;
        }
        UUID playerId = serverPlayer.getUUID();

        // Standing first: pending decay is applied before anything reads a tier, so the shop the
        // player sees is the standing they actually still have.
        FactionDecay.apply(server, playerId);

        Map<Identifier, FactionDefinition> factions = FactionDefinitions.factionsForServer(server);
        OutpostFaction binding = state.getValue(FACTION);
        if (binding != OutpostFaction.NONE) {
            FactionDefinition faction = factions.get(binding.factionId());
            if (faction != null) {
                trade(serverPlayer, server, level, pos, state, faction);
            }
            return InteractionResult.SUCCESS;
        }

        List<Identifier> memberships = new ArrayList<>();
        for (Identifier faction : FactionMembership.membershipOf(server, playerId)) {
            if (factions.containsKey(faction)) {
                memberships.add(faction);
            }
        }
        memberships.sort(null); // a stable cycle order on every visit

        if (memberships.isEmpty()) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.nerofactions.terminal.not_member"));
            return InteractionResult.SUCCESS;
        }

        Identifier best = bestStanding(playerId, memberships);
        boolean switched = serverPlayer.isShiftKeyDown() && memberships.size() > 1;
        Identifier chosen = switched
                ? TerminalSessions.next(playerId, memberships, best)
                : TerminalSessions.current(playerId, memberships, best);
        FactionDefinition faction = factions.get(chosen);

        if (switched) {
            serverPlayer.sendSystemMessage(Component.translatable(
                    "message.nerofactions.terminal.switched", faction.displayName()));
        }
        trade(serverPlayer, server, level, pos, state, faction);
        return InteractionResult.SUCCESS;
    }

    private static void trade(ServerPlayer player, MinecraftServer server, Level level, BlockPos pos,
            BlockState state, FactionDefinition faction) {
        UUID playerId = player.getUUID();
        TerminalSessions.clearVisit(playerId);
        FactionAccess access = FactionAccess.evaluate(server, playerId, faction);
        if (access.refusesService()) {
            player.sendSystemMessage(refusal(faction, access));
            return;
        }
        FactionTier tier = FactionTiers.tierOf(faction, ReputationApi.getReputation(playerId, faction.id()));
        MerchantOffers offers = buildOffers(server, faction, tier, access);
        if (!openShop(player, level, pos, state, faction, access, tier, offers)) {
            return;
        }
        TerminalSessions.rememberTerminal(playerId, level.dimension().identifier(), pos.asLong(),
                state.getValue(FACTION), faction.id());
        if (FactionEconomy.creditsAvailable() && NeroFactionsConfig.TERMINAL_CREDITS_PER_EMERALD.get() > 0) {
            Component open = Component.translatable("message.nerofactions.terminal.credit_counter.open")
                    .withStyle(style -> style
                            .withUnderlined(true)
                            .withClickEvent(VersionCompat.runCommand("/nerofactions terminal credits")));
            player.sendSystemMessage(
                    Component.translatable("message.nerofactions.terminal.credit_counter", open));
        }
    }

    public static boolean tradeAtBoundTerminal(ServerPlayer player, BlockPos pos, OutpostFaction faction) {
        Level level = player.level();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TradeTerminalBlock) || state.getValue(FACTION) != faction
                || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) {
            return false;
        }
        var server = level.getServer();
        if (server == null) {
            return false;
        }
        var definition = FactionDefinitions.factionsForServer(server).get(faction.factionId());
        if (definition == null) {
            return false;
        }
        FactionDecay.apply(server, player.getUUID());
        trade(player, server, level, pos, state, definition);
        return true;
    }

    public static Component refusal(FactionDefinition faction, FactionAccess access) {
        return Component.translatable(access == FactionAccess.REFUSE_STANDING
                ? "message.nerofactions.terminal.hostile_standing"
                : "message.nerofactions.terminal.hostile_rival", faction.displayName());
    }

    /** The member faction the player stands highest with (ties break on the sorted order). */
    private static Identifier bestStanding(UUID player, List<Identifier> memberships) {
        Identifier best = memberships.get(0);
        int bestValue = Integer.MIN_VALUE;
        for (Identifier faction : memberships) {
            int value = ReputationApi.getReputation(player, faction);
            if (value > bestValue) {
                bestValue = value;
                best = faction;
            }
        }
        return best;
    }

    /**
     * Opens the vanilla merchant screen through the {@link MenuOpener} guard. See the class
     * javadoc for why {@code Merchant.openTradingScreen} is deliberately not used.
     */
    private static boolean openShop(ServerPlayer player, Level level, BlockPos pos, BlockState state,
            FactionDefinition faction, FactionAccess access, FactionTier tier, MerchantOffers offers) {
        TerminalMerchant merchant = new TerminalMerchant(player, level, pos, state, faction, access, tier, offers);
        merchant.setTradingPlayer(player);
        OptionalInt containerId = MenuOpener.open(player, new SimpleMenuProvider(
                (id, inventory, p) -> new MerchantMenu(id, inventory, merchant) {
                    @Override
                    public boolean stillValid(Player who) {
                        return merchant.stillValid(who);
                    }
                },
                Component.literal(faction.displayName())));
        if (containerId.isPresent()) {
            // What vanilla's openTradingScreen would send after its own openMenu: no trader
            // level badge, no XP bar, no restock — a terminal, not a villager.
            player.sendMerchantOffers(containerId.getAsInt(), offers, 0, 0, false, false);
            return true;
        }
        merchant.setTradingPlayer(null);
        return false;
    }

    // --- offer construction (thin adapter over the pure TerminalOffers core) --------------------

    private static MerchantOffers buildOffers(MinecraftServer server, FactionDefinition faction,
            FactionTier tier, FactionAccess access) {
        List<OfferSpec> specs = TerminalOffers.specs(faction, tier,
                TradeTerminalBlock::vanillaItemsIn,
                NeroFactionsConfig.DISCOUNT_CAP_PERCENT.get(),
                NeroFactionsConfig.SURCHARGE_CAP_PERCENT.get(), access);
        MerchantOffers offers = new MerchantOffers();
        for (OfferSpec spec : specs) {
            Item cost = BuiltInRegistries.ITEM.getValue(spec.costItem());
            if (cost == null || !BuiltInRegistries.ITEM.containsKey(spec.costItem())) {
                continue;
            }
            ItemStack result;
            if (spec.isBanner()) {
                result = FactionBanners.build(server.registryAccess(), faction);
            } else {
                Optional<Identifier> resultId = spec.result();
                if (resultId.isEmpty() || !BuiltInRegistries.ITEM.containsKey(resultId.get())) {
                    continue;
                }
                result = new ItemStack(BuiltInRegistries.ITEM.getValue(resultId.get()),
                        spec.resultCount());
            }
            //? if >=1.20.5 {
            offers.add(new MerchantOffer(new ItemCost(cost, spec.costCount()), result,
                    spec.maxUses(), 1, 0.0F));
            //?} else {
            /*offers.add(new MerchantOffer(new ItemStack(cost, spec.costCount()), result,
                    spec.maxUses(), 1, 0.0F));
            *///?}
        }
        return offers;
    }

    /**
     * The game-side speciality resolver for {@code TerminalOffers}: a trade-modifier tag id to
     * the <b>vanilla</b> items it contains, sorted for determinism — or, when the id names an
     * item directly (the {@code FactionPricing} convention), that one item. Non-vanilla tag
     * members are skipped: the terminal's stock stays installable-mod-agnostic.
     */
    private static List<Identifier> vanillaItemsIn(Identifier tagOrItem) {
        List<Identifier> out = new ArrayList<>();
        TagKey<Item> tag = TagKey.create(Registries.ITEM, tagOrItem);
        //? if >=1.21.2 {
        BuiltInRegistries.ITEM.get(tag).ifPresent(holders -> holders.forEach(holder ->
        //?} else {
        /*BuiltInRegistries.ITEM.getTag(tag).ifPresent(holders -> holders.forEach(holder ->
        *///?}
                holder.unwrapKey().ifPresent(key -> {
                    Identifier id = key.identifier();
                    if ("minecraft".equals(id.getNamespace())) {
                        out.add(id);
                    }
                })));
        if (out.isEmpty() && BuiltInRegistries.ITEM.containsKey(tagOrItem)) {
            out.add(tagOrItem);
        }
        out.sort(null);
        return out;
    }
}
