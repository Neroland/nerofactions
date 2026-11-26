package za.co.neroland.nerofactions.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record StandingSnapshot(List<Row> factions, long cooldownUntil, int joinCooldownMinutes,
        int switchPenaltyPoints, boolean allowMultipleFactions) implements CustomPacketPayload {
    public static final int MAX_FACTIONS = 64;
    //? if >=1.20.5 {
    public static final Type<StandingSnapshot> TYPE = new Type<>(Identifier.fromNamespaceAndPath("nerofactions", "standing"));
    //?} else {
    /*public static final Type<StandingSnapshot> TYPE = new Type<>(Identifier.fromNamespaceAndPath("nerofactions", "standing"), StandingSnapshot.class);
    *///?}
    public static final StreamCodec<FriendlyByteBuf, StandingSnapshot> STREAM_CODEC = StreamCodec.of(
            StandingSnapshot::write, StandingSnapshot::read);

    public record Row(Identifier faction, String displayName, int standing, int tier,
            int nextThreshold, boolean member, boolean hostile, int threshold,
            String theme, String motto, String color, String icon, String descriptionKey,
            String enemies, String unlocks) {
    }

    public StandingSnapshot {
        factions = List.copyOf(factions);
        if (factions.size() > MAX_FACTIONS) {
            throw new IllegalArgumentException("Too many faction rows");
        }
    }

    @Override
    public Type<StandingSnapshot> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, StandingSnapshot snapshot) {
        buf.writeVarInt(snapshot.factions.size());
        for (Row row : snapshot.factions) {
            buf.writeUtf(row.faction.toString(), 256);
            buf.writeUtf(row.displayName, 256);
            buf.writeInt(row.standing);
            buf.writeVarInt(row.tier);
            buf.writeInt(row.nextThreshold);
            buf.writeBoolean(row.member);
            buf.writeBoolean(row.hostile);
            buf.writeInt(row.threshold);
            buf.writeUtf(row.theme, 256);
            buf.writeUtf(row.motto, 256);
            buf.writeUtf(row.color, 256);
            buf.writeUtf(row.icon, 256);
            buf.writeUtf(row.descriptionKey, 256);
            buf.writeUtf(row.enemies, 256);
            buf.writeUtf(row.unlocks, 256);
        }
        buf.writeLong(snapshot.cooldownUntil);
        buf.writeVarInt(snapshot.joinCooldownMinutes);
        buf.writeVarInt(snapshot.switchPenaltyPoints);
        buf.writeBoolean(snapshot.allowMultipleFactions);
    }

    private static StandingSnapshot read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_FACTIONS) {
            throw new IllegalArgumentException("Invalid faction row count");
        }
        List<Row> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Identifier id = Identifier.tryParse(buf.readUtf(256));
            String name = buf.readUtf(256);
            int standing = buf.readInt();
            int tier = buf.readVarInt();
            if (id == null || tier < 0 || tier > 4) {
                throw new IllegalArgumentException("Invalid faction row");
            }
            rows.add(new Row(id, name, standing, tier, buf.readInt(), buf.readBoolean(), buf.readBoolean(),
                    buf.readInt(), buf.readUtf(256), buf.readUtf(256), buf.readUtf(256), buf.readUtf(256),
                    buf.readUtf(256), buf.readUtf(256), buf.readUtf(256)));
        }
        return new StandingSnapshot(rows, buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }
}
