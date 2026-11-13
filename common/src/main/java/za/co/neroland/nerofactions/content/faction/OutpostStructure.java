package za.co.neroland.nerofactions.content.faction;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.data.OutpostVisits;
import za.co.neroland.nerofactions.registry.NeroFactionsWorldgen;
import za.co.neroland.nerolandcore.event.ThresholdEvents;

public final class OutpostStructure extends Structure {
    private static final TagKey<Structure> OUTPOSTS = TagKey.create(Registries.STRUCTURE,
            Identifier.parse("nerofactions:outposts"));
    public static final Identifier FOUND_CHANNEL = Identifier.parse("nerofactions:outpost_found");

    public static final MapCodec<OutpostStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance),
            OutpostFaction.CODEC.fieldOf("faction").forGetter(structure -> structure.faction),
            JigsawStructure.CODEC.codec().fieldOf("layout").forGetter(structure -> structure.layout)
    ).apply(instance, OutpostStructure::new));

    private final OutpostFaction faction;
    private final JigsawStructure layout;

    private OutpostStructure(StructureSettings settings, OutpostFaction faction, JigsawStructure layout) {
        super(settings);
        this.faction = faction;
        this.layout = layout;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return NeroFactionsConfig.outpostEnabled(faction)
                ? layout.findGenerationPoint(context) : Optional.empty();
    }

    @Override
    public StructureType<?> type() {
        return NeroFactionsWorldgen.OUTPOST.get();
    }

    public static StructureStart at(ServerLevel level, BlockPos pos) {
        return level.structureManager().getStructureWithPieceAt(pos, OUTPOSTS);
    }

    public OutpostFaction faction() {
        return faction;
    }

    public static void trackVisits(MinecraftServer server) {
        if (!NeroFactionsConfig.OUTPOST_VISITS_ENABLED.get() || server.overworld().getGameTime() % 40 != 0) {
            return;
        }
        for (var player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            var level = server.getLevel(player.level().dimension());
            if (level == null || !level.hasChunkAt(player.blockPosition())) {
                continue;
            }
            var start = at(level, player.blockPosition());
            if (start.isValid() && start.getStructure() instanceof OutpostStructure outpost
                    && OutpostVisits.get(server).record(player.getUUID(), outpost.faction, level.dimension().identifier())) {
                ThresholdEvents.fire(NeroFactionsCommon.MOD_ID, new ThresholdEvents.ThresholdCrossing(
                        FOUND_CHANNEL, outpost.faction.factionId().toString(), 1, 1, true));
            }
        }
    }
}
