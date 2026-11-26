package za.co.neroland.nerofactions.network;

//? if <1.20.5 {
/*import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import za.co.neroland.nerolandcore.network.legacy.LegacyPayload;
import za.co.neroland.nerolandcore.network.legacy.LegacyStreamCodec;

public record LegacyVoidReachPayload(int ticks) implements LegacyPayload {
    private static final int MAX_BOON_TICKS = 180 * 20;

    public static final Type<LegacyVoidReachPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("nerofactions", "legacy_void_reach"), LegacyVoidReachPayload.class);
    public static final LegacyStreamCodec<FriendlyByteBuf, LegacyVoidReachPayload> STREAM_CODEC =
            LegacyStreamCodec.of((buffer, payload) -> buffer.writeVarInt(payload.ticks),
                    buffer -> new LegacyVoidReachPayload(buffer.readVarInt()));

    public LegacyVoidReachPayload {
        ticks = Math.max(0, Math.min(MAX_BOON_TICKS, ticks));
    }

    @Override
    public Type<LegacyVoidReachPayload> type() {
        return TYPE;
    }
}
*///?}
