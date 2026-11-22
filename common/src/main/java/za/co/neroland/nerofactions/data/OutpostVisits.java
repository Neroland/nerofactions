package za.co.neroland.nerofactions.data;

import java.util.ArrayList;
import java.util.Iterator;
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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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

    /**
     * One outpost a player has stood inside: its dimension, faction and footprint (x/z box plus the centre's height).
     * No time and nothing else about the player; the footprint is a worldgen structure's, not a player's base.
     */
    public record Discovered(Identifier dimension, OutpostFaction faction, int minX, int minZ, int maxX, int maxZ,
            int y) {
        static final Codec<Discovered> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("dimension").forGetter(Discovered::dimension),
                OutpostFaction.CODEC.fieldOf("faction").forGetter(Discovered::faction),
                Codec.INT.fieldOf("min_x").forGetter(Discovered::minX),
                Codec.INT.fieldOf("min_z").forGetter(Discovered::minZ),
                Codec.INT.fieldOf("max_x").forGetter(Discovered::maxX),
                Codec.INT.fieldOf("max_z").forGetter(Discovered::maxZ),
                Codec.INT.fieldOf("y").forGetter(Discovered::y)
        ).apply(instance, Discovered::new));

        String key() {
            return dimension + "|" + minX + "|" + minZ + "|" + maxX + "|" + maxZ;
        }

        public int centreX() {
            return Math.floorDiv(minX + maxX, 2);
        }

        public int centreZ() {
            return Math.floorDiv(minZ + maxZ, 2);
        }
    }

    private record PlayerVisits(UUID player, List<Visit> visits, List<Discovered> outposts) {
        static final Codec<PlayerVisits> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(PlayerVisits::player),
                Visit.CODEC.listOf().fieldOf("visits").forGetter(PlayerVisits::visits),
                // Optional with an empty default so saves from before discovered outposts load unchanged.
                Discovered.CODEC.listOf().optionalFieldOf("outposts", List.of()).forGetter(PlayerVisits::outposts)
        ).apply(instance, PlayerVisits::new));
    }

    /** At most this many discovered outposts per player; the oldest is dropped when a new one is found. */
    public static final int MAX_DISCOVERED = 256;

    static final Codec<OutpostVisits> CODEC = PlayerVisits.CODEC.listOf().fieldOf("players")
            .xmap(OutpostVisits::new, OutpostVisits::rows).codec();
    private static final SavedDataType<OutpostVisits> TYPE = SavedDataCompat.type(ID, OutpostVisits::new, CODEC);

    private final Map<UUID, Set<Visit>> visits = new LinkedHashMap<>();
    private final Map<UUID, LinkedHashMap<String, Discovered>> discovered = new LinkedHashMap<>();

    public OutpostVisits() {
    }

    private OutpostVisits(List<PlayerVisits> rows) {
        for (PlayerVisits row : rows) {
            if (!row.visits().isEmpty()) {
                visits.computeIfAbsent(row.player(), ignored -> new LinkedHashSet<>()).addAll(row.visits());
            }
            for (Discovered outpost : row.outposts()) {
                addDiscovered(row.player(), outpost);
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

    /**
     * Records that {@code player} stood inside the outpost with footprint {@code box} (centre height {@code y}).
     * Capped at {@link #MAX_DISCOVERED} per player, oldest dropped. @return whether the outpost is new for them.
     */
    public boolean discover(UUID player, OutpostFaction faction, Identifier dimension, BoundingBox box, int y) {
        if (player == null || faction == null || faction == OutpostFaction.NONE || dimension == null || box == null) {
            return false;
        }
        boolean added = addDiscovered(player, new Discovered(dimension, faction, box.minX(), box.minZ(), box.maxX(),
                box.maxZ(), y));
        if (added) {
            setDirty();
        }
        return added;
    }

    private boolean addDiscovered(UUID player, Discovered outpost) {
        LinkedHashMap<String, Discovered> rows = discovered.computeIfAbsent(player, ignored -> new LinkedHashMap<>());
        if (rows.putIfAbsent(outpost.key(), outpost) != null) {
            return false;
        }
        Iterator<String> oldest = rows.keySet().iterator();
        while (rows.size() > MAX_DISCOVERED && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
        return true;
    }

    /** {@code player}'s own discovered outposts in {@code dimension}; never anyone else's. */
    public List<Discovered> discovered(UUID player, Identifier dimension) {
        LinkedHashMap<String, Discovered> rows = player == null ? null : discovered.get(player);
        if (rows == null || dimension == null) {
            return List.of();
        }
        List<Discovered> out = new ArrayList<>();
        for (Discovered row : rows.values()) {
            if (row.dimension().equals(dimension)) {
                out.add(row);
            }
        }
        return out;
    }

    public Set<UUID> knownPlayers() {
        Set<UUID> known = new LinkedHashSet<>(visits.keySet());
        known.addAll(discovered.keySet());
        return Set.copyOf(known);
    }

    void forget(UUID player) {
        boolean removed = visits.remove(player) != null;
        removed |= discovered.remove(player) != null;
        if (removed) {
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

    /**
     * Subject-access export: the faction visits ({@link #export}) followed by the discovered outposts (dimension,
     * faction, footprint). The link module keeps using {@link #export}, which carries no coordinates.
     */
    public JsonArray exportAll(UUID player) {
        JsonArray rows = export(player);
        LinkedHashMap<String, Discovered> own = discovered.get(player);
        if (own == null) {
            return rows;
        }
        for (Discovered outpost : own.values()) {
            JsonObject row = new JsonObject();
            row.addProperty("discovered_outpost", true);
            row.addProperty("dimension", outpost.dimension().toString());
            row.addProperty("faction", outpost.faction().factionId().toString());
            row.addProperty("min_x", outpost.minX());
            row.addProperty("min_z", outpost.minZ());
            row.addProperty("max_x", outpost.maxX());
            row.addProperty("max_z", outpost.maxZ());
            rows.add(row);
        }
        return rows;
    }

    private List<PlayerVisits> rows() {
        List<PlayerVisits> rows = new ArrayList<>();
        for (UUID player : knownPlayers()) {
            LinkedHashMap<String, Discovered> found = discovered.get(player);
            rows.add(new PlayerVisits(player, List.copyOf(visits.getOrDefault(player, Set.of())),
                    found == null ? List.of() : List.copyOf(found.values())));
        }
        return rows;
    }
}
