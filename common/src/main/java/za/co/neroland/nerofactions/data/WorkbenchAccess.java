package za.co.neroland.nerofactions.data;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
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

public final class WorkbenchAccess extends SavedData {
    private static final Identifier ID = Identifier.parse("nerofactions:workbench_access");
    private static final int MAX_GUESTS = 16;
    private static final Codec<List<UUID>> GUESTS = UUIDUtil.CODEC.listOf().flatXmap(
            WorkbenchAccess::checkGuests, WorkbenchAccess::checkGuests);

    private static com.mojang.serialization.DataResult<List<UUID>> checkGuests(List<UUID> guests) {
        return guests.size() <= MAX_GUESTS ? com.mojang.serialization.DataResult.success(guests)
                : com.mojang.serialization.DataResult.error(() -> "Too many workbench guests");
    }

    private record Grant(UUID bench, UUID owner, List<UUID> guests) {
        static final Codec<Grant> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("bench").forGetter(Grant::bench),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Grant::owner),
                GUESTS.optionalFieldOf("guests", List.of()).forGetter(Grant::guests)
        ).apply(instance, Grant::new));
    }

    static final Codec<WorkbenchAccess> CODEC = Grant.CODEC.listOf().optionalFieldOf("benches", List.of())
            .xmap(WorkbenchAccess::new, state -> List.copyOf(state.grants.values())).codec();
    private static final SavedDataType<WorkbenchAccess> TYPE = SavedDataCompat.type(ID, WorkbenchAccess::new, CODEC);
    private final Map<UUID, Grant> grants = new LinkedHashMap<>();

    WorkbenchAccess() {}

    private WorkbenchAccess(List<Grant> saved) {
        for (var grant : saved) grants.putIfAbsent(grant.bench(), grant);
    }

    public static WorkbenchAccess get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, WorkbenchAccess::new, ID.toString());
    }

    public static void eraseFor(MinecraftServer server, UUID player) {
        var access = get(server);
        access.forget(player);
        SavedDataRecovery.backupNow(server.overworld(), TYPE, access, ID.toString());
    }

    public boolean claim(UUID bench, UUID owner) {
        if (grants.putIfAbsent(bench, new Grant(bench, owner, List.of())) != null) return false;
        setDirty();
        return true;
    }

    public boolean permits(UUID bench, UUID player) {
        var grant = grants.get(bench);
        return grant != null && (grant.owner().equals(player) || grant.guests().contains(player));
    }

    public boolean setGuest(UUID bench, UUID owner, UUID guest, boolean allow) {
        var grant = grants.get(bench);
        if (grant == null || !grant.owner().equals(owner) || owner.equals(guest)) return false;
        var guests = new LinkedHashSet<>(grant.guests());
        if (allow ? guests.size() >= MAX_GUESTS || !guests.add(guest) : !guests.remove(guest)) return false;
        grants.put(bench, new Grant(bench, owner, List.copyOf(guests)));
        setDirty();
        return true;
    }

    public static void removeBench(MinecraftServer server, UUID bench) {
        var access = get(server);
        if (access.grants.remove(bench) != null) {
            access.setDirty();
            SavedDataRecovery.backupNow(server.overworld(), TYPE, access, ID.toString());
        }
    }

    boolean forget(UUID player) {
        boolean changed = false;
        var iterator = grants.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var grant = entry.getValue();
            if (grant.owner().equals(player)) {
                iterator.remove();
                changed = true;
            } else if (grant.guests().contains(player)) {
                var guests = grant.guests().stream().filter(guest -> !guest.equals(player)).toList();
                entry.setValue(new Grant(grant.bench(), grant.owner(), guests));
                changed = true;
            }
        }
        if (changed) setDirty();
        return changed;
    }

    public Set<UUID> knownPlayers() {
        var players = new LinkedHashSet<UUID>();
        for (var grant : grants.values()) {
            players.add(grant.owner());
            players.addAll(grant.guests());
        }
        return players;
    }

    public JsonObject export(UUID player) {
        var result = new JsonObject();
        var owned = new JsonArray();
        var invited = new JsonArray();
        for (var grant : grants.values()) {
            if (grant.owner().equals(player)) owned.add(grant.bench().toString());
            if (grant.guests().contains(player)) invited.add(grant.bench().toString());
        }
        result.add("owned", owned);
        result.add("invited", invited);
        return result;
    }
}
