package za.co.neroland.nerofactions.content.faction.salvagers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerofactions.registry.NeroFactionsBlocks;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

public final class DeconstructorBlock extends BaseEntityBlock {
    private static final MapCodec<DeconstructorBlock> CODEC = BlockCodecs.simple(DeconstructorBlock::new);
    private static final TagKey<Item> DENIED = TagKey.create(Registries.ITEM, Identifier.parse("nerofactions:no_deconstruct"));
    private static final TagKey<Item> ALLOWED = TagKey.create(Registries.ITEM, Identifier.parse("nerofactions:deconstructible"));
    private static final TagKey<Item> NEVER_COMMODITY = TagKey.create(Registries.ITEM, Identifier.parse("neroeconomy:never_commodity"));

    public DeconstructorBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<DeconstructorBlock> codec() {
        return CODEC;
    }

    //? if <26.3 {
    /*@Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }
    *///?}

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineEntity(pos, state);
    }

    public static final class MachineEntity extends BlockEntity {
        public MachineEntity(BlockPos pos, BlockState state) {
            super(NeroFactionsBlocks.DECONSTRUCTOR_ENTITY.get(), pos, state);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer salvager) message(salvager, "instructions");
        return InteractionResult.SUCCESS;
    }

    //? if >=1.21.2 {
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer salvager) deconstruct(salvager, stack);
        return InteractionResult.SUCCESS;
    }
    //?} else {
    /*@Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer salvager) deconstruct(salvager, stack);
        return net.minecraft.world.ItemInteractionResult.SUCCESS;
    }
    *///?}

    private static void deconstruct(ServerPlayer player, ItemStack input) {
        var server = player.level().getServer();
        if (server == null || !NeroFactionsConfig.DECONSTRUCTOR_ENABLED.get() || player.getAbilities().instabuild
                || player.isSpectator() || !FactionMembership.membershipOf(server, player.getUUID())
                        .contains(OutpostFaction.SALVAGERS.factionId())) {
            message(player, "unavailable");
            return;
        }
        if (!input.is(ALLOWED) || !eligible(input)) {
            message(player, "unsupported");
            return;
        }
        var matches = server.getRecipeManager().getRecipes().stream()
                .map(holder -> holder.value()).filter(recipe -> recipe.getClass() == ShapedRecipe.class).map(ShapedRecipe.class::cast)
                .map(recipe -> plan(player, input, recipe)).filter(plan -> plan != null).limit(2).toList();
        if (matches.size() != 1 || input.getCount() < matches.getFirst().batch()) {
            message(player, "unsupported");
            return;
        }
        var plan = matches.getFirst();
        var products = plan.ingredients().entrySet().stream().map(entry -> new ItemStack(entry.getKey(),
                Yield.units(entry.getValue(), NeroFactionsConfig.DECONSTRUCTOR_YIELD.get())))
                .filter(stack -> !stack.isEmpty()).toList();
        if (products.isEmpty()) {
            message(player, "unsupported");
            return;
        }
        input.shrink(plan.batch());
        for (ItemStack product : products) {
            //? if >=26.3 {
            /*player.getInventory().placeItemBackInInventory(product, net.minecraft.util.Prediction.SERVER_ONLY);
            *///?} else {
            player.getInventory().placeItemBackInInventory(product);
            //?}
        }
        message(player, "recovered");
    }

    private static Plan plan(ServerPlayer player, ItemStack input, ShapedRecipe recipe) {
        //? if >=26.1 {
        ItemStack result = recipe.assemble(CraftingInput.EMPTY);
        List<Ingredient> ingredients = recipe.placementInfo().ingredients();
        //?} else if >=1.21.2 {
        /*ItemStack result = recipe.assemble(CraftingInput.EMPTY, player.registryAccess());
        List<Ingredient> ingredients = recipe.placementInfo().ingredients();
        *///?} else {
        /*ItemStack result = recipe.getResultItem(player.registryAccess());
        List<Ingredient> ingredients = recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
        *///?}
        if (!result.is(input.getItem()) || !eligible(result) || result.getCount() < 1) return null;
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients) {
            //? if >=1.21.2 {
            List<ItemStack> candidates = ingredient.items().limit(2).map(holder -> new ItemStack(holder.value())).toList();
            //?} else {
            /*List<ItemStack> candidates = List.of(ingredient.getItems());
            *///?}
            if (candidates.size() != 1 || !eligible(candidates.getFirst())
                    || candidates.getFirst().is(input.getItem())) return null;
            counts.merge(candidates.getFirst().getItem(), 1, Integer::sum);
        }
        return counts.isEmpty() ? null : new Plan(result.getCount(), counts);
    }

    private static boolean eligible(ItemStack stack) {
        return !stack.isEmpty() && !stack.is(DENIED) && !stack.is(NEVER_COMMODITY)
                && stack.getComponentsPatch().isEmpty() && !stack.isEnchanted() && !stack.isDamaged();
    }

    private static void message(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable("message.nerofactions.deconstructor." + key));
    }

    private record Plan(int batch, Map<Item, Integer> ingredients) {}

    static final class Yield {
        static int units(int count, int percent) {
            return count * Math.clamp(percent, 0, 75) / 100;
        }
    }
}
