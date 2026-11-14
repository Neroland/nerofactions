package za.co.neroland.nerofactions.content.faction.terraformingauthority;

import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.crafting.GatedRecipeCheck;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

//? if >=1.21.5 {
public final class SurveySpade extends Item {
//?} else {
/*public final class SurveySpade extends net.minecraft.world.item.ShovelItem {
*///?}
    public SurveySpade(Properties properties) {
        //? if >=1.21.5 {
        super(properties.shovel(net.minecraft.world.item.ToolMaterial.IRON, 1.5F, -3));
        //?} else if >=1.21.2 {
        /*super(net.minecraft.world.item.ToolMaterial.IRON, 1.5F, -3, properties);
        *///?} else {
        /*super(net.minecraft.world.item.Tiers.IRON,
                properties.attributes(createAttributes(net.minecraft.world.item.Tiers.IRON, 1.5F, -3)));
        *///?}
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown()) return super.useOn(context);
        if (context.getPlayer() instanceof ServerPlayer player) {
            if (!(context.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return InteractionResult.PASS;
            var server = level.getServer();
            var faction = OutpostFaction.TERRAFORMING_AUTHORITY.factionId();
            if (server == null || !FactionMembership.membershipOf(server, player.getUUID()).contains(faction)
                    || !GatedRecipeCheck.standingMet(FactionDefinitions.factionsForServer(server).get(faction),
                            ReputationApi.getReputation(player.getUUID(), faction), FactionTier.MEMBER)) {
                player.sendSystemMessage(Component.translatable("message.nerofactions.survey.unavailable"));
                return InteractionResult.SUCCESS;
            }
            var position = context.getClickedPos();
            if (!level.hasChunkAt(position)) return InteractionResult.PASS;
            var biome = level.getBiome(position);
            var name = biome.unwrapKey().map(key -> key.identifier().toString()).orElse("unregistered");
            player.sendSystemMessage(Component.translatable("message.nerofactions.survey.reading",
                    name, Reading.temperature(biome.value().getBaseTemperature())));
            za.co.neroland.nerofactions.integration.Integrations.atmosphere(level, position)
                    .ifPresent(player::sendSystemMessage);
        }
        return InteractionResult.SUCCESS;
    }

    static final class Reading {
        static String temperature(float base) {
            return String.format(Locale.ROOT, "%.2f", base);
        }
    }
}
