package za.co.neroland.nerofactions.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.content.faction.OutpostResident;
import za.co.neroland.nerofactions.content.faction.FactionEnvoy;
import za.co.neroland.nerofactions.content.faction.FactionGuard;
import za.co.neroland.nerolandcore.entity.EntityRegistrationSupport;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

public final class NeroFactionsEntities {
    private static final RegistrationProvider<EntityType<?>> TYPES =
            RegistrationProvider.get(Registries.ENTITY_TYPE, NeroFactionsCommon.MOD_ID);

    public static final RegistryEntry<EntityType<FactionEnvoy>> ENVOY = TYPES.register("faction_envoy",
            key -> resident(key, FactionEnvoy::new));
    public static final RegistryEntry<EntityType<FactionGuard>> GUARD = TYPES.register("faction_guard",
            key -> resident(key, FactionGuard::new));

    private static <T extends OutpostResident> EntityType<T> resident(ResourceKey<EntityType<?>> key,
            EntityType.EntityFactory<T> factory) {
        var builder = EntityType.Builder.of(factory, MobCategory.MISC)
                .sized(0.6F, 1.95F).clientTrackingRange(8);
        //? if >=1.21.2 {
        return builder.build(key);
        //?} else {
        /*return builder.build(key.identifier().toString());
        *///?}
    }

    private NeroFactionsEntities() {
    }

    public static void init() {
        EntityRegistrationSupport support = EntityRegistrationSupport.get(NeroFactionsCommon.MOD_ID);
        support.registerAttributes(ENVOY, OutpostResident::attributes);
        support.registerAttributes(GUARD, FactionGuard::attributes);
    }
}
