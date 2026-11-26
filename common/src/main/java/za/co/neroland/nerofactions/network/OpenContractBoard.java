package za.co.neroland.nerofactions.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import za.co.neroland.nerofactions.menu.RegistryMenu;

public record OpenContractBoard(int menuId) implements CustomPacketPayload {
    //? if >=1.20.5 {
    public static final Type<OpenContractBoard> TYPE = new Type<>(Identifier.parse("nerofactions:open_contract_board"));
    //?} else {
    /*public static final Type<OpenContractBoard> TYPE = new Type<>(Identifier.parse("nerofactions:open_contract_board"), OpenContractBoard.class);
    *///?}
    public static final StreamCodec<FriendlyByteBuf, OpenContractBoard> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.menuId), buf -> new OpenContractBoard(buf.readVarInt()));

    @Override
    public Type<OpenContractBoard> type() { return TYPE; }

    public static void handle(OpenContractBoard request, ServerPlayer player) {
        if (player.containerMenu instanceof RegistryMenu registry && registry.containerId == request.menuId) registry.openContracts(player);
    }
}
