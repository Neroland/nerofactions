package za.co.neroland.nerofactions.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
//? if >=1.20.3 {
import net.minecraft.world.RandomizableContainer;
//?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import za.co.neroland.nerofactions.content.faction.FactionArmor;
import za.co.neroland.nerofactions.content.faction.GearSetBonuses;
import za.co.neroland.nerofactions.content.faction.salvagers.SalvagerLoot;

/**
 * Salvager set bonus: the first time an active Salvager unpacks a loot container, its loot table is rolled once
 * more and the extra stacks go into empty slots only. {@code unpackLootTable} only reaches {@code fill} when the
 * container still has a loot table and a server, so this runs server-side, once per container.
 */
//? if >=1.20.3 {
@Mixin(RandomizableContainer.class)
interface SalvagerLootMixin {
//?} else {
/*@Mixin(net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity.class)
abstract class SalvagerLootMixin {
*///?}
    @Redirect(method = "unpackLootTable", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/loot/LootTable;fill("
                    + "Lnet/minecraft/world/Container;"
                    + "Lnet/minecraft/world/level/storage/loot/LootParams;J)V"))
    private void nerofactions$salvagerExtraLoot(LootTable table, Container container, LootParams params, long seed,
            Player opener) {
        table.fill(container, params, seed);
        boolean active = opener instanceof ServerPlayer player
                && GearSetBonuses.active(player, FactionArmor.SALVAGER_PATCHWORK);
        if (SalvagerLoot.extraRolls(active, table != LootTable.EMPTY) == 0) return;
        var extras = table.getRandomItems(params, SalvagerLoot.extraSeed(seed));
        var placed = SalvagerLoot.placements(container.getContainerSize(),
                slot -> container.getItem(slot).isEmpty(), ItemStack::isEmpty, extras);
        placed.forEach(container::setItem);
        if (!placed.isEmpty()) container.setChanged();
    }
}
