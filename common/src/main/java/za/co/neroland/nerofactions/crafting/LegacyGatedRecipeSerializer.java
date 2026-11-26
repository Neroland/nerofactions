package za.co.neroland.nerofactions.crafting;

//? if <1.20.5 {
/*import java.util.Optional;
import java.util.function.Supplier;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;

import za.co.neroland.nerofactions.content.FactionTier;

public final class LegacyGatedRecipeSerializer implements RecipeSerializer<GatedRecipe> {
    private final Supplier<RecipeSerializer<GatedRecipe>> serializer;

    public LegacyGatedRecipeSerializer(Supplier<RecipeSerializer<GatedRecipe>> serializer) {
        this.serializer = serializer;
    }

    //? if >=1.20.2 {
    @Override
    public Codec<GatedRecipe> codec() {
        return GatedRecipe.mapCodec(serializer).codec();
    }

    @Override
    public GatedRecipe fromNetwork(FriendlyByteBuf buf) {
    //?} else {
    @Override
    public GatedRecipe fromJson(Identifier id, JsonObject json) {
        CraftingRecipe wrapped = crafting(RecipeManager.fromJson(id, GsonHelper.getAsJsonObject(json, "recipe")));
        Identifier faction = Identifier.parse(GsonHelper.getAsString(json, "faction"));
        FactionTier tier = GatedRecipe.TIER_CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonPrimitive(GsonHelper.getAsString(json, "tier"))).result()
                .orElseThrow(() -> new JsonSyntaxException("Missing or unrecognised faction tier"));
        Optional<Identifier> coreGate = json.has("core_gate")
                ? Optional.of(Identifier.parse(GsonHelper.getAsString(json, "core_gate"))) : Optional.empty();
        return new GatedRecipe(serializer, wrapped, faction, tier, coreGate, id);
    }

    @Override
    public GatedRecipe fromNetwork(Identifier id, FriendlyByteBuf buf) {
    //?}
        Identifier type = buf.readResourceLocation();
        RecipeSerializer<?> nested = BuiltInRegistries.RECIPE_SERIALIZER.getOptional(type)
                .orElseThrow(() -> new IllegalArgumentException("Unknown recipe serializer " + type));
        if (nested == serializer.get()) {
            throw new IllegalArgumentException("A gated recipe cannot wrap another gated recipe");
        }
        //? if >=1.20.2 {
        CraftingRecipe wrapped = crafting(nested.fromNetwork(buf));
        //?} else {
        CraftingRecipe wrapped = crafting(nested.fromNetwork(id, buf));
        //?}
        Identifier faction = buf.readResourceLocation();
        FactionTier tier = buf.readEnum(FactionTier.class);
        Optional<Identifier> coreGate = buf.readBoolean() ? Optional.of(buf.readResourceLocation()) : Optional.empty();
        //? if >=1.20.2 {
        return new GatedRecipe(serializer, wrapped, faction, tier, coreGate);
        //?} else {
        return new GatedRecipe(serializer, wrapped, faction, tier, coreGate, id);
        //?}
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, GatedRecipe recipe) {
        writeWrapped(buf, recipe.wrapped().getSerializer(), recipe.wrapped());
        buf.writeResourceLocation(recipe.faction());
        buf.writeEnum(recipe.tier());
        buf.writeBoolean(recipe.coreGate().isPresent());
        recipe.coreGate().ifPresent(buf::writeResourceLocation);
    }

    private static CraftingRecipe crafting(Recipe<?> recipe) {
        if (recipe instanceof CraftingRecipe wrapped && !(recipe instanceof GatedRecipe)) {
            return wrapped;
        }
        throw new JsonSyntaxException("A gated recipe must wrap an ordinary crafting recipe");
    }

    @SuppressWarnings("unchecked")
    private static <T extends Recipe<?>> void writeWrapped(FriendlyByteBuf buf, RecipeSerializer<T> serializer,
            CraftingRecipe recipe) {
        buf.writeResourceLocation(BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer));
        serializer.toNetwork(buf, (T) recipe);
    }
}
*///?}
