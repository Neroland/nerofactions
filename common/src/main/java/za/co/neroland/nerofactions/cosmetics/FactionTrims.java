package za.co.neroland.nerofactions.cosmetics;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
//? if >=1.21.2 {
import net.minecraft.world.item.SmithingTemplateItem;
//?}
import za.co.neroland.nerofactions.content.faction.OutpostFaction;

public final class FactionTrims {
    private FactionTrims() {
    }

    public static Item template(Item.Properties properties) {
        //? if >=1.21.2 {
        return SmithingTemplateItem.createArmorTrimTemplate(properties);
        //?} else {
        /*return new Item(properties);
        *///?}
    }

    public static Item material(OutpostFaction faction, Item.Properties properties) {
        //? if >=1.21.5 {
        properties.trimMaterial(ResourceKey.create(Registries.TRIM_MATERIAL, faction.factionId()));
        //?}
        return new Item(properties);
    }
}
