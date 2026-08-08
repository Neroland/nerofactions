package za.co.neroland.nerofactions.crafting;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.membership.FactionDecay;
import za.co.neroland.nerolandcore.progression.ProgressionGates;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

/**
 * The {@code nerofactions:gated} recipe — a normal crafting recipe locked behind faction standing.
 * The JSON wraps an ordinary shaped/shapeless (any crafting-type) recipe and adds who may use it:
 *
 * <pre>{@code {
 *   "type": "nerofactions:gated",
 *   "faction": "nerofactions:space_guild",
 *   "tier": "trusted",
 *   "core_gate": "nerolandcore:reached_orbit",   // optional
 *   "recipe": { "type": "minecraft:crafting_shaped", ... }
 * } }</pre>
 *
 * <p>The wrapper parses on <b>every</b> loader unconditionally — there is no loader-condition
 * system in play, so a pack carrying gated recipes never half-loads. All gating happens at
 * {@code matches()} time, server-side, per player:
 *
 * <ol>
 *   <li>the wrapped recipe must match the grid, and</li>
 *   <li>the crafting player (pinned by {@link CraftingPlayer} around vanilla's two lookup sites)
 *       must — after {@link FactionDecay#apply pending decay} is applied so a lapsed member cannot
 *       keep unlocks their eroded standing no longer supports — resolve to at least the required
 *       tier on the faction's ladder, and</li>
 *   <li>if a {@code core_gate} is named, that Core progression gate must be open for the player
 *       ({@link ProgressionGates#isOpen}, resolved by the gate's own scope).</li>
 * </ol>
 *
 * <p><b>Standing gates, membership does not</b> — see {@link GatedRecipeCheck} for why.
 *
 * <p><b>Fail closed.</b> No pinned player (auto-crafters, the crafter block, modded player-blind
 * lookups), an unknown faction, a client-side call, an unbound server: the recipe simply does not
 * resolve. A forged client packet cannot help — the client never resolves recipes in 26.x, and
 * every decision above reads server state only.
 *
 * <p><b>Deliberately invisible.</b> {@link #display()} is empty and {@link #placementInfo()} is
 * {@code NOT_PLACEABLE}: the recipe book's display list is synced per client, not per player
 * standing, so advertising a locked recipe to everyone (or letting the book ghost-place it) would
 * either leak or confuse. Discovery is the faction's job — reward toasts, the wiki, NeroLink —
 * not the vanilla book's. {@link #isSpecial()} is true for the same reason.
 */
public final class GatedRecipe implements CraftingRecipe {

    private static final Codec<CraftingRecipe> WRAPPED_CODEC = Recipe.CODEC.comapFlatMap(
            recipe -> recipe instanceof CraftingRecipe crafting
                    ? DataResult.success(crafting)
                    : DataResult.error(() -> "nerofactions:gated can only wrap a crafting recipe"),
            recipe -> recipe);

    static final Codec<FactionTier> TIER_CODEC = Codec.STRING.comapFlatMap(
            name -> FactionTier.byName(name)
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown faction tier '" + name
                            + "'; expected outsider, associate, member, trusted or inner_circle")),
            FactionTier::jsonName);

    private final Supplier<RecipeSerializer<GatedRecipe>> serializer;
    private final CraftingRecipe wrapped;
    private final Identifier faction;
    private final FactionTier tier;
    private final Optional<Identifier> coreGate;

    GatedRecipe(Supplier<RecipeSerializer<GatedRecipe>> serializer, CraftingRecipe wrapped,
            Identifier faction, FactionTier tier, Optional<Identifier> coreGate) {
        this.serializer = serializer;
        this.wrapped = wrapped;
        this.faction = faction;
        this.tier = tier;
        this.coreGate = coreGate;
    }

    /** The file codec; the serializer supplier breaks the recipe↔serializer registration cycle. */
    public static MapCodec<GatedRecipe> mapCodec(Supplier<RecipeSerializer<GatedRecipe>> serializer) {
        return RecordCodecBuilder.mapCodec(inst -> inst.group(
                WRAPPED_CODEC.fieldOf("recipe").forGetter(recipe -> recipe.wrapped),
                Identifier.CODEC.fieldOf("faction").forGetter(recipe -> recipe.faction),
                TIER_CODEC.fieldOf("tier").forGetter(recipe -> recipe.tier),
                Identifier.CODEC.optionalFieldOf("core_gate").forGetter(recipe -> recipe.coreGate)
        ).apply(inst, (wrapped, faction, tier, coreGate) ->
                new GatedRecipe(serializer, wrapped, faction, tier, coreGate)));
    }

    /** Legacy sync codec (26.x syncs displays, not recipes — required by the registry anyway). */
    public static StreamCodec<RegistryFriendlyByteBuf, GatedRecipe> streamCodec(
            Supplier<RecipeSerializer<GatedRecipe>> serializer) {
        return StreamCodec.composite(
                Recipe.STREAM_CODEC.map(recipe -> (CraftingRecipe) recipe, recipe -> recipe),
                recipe -> recipe.wrapped,
                Identifier.STREAM_CODEC, recipe -> recipe.faction,
                ByteBufCodecs.VAR_INT.map(ordinal -> FactionTier.values()[ordinal],
                        FactionTier::ordinal),
                recipe -> recipe.tier,
                ByteBufCodecs.optional(Identifier.STREAM_CODEC), recipe -> recipe.coreGate,
                (wrapped, faction, tier, coreGate) ->
                        new GatedRecipe(serializer, wrapped, faction, tier, coreGate));
    }

    // --- the gate ------------------------------------------------------------

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (level.isClientSide() || !wrapped.matches(input, level)) {
            return false;
        }
        return unlockedFor(CraftingPlayer.current());
    }

    /**
     * Server-authoritative unlock decision for one player; every input is server state. Null
     * player (unattributed lookup) is a locked recipe by design.
     */
    private boolean unlockedFor(@Nullable ServerPlayer player) {
        if (player == null) {
            return false;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return false;
        }
        FactionDefinition definition = FactionDefinitions.factionsForServer(server).get(faction);
        if (definition == null) {
            return false;
        }
        // Pending decay first: a lapsed member's standing erodes on read, so the tier the recipe
        // sees is the tier the player actually still has (deterministic, never double-counted).
        FactionDecay.apply(server, player.getUUID());
        int standing = ReputationApi.getReputation(player.getUUID(), faction);
        boolean gateOpen = coreGate.isPresent() && ProgressionGates.isOpen(player, coreGate.get());
        return GatedRecipeCheck.allowed(definition, standing, tier, coreGate.isPresent(), gateOpen);
    }

    // --- delegation to the wrapped recipe ------------------------------------

    @Override
    public ItemStack assemble(CraftingInput input) {
        return wrapped.assemble(input);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return wrapped.getRemainingItems(input);
    }

    @Override
    public CraftingBookCategory category() {
        return wrapped.category();
    }

    @Override
    public String group() {
        return wrapped.group();
    }

    // --- presentation: locked recipes are never advertised -------------------

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of();
    }

    @Override
    public RecipeSerializer<GatedRecipe> getSerializer() {
        return serializer.get();
    }

    // --- accessors (tests, later link/UI surfaces) ---------------------------

    public CraftingRecipe wrapped() {
        return wrapped;
    }

    public Identifier faction() {
        return faction;
    }

    public FactionTier tier() {
        return tier;
    }

    public Optional<Identifier> coreGate() {
        return coreGate;
    }
}
