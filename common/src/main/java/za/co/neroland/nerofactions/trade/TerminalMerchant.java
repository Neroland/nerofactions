package za.co.neroland.nerofactions.trade;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.reputation.ReputationSources;
import za.co.neroland.nerofactions.reputation.ReputationSources.Source;

/**
 * The trade terminal's <b>per-interaction virtual merchant</b> — the lightweight {@link Merchant}
 * a {@code MerchantMenu} needs, backed by a block instead of an entity (the AlienVillager pattern
 * from Nerospace, minus the entity). One instance exists per right-click and dies with the menu;
 * nothing about it is persisted, so the block needs no block entity.
 *
 * <p><b>Server-authoritative.</b> Instances are only ever constructed server-side by
 * {@code TradeTerminalBlock}; the offers were resolved from server state (standing, faction
 * definitions) before the menu opened, and every trade completion is validated by the vanilla
 * menu against this merchant's own offer list.
 *
 * <p><b>The earning loop.</b> {@link #notifyTrade} — vanilla's completed-trade callback — awards
 * {@code Source.TRADE} reputation via {@link ReputationSources#award} (weighted, daily-capped,
 * enemy-bled like every other source) with the config {@code tradeAwardBase} ({@code 0}
 * disables). Selling the faction its speciality goods therefore <em>is</em> the gather/deliver
 * reputation loop.
 *
 * <p><b>POPIA/GDPR:</b> nothing here logs or stores anything; the reputation write goes through
 * the same audited stores as every other award.
 */
public final class TerminalMerchant implements Merchant {

    private static final double MAX_DISTANCE_SQ = 64.0D; // 8 blocks, the vanilla container norm

    private final ServerPlayer player;
    private final Level level;
    private final BlockPos pos;
    private final Block terminalBlock;
    private final FactionDefinition faction;
    private final MerchantOffers offers;

    private Player tradingPlayer;

    public TerminalMerchant(ServerPlayer player, Level level, BlockPos pos, Block terminalBlock,
            FactionDefinition faction, MerchantOffers offers) {
        this.player = player;
        this.level = level;
        this.pos = pos;
        this.terminalBlock = terminalBlock;
        this.faction = faction;
        this.offers = offers;
    }

    @Override
    public void setTradingPlayer(Player tradingPlayer) {
        this.tradingPlayer = tradingPlayer;
    }

    @Override
    public Player getTradingPlayer() {
        return tradingPlayer;
    }

    @Override
    public MerchantOffers getOffers() {
        return offers;
    }

    @Override
    public void overrideOffers(MerchantOffers newOffers) {
        // Per-interaction merchant: the offers were resolved when the menu opened and are final.
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        int base = NeroFactionsConfig.TRADE_AWARD_BASE.get();
        MinecraftServer server = level.getServer();
        if (base > 0 && server != null) {
            ReputationSources.award(server, player.getUUID(), faction.id(), Source.TRADE, base);
        }
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
        // No price-demand simulation for a terminal.
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
        // Terminals have no trader XP.
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean isClientSide() {
        return level.isClientSide();
    }

    @Override
    public boolean stillValid(Player who) {
        return tradingPlayer == who
                && who.isAlive()
                && who.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                        <= MAX_DISTANCE_SQ
                && level.getBlockState(pos).is(terminalBlock);
    }
}
