package za.co.neroland.nerofactions.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import za.co.neroland.nerofactions.menu.ContractBoardMenu;

public record ContractIntent(int menuId, Identifier faction, Identifier contract, long day, int slot, int action) implements CustomPacketPayload {
    public static final Type<ContractIntent> TYPE = new Type<>(Identifier.parse("nerofactions:contract_intent"));
    public static final StreamCodec<FriendlyByteBuf, ContractIntent> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.menuId); buf.writeUtf(p.faction.toString(), 256);
                buf.writeUtf(p.contract.toString(), 256); buf.writeLong(p.day);
                buf.writeVarInt(p.slot); buf.writeVarInt(p.action); },
            buf -> new ContractIntent(buf.readVarInt(), Identifier.tryParse(buf.readUtf(256)),
                    Identifier.tryParse(buf.readUtf(256)), buf.readLong(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<ContractIntent> type() { return TYPE; }

    public static void handle(ContractIntent intent, ServerPlayer player) {
        if (!intent.valid()) return;
        if (player.containerMenu instanceof ContractBoardMenu board && board.containerId == intent.menuId) {
            board.act(player, intent);
        }
    }

    public boolean valid() {
        return faction != null && contract != null && slot >= 0 && slot <= 2 && action >= 0 && action <= 2;
    }

    public boolean matches(int expectedMenu, long expectedDay, za.co.neroland.nerofactions.data.ContractState.Entry entry) {
        return valid() && menuId == expectedMenu && day == expectedDay && entry != null && entry.slot() == slot
                && entry.contract().id().equals(contract) && entry.contract().faction().equals(faction);
    }
}
