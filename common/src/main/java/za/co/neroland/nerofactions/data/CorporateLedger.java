package za.co.neroland.nerofactions.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import za.co.neroland.nerolandcore.data.SavedDataCompat;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

public final class CorporateLedger extends SavedData {
    private static final Identifier ID = Identifier.parse("nerofactions:corporate_ledger");
    private static final int STAMP_CAP = 3;

    private record Usage(UUID player, long day, boolean delivered, int stamps) {
        static final Codec<Usage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(Usage::player),
                Codec.LONG.fieldOf("day").forGetter(Usage::day),
                Codec.BOOL.fieldOf("delivered").forGetter(Usage::delivered),
                Codec.intRange(0, STAMP_CAP).fieldOf("stamps").forGetter(Usage::stamps)
        ).apply(instance, Usage::new));
    }

    static final Codec<CorporateLedger> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Usage.CODEC.listOf().optionalFieldOf("usage", List.of()).forGetter(data -> List.copyOf(data.usage.values())),
            Codec.LONG.optionalFieldOf("swept_through", Long.MIN_VALUE).forGetter(data -> data.sweptThrough)
    ).apply(instance, CorporateLedger::new));
    private static final SavedDataType<CorporateLedger> TYPE = SavedDataCompat.type(ID, CorporateLedger::new, CODEC);
    private final Map<UUID, Usage> usage = new LinkedHashMap<>();
    private long sweptThrough = Long.MIN_VALUE;

    CorporateLedger() {}

    private CorporateLedger(List<Usage> rows, long sweptThrough) {
        this.sweptThrough = sweptThrough;
        for (var row : rows) usage.merge(row.player(), row, (first, second) -> {
            if (first.day() != second.day()) return first.day() > second.day() ? first : second;
            return new Usage(first.player(), first.day(), first.delivered() || second.delivered(),
                    Math.max(first.stamps(), second.stamps()));
        });
    }

    public static CorporateLedger get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, CorporateLedger::new, ID.toString());
    }

    public static void eraseFor(MinecraftServer server, UUID player) {
        var ledger = get(server);
        ledger.forget(player);
        SavedDataRecovery.backupNow(server.overworld(), TYPE, ledger, ID.toString());
    }

    public void expire(long day) {
        if (day <= sweptThrough) return;
        sweptThrough = day;
        usage.values().removeIf(row -> row.day() < day);
        setDirty();
    }

    public long currentDay(long day) { return Math.max(sweptThrough, day); }

    public boolean delivered(UUID player, long day) {
        var row = usage.get(player);
        return row != null && row.day() >= day && row.delivered();
    }

    public boolean reserveDelivery(UUID player, long day) {
        expire(day);
        var row = today(player, day);
        if (row.delivered()) return false;
        usage.put(player, new Usage(player, row.day(), true, row.stamps()));
        setDirty();
        return true;
    }

    public boolean reserveStamp(UUID player, long day) {
        expire(day);
        var row = today(player, day);
        if (row.stamps() >= STAMP_CAP) return false;
        usage.put(player, new Usage(player, row.day(), row.delivered(), row.stamps() + 1));
        setDirty();
        return true;
    }

    private Usage today(UUID player, long day) {
        long current = currentDay(day);
        var row = usage.get(player);
        return row == null || current > row.day() ? new Usage(player, current, false, 0) : row;
    }

    public Set<UUID> knownPlayers() { return Set.copyOf(usage.keySet()); }

    void forget(UUID player) {
        if (usage.remove(player) != null) setDirty();
    }

    public JsonObject export(UUID player) {
        var result = new JsonObject();
        var row = usage.get(player);
        if (row != null) {
            result.addProperty("day", row.day());
            result.addProperty("delivered", row.delivered());
            result.addProperty("stamps", row.stamps());
        }
        return result;
    }
}
