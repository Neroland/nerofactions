package za.co.neroland.nerofactions.content.faction;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import za.co.neroland.nerofactions.block.TradeTerminalBlock;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.membership.FactionDecay;

public final class FactionEnvoy extends OutpostResident {
    public FactionEnvoy(EntityType<? extends FactionEnvoy> type, Level level) {
        super(type, level);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer visitor && bounds() != null) {
            var server = level().getServer();
            var definition = FactionDefinitions.factionsForServer(server).get(faction().factionId());
            if (definition == null) {
                return InteractionResult.PASS;
            }
            FactionDecay.apply(server, visitor.getUUID());
            var access = FactionAccess.evaluate(server, visitor.getUUID(), definition);
            if (access.refusesService()) {
                visitor.sendSystemMessage(TradeTerminalBlock.refusal(definition, access));
                return InteractionResult.SUCCESS;
            }
            BlockPos origin = blockPosition();
            for (var pos : BlockPos.betweenClosed(origin.offset(-6, -2, -6), origin.offset(6, 2, 6))) {
                if (insideOutpost(pos) && level().hasChunkAt(pos)
                        && TradeTerminalBlock.tradeAtBoundTerminal(visitor, pos.immutable(), faction())) {
                    return InteractionResult.SUCCESS;
                }
            }
            visitor.sendSystemMessage(Component.translatable("message.nerofactions.envoy.terminal_unavailable"));
        }
        return InteractionResult.SUCCESS;
    }
}
