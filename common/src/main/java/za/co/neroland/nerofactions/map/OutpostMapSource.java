package za.co.neroland.nerofactions.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.FactionTiers;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.content.faction.OutpostStructure;
import za.co.neroland.nerofactions.data.OutpostVisits;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

/**
 * What the outpost layer reads, behind one seam so tests can seed outposts without a world. The live source looks
 * only at chunks that are already loaded around the viewer (never loads or generates one) and at the viewer's own
 * standing.
 */
public interface OutpostMapSource {

    /** An outpost structure: a stable key (its start chunk), its faction and its footprint. */
    record Seen(long startChunk, OutpostFaction faction, int minX, int minZ, int maxX, int maxZ, BlockPos centre) {
    }

    /** Outposts whose start is recorded in a loaded chunk within {@code radius} blocks of {@code around}. */
    List<Seen> near(ResourceKey<Level> dimension, BlockPos around, int radius);

    /** The viewer's own standing with an outpost's faction. */
    OutpostStanding standing(UUID viewer, OutpostFaction faction);

    /**
     * Outposts {@code viewer} has stood inside in {@code dimension}, from their own visit record only; never another
     * player's. None by default.
     */
    default List<Seen> discovered(UUID viewer, ResourceKey<Level> dimension) {
        return List.of();
    }

    /** A stable key for a footprint seen from a visit record: its minimum corner, packed like a chunk key. */
    static long footprintKey(int minX, int minZ) {
        return (minX & 0xFFFFFFFFL) | ((minZ & 0xFFFFFFFFL) << 32);
    }

    /** The live source for {@code server}, or {@code null} without one. */
    static OutpostMapSource of(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return new OutpostMapSource() {
            @Override
            public List<Seen> near(ResourceKey<Level> dimension, BlockPos around, int radius) {
                ServerLevel level = server.getLevel(dimension);
                return level == null ? List.of() : scan(level, around, radius);
            }

            @Override
            public List<Seen> discovered(UUID viewer, ResourceKey<Level> dimension) {
                if (viewer == null || dimension == null) {
                    return List.of();
                }
                List<Seen> out = new ArrayList<>();
                for (OutpostVisits.Discovered row : OutpostVisits.get(server).discovered(viewer,
                        dimension.identifier())) {
                    out.add(new Seen(footprintKey(row.minX(), row.minZ()), row.faction(), row.minX(), row.minZ(),
                            row.maxX(), row.maxZ(), new BlockPos(row.centreX(), row.y(), row.centreZ())));
                }
                return out;
            }

            @Override
            public OutpostStanding standing(UUID viewer, OutpostFaction faction) {
                Identifier id = faction.factionId();
                int value = ReputationApi.getReputation(viewer, id);
                if (FactionTiers.isHostile(value, NeroFactionsConfig.HOSTILE_BELOW_STANDING.get())) {
                    return OutpostStanding.HOSTILE;
                }
                FactionDefinition definition = FactionDefinitions.factionsForServer(server).get(id);
                if (definition != null && FactionTiers.tierOf(definition, value).ordinal()
                        >= FactionTier.ASSOCIATE.ordinal()) {
                    return OutpostStanding.FRIENDLY;
                }
                return OutpostStanding.NEUTRAL;
            }
        };
    }

    /** Outpost starts referenced by the loaded chunks around {@code around}; loaded chunks only. */
    private static List<Seen> scan(ServerLevel level, BlockPos around, int radius) {
        Map<Long, Seen> found = new LinkedHashMap<>();
        int minCx = (around.getX() - radius) >> 4;
        int maxCx = (around.getX() + radius) >> 4;
        int minCz = (around.getZ() - radius) >> 4;
        int maxCz = (around.getZ() + radius) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (Map.Entry<Structure, LongSet> entry : chunk.getAllReferences().entrySet()) {
                    if (!(entry.getKey() instanceof OutpostStructure outpost)) {
                        continue;
                    }
                    for (long ref : entry.getValue()) {
                        if (found.containsKey(ref)) {
                            continue;
                        }
                        LevelChunk startChunk = level.getChunkSource().getChunkNow(ChunkPos.getX(ref),
                                ChunkPos.getZ(ref));
                        StructureStart start = startChunk == null ? null : startChunk.getStartForStructure(outpost);
                        if (start == null || !start.isValid()) {
                            continue;
                        }
                        BoundingBox box = start.getBoundingBox();
                        found.put(ref, new Seen(ref, outpost.faction(), box.minX(), box.minZ(), box.maxX(),
                                box.maxZ(), box.getCenter()));
                    }
                }
            }
        }
        return new ArrayList<>(found.values());
    }
}
