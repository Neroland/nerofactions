package za.co.neroland.nerofactions.content.faction.minerunion;

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
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerolandcore.data.SavedDataCompat;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

public final class QuotaState extends SavedData {
    private static final Identifier ID = Identifier.parse("nerofactions:miner_quota");
    public static final int MAX_QUOTA = 4096;
    private static final long WEEK_MS = 7 * 24 * 60 * 60 * 1000L;

    private record Deposit(UUID player, long week, int tonnes) {
        static final Codec<Deposit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(Deposit::player),
                Codec.LONG.fieldOf("week").forGetter(Deposit::week),
                Codec.intRange(1, MAX_QUOTA).fieldOf("tonnes").forGetter(Deposit::tonnes)
        ).apply(instance, Deposit::new));
    }

    public static final Codec<QuotaState> CODEC = RecordCodecBuilder.create(instance -> instance.group(Deposit.CODEC.listOf()
            .optionalFieldOf("deposits", List.of()).forGetter(QuotaState::deposits)).apply(instance, QuotaState::new));
    private static final SavedDataType<QuotaState> TYPE = SavedDataCompat.type(ID, QuotaState::new, CODEC);
    private final Map<UUID, Deposit> totals = new LinkedHashMap<>();

    public QuotaState() {
    }

    private QuotaState(List<Deposit> deposits) {
        deposits.forEach(deposit -> totals.merge(deposit.player(), deposit, (a, b) ->
                a.week() > b.week() || (a.week() == b.week() && a.tonnes() >= b.tonnes()) ? a : b));
    }

    public static QuotaState get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, QuotaState::new, ID.toString());
    }

    public static void eraseFor(MinecraftServer server, UUID player) {
        QuotaState state = get(server);
        state.forget(player);
        SavedDataRecovery.backupNow(server.overworld(), TYPE, state, ID.toString());
    }

    public static long week(long timestamp) {
        return Math.floorDiv(timestamp, WEEK_MS);
    }

    static int limit(int baseQuota, FactionTier tier) {
        return Math.min(MAX_QUOTA, baseQuota * (tier.ordinal() + 1));
    }

    public int contributed(UUID player, long week) {
        Deposit existing = totals.get(player);
        return existing == null || week > existing.week() ? 0 : existing.tonnes();
    }

    int accept(UUID player, long week, int limit, int itemWeight, int offered) {
        if (itemWeight <= 0 || offered <= 0) {
            return 0;
        }
        int contributed = contributed(player, week);
        int accepted = Math.min(offered, Math.max(0, Math.min(MAX_QUOTA, limit) - contributed) / itemWeight);
        if (accepted == 0) {
            return 0;
        }
        Deposit previous = totals.get(player);
        long effectiveWeek = previous == null ? week : Math.max(week, previous.week());
        totals.put(player, new Deposit(player, effectiveWeek, contributed + accepted * itemWeight));
        setDirty();
        return accepted;
    }

    public Set<UUID> knownPlayers() {
        return Set.copyOf(totals.keySet());
    }

    public void forget(UUID player) {
        if (totals.remove(player) != null) {
            setDirty();
        }
    }

    public JsonObject export(UUID player) {
        var result = new JsonObject();
        Deposit deposit = totals.get(player);
        if (deposit != null) {
            result.addProperty("week", deposit.week());
            result.addProperty("tonnes", deposit.tonnes());
        }
        return result;
    }

    private List<Deposit> deposits() {
        return List.copyOf(totals.values());
    }
}
