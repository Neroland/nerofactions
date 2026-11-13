package za.co.neroland.nerofactions.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.content.faction.OutpostStructure;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;

public final class NeroFactionsWorldgen {
    private static final RegistrationProvider<StructureType<?>> STRUCTURES =
            RegistrationProvider.get(Registries.STRUCTURE_TYPE, NeroFactionsCommon.MOD_ID);

    public static final RegistrationProvider.RegistryEntry<StructureType<OutpostStructure>> OUTPOST =
            STRUCTURES.register("faction_outpost", key -> () -> OutpostStructure.CODEC);

    private NeroFactionsWorldgen() {
    }

    public static void init() {
    }
}
