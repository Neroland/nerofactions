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
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerolandcore.data.SavedDataCompat;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

public final class OutpostVisits extends SavedData {
    private static final Identifier ID = Identifier.parse("nerofactions:outpost_visits");

    private record Visit(OutpostFaction faction, Identifier dimension) {
        static final Codec<Visit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                OutpostFaction.CODEC.fieldOf("faction").forGetter(Visit::faction),
                Identifier.CODEC.fieldOf("dimension").forGetter(Visit::dimension)
        ).apply(instance, Visit::new));
    }

    private record PlayerVisits(UUID player, List<Visit> visits) {
        static final Codec<PlayerVisits> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(PlayerVisits::player),
                Visit.CODEC.listOf().fieldOf("visits").forGetter(PlayerVisits::visits)
        ).apply(instance, PlayerVisits::new));
    }

    static final Codec<OutpostVisits> CODEC = PlayerVisits.CODEC.listOf().fieldOf("players")
            .xmap(OutpostVisits::new, OutpostVisits::rows).codec();
    private static final SavedDataType<OutpostVisits> TYPE = SavedDataCompat.type(ID, OutpostVisits::new, CODEC);

    private final Map<UUID, Set<Visit>> visits = new LinkedHashMap<>();

    public OutpostVisits() {
    }

    private OutpostVisits(List<PlayerVisits> rows) {
        for (PlayerVisits row : rows) {
            if (!row.visits().isEmpty()) {
                visits.computeIfAbsent(row.player(), ignored -> new LinkedHashSet<>()).addAll(row.visits());
            }
        }
    }

    public static OutpostVisits get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, OutpostVisits::new, ID.toString());
    }

    public static void eraseFor(MinecraftServer server, UUID player) {
        OutpostVisits state = get(server);
        state.forget(player);
        SavedDataRecovery.backupNow(server.overworld(), TYPE, state, ID.toString());
    }

    public boolean record(UUID player, OutpostFaction faction, Identifier dimension) {
        if (faction == OutpostFaction.NONE) {
            return false;
        }
        boolean added = visits.computeIfAbsent(player, ignored -> new LinkedHashSet<>()).add(new Visit(faction, dimension));
        if (added) {
            setDirty();
        }
        return added;
    }

    public Set<UUID> knownPlayers() {
        return Set.copyOf(visits.keySet());
    }

    void forget(UUID player) {
        if (visits.remove(player) != null) {
            setDirty();
        }
    }

    public JsonArray export(UUID player) {
        JsonArray rows = new JsonArray();
        for (Visit visit : visits.getOrDefault(player, Set.of())) {
            JsonObject row = new JsonObject();
            row.addProperty("faction", visit.faction().factionId().toString());
            row.addProperty("dimension", visit.dimension().toString());
            rows.add(row);
        }
        return rows;
    }

    private List<PlayerVisits> rows() {
        return visits.entrySet().stream()
                .map(row -> new PlayerVisits(row.getKey(), List.copyOf(row.getValue()))).toList();
    }
}
