package za.co.neroland.nerofactions.registry;

import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.crafting.GatedRecipe;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;

/**
 * Recipe-serializer registration through Core's {@link RegistrationProvider} seam (the Nerotech
 * {@code ModRecipeTypes} pattern). Only a serializer is registered — {@link GatedRecipe} is a
 * {@code minecraft:crafting}-type recipe, so no custom {@code RecipeType} exists and every vanilla
 * crafting lookup finds it.
 *
 * <p>On NeoForge/Forge the underlying {@code DeferredRegister}s are driven by
 * {@code RegistrationProvider.attach(...)} in the loader entry points; on Fabric registration
 * applies eagerly at class load, which is why {@link #init()} is called from
 * {@code NeroFactionsCommon.init()} step 3 — before anything that could resolve recipes.
 */
public final class NeroFactionsRecipes {

    public static final RegistrationProvider<RecipeSerializer<?>> SERIALIZERS =
            RegistrationProvider.get(Registries.RECIPE_SERIALIZER, NeroFactionsCommon.MOD_ID);

    /** {@code nerofactions:gated} — the standing-gated crafting recipe wrapper. */
    public static final RegistrationProvider.RegistryEntry<RecipeSerializer<GatedRecipe>> GATED =
            gatedSerializer();

    private NeroFactionsRecipes() {
    }

    /** Classload-forcing no-op: touching this class runs the static registrations above. */
    public static void init() {
    }

    /**
     * The ecosystem idiom for the serializer-references-itself cycle: the codecs capture a
     * supplier that reads the {@link AtomicReference} filled the moment the serializer instance
     * exists, so registration order is irrelevant on every loader.
     */
    private static RegistrationProvider.RegistryEntry<RecipeSerializer<GatedRecipe>> gatedSerializer() {
        AtomicReference<RecipeSerializer<GatedRecipe>> self = new AtomicReference<>();
        return SERIALIZERS.register("gated", key -> {
            RecipeSerializer<GatedRecipe> serializer = new RecipeSerializer<>(
                    GatedRecipe.mapCodec(self::get), GatedRecipe.streamCodec(self::get));
            self.set(serializer);
            return serializer;
        });
    }
}
