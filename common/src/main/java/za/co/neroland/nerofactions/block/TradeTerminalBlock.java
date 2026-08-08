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
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.FactionTiers;
import za.co.neroland.nerofactions.cosmetics.FactionBanners;
import za.co.neroland.nerofactions.membership.FactionDecay;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerofactions.menu.MenuOpener;
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

    public TradeTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
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

        FactionTier tier = FactionTiers.tierOf(faction,
                ReputationApi.getReputation(playerId, chosen));
        MerchantOffers offers = buildOffers(server, faction, tier);
        if (switched) {
            serverPlayer.sendSystemMessage(Component.translatable(
                    "message.nerofactions.terminal.switched", faction.displayName()));
        }
        openShop(serverPlayer, level, pos, faction, offers);
        return InteractionResult.SUCCESS;
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
    private static void openShop(ServerPlayer player, Level level, BlockPos pos,
            FactionDefinition faction, MerchantOffers offers) {
        TerminalMerchant merchant = new TerminalMerchant(player, level, pos,
                level.getBlockState(pos).getBlock(), faction, offers);
        merchant.setTradingPlayer(player);
        OptionalInt containerId = MenuOpener.open(player, new SimpleMenuProvider(
                (id, inventory, p) -> new MerchantMenu(id, inventory, merchant),
                Component.literal(faction.displayName())));
        if (containerId.isPresent()) {
            // What vanilla's openTradingScreen would send after its own openMenu: no trader
            // level badge, no XP bar, no restock — a terminal, not a villager.
            player.sendMerchantOffers(containerId.getAsInt(), offers, 0, 0, false, false);
        } else {
            merchant.setTradingPlayer(null);
        }
    }

    // --- offer construction (thin adapter over the pure TerminalOffers core) --------------------

    private static MerchantOffers buildOffers(MinecraftServer server, FactionDefinition faction,
            FactionTier tier) {
        List<OfferSpec> specs = TerminalOffers.specs(faction, tier,
                TradeTerminalBlock::vanillaItemsIn,
                NeroFactionsConfig.DISCOUNT_CAP_PERCENT.get(),
                NeroFactionsConfig.SURCHARGE_CAP_PERCENT.get());
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
            offers.add(new MerchantOffer(new ItemCost(cost, spec.costCount()), result,
                    spec.maxUses(), 1, 0.0F));
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
        BuiltInRegistries.ITEM.get(tag).ifPresent(holders -> holders.forEach(holder ->
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
