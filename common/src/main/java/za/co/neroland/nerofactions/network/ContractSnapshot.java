package za.co.neroland.nerofactions.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ContractSnapshot(List<Row> entries, long day, boolean rerollAvailable, String feedback) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 192;
    public static final Type<ContractSnapshot> TYPE = new Type<>(Identifier.parse("nerofactions:contracts"));
    public static final StreamCodec<FriendlyByteBuf, ContractSnapshot> STREAM_CODEC = StreamCodec.of(
            ContractSnapshot::write, ContractSnapshot::read);

    public record Row(Identifier faction, Identifier id, int slot, String type, String target,
            int progress, int count, int reputation, long credits, boolean claimed) {
    }

    public ContractSnapshot { entries = List.copyOf(entries); }

    @Override
    public Type<ContractSnapshot> type() { return TYPE; }

    private static void write(FriendlyByteBuf buf, ContractSnapshot snapshot) {
        if (snapshot.entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many contracts");
        buf.writeVarInt(snapshot.entries.size());
        for (Row row : snapshot.entries) {
            buf.writeUtf(row.faction.toString(), 256);
            buf.writeUtf(row.id.toString(), 256);
            buf.writeVarInt(row.slot);
            buf.writeUtf(row.type, 32);
            buf.writeUtf(row.target, 256);
            buf.writeVarInt(row.progress);
            buf.writeVarInt(row.count);
            buf.writeVarInt(row.reputation);
            buf.writeLong(row.credits);
            buf.writeBoolean(row.claimed);
        }
        buf.writeLong(snapshot.day);
        buf.writeBoolean(snapshot.rerollAvailable);
        buf.writeUtf(snapshot.feedback, 128);
    }

    private static ContractSnapshot read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) throw new IllegalArgumentException("Invalid contract list size");
        var rows = new ArrayList<Row>(size);
        for (int i = 0; i < size; i++) {
            var faction = Identifier.tryParse(buf.readUtf(256));
            var id = Identifier.tryParse(buf.readUtf(256));
            int slot = buf.readVarInt();
            String type = buf.readUtf(32);
            String target = buf.readUtf(256);
            int progress = buf.readVarInt();
            int count = buf.readVarInt();
            int reputation = buf.readVarInt();
            long credits = buf.readLong();
            boolean claimed = buf.readBoolean();
            if (faction == null || id == null || slot < 0 || slot > 2 || count < 1 || count > 4096
                    || progress < 0 || progress > count || reputation < 0 || reputation > 10000
                    || credits < 0 || credits > 1000000
                    || !java.util.Set.of("deliver", "kill", "craft", "visit_structure").contains(type)) {
                throw new IllegalArgumentException("Invalid contract row");
            }
            rows.add(new Row(faction, id, slot, type, target, progress, count, reputation, credits, claimed));
        }
        return new ContractSnapshot(rows, buf.readLong(), buf.readBoolean(), buf.readUtf(128));
    }
}
