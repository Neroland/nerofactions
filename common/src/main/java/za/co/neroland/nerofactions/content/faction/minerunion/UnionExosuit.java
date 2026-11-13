package za.co.neroland.nerofactions.content.faction.minerunion;

import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
//? if >=1.21.2 {
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
//?} else {
/*import java.util.List;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.crafting.Ingredient;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;
*///?}
//? if <1.21.5 {
/*import net.minecraft.world.item.ArmorItem;
*///?}
import za.co.neroland.nerofactions.registry.NeroFactionsItems;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

public final class UnionExosuit {
    private static final Identifier ID = Identifier.parse("nerofactions:union_exosuit");
    private static final TagKey<Item> REPAIR = TagKey.create(Registries.ITEM,
            Identifier.parse("nerofactions:repairs_union_exosuit"));
    private static final int DURABILITY = 18;

    //? if >=1.21.2 {
    private static final ArmorMaterial MATERIAL = new ArmorMaterial(DURABILITY, Map.of(
            ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 6, ArmorType.LEGGINGS, 5, ArmorType.BOOTS, 2),
            12, SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 0.0F, REPAIR,
            ResourceKey.create(EquipmentAssets.ROOT_ID, ID));
    //?} else {
    /*private static final RegistrationProvider<ArmorMaterial> MATERIALS =
            RegistrationProvider.get(Registries.ARMOR_MATERIAL, ID.getNamespace());
    private static final RegistryEntry<ArmorMaterial> MATERIAL = MATERIALS.register(ID.getPath(),
            key -> new ArmorMaterial(Map.of(Type.HELMET, 2, Type.CHESTPLATE, 6, Type.LEGGINGS, 5, Type.BOOTS, 2),
                    12, SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.of(REPAIR),
                    List.of(new ArmorMaterial.Layer(ID)), 1.0F, 0.0F));
    *///?}

    private UnionExosuit() {
    }

    public static RegistryEntry<Item> register(String part) {
        String id = ID.getPath() + "_" + part;
        //? if >=1.21.2 {
        ArmorType type = switch (part) {
            case "helmet" -> ArmorType.HELMET;
            case "chestplate" -> ArmorType.CHESTPLATE;
            case "leggings" -> ArmorType.LEGGINGS;
            case "boots" -> ArmorType.BOOTS;
            default -> throw new IllegalArgumentException(part);
        };
        //?} else {
        /*Type type = switch (part) {
            case "helmet" -> Type.HELMET;
            case "chestplate" -> Type.CHESTPLATE;
            case "leggings" -> Type.LEGGINGS;
            case "boots" -> Type.BOOTS;
            default -> throw new IllegalArgumentException(part);
        };
        *///?}
        //? if >=1.21.5 {
        return NeroFactionsItems.ITEMS.register(id,
                key -> new Item(new Item.Properties().setId(key).humanoidArmor(MATERIAL, type)));
        //?} else if >=1.21.2 {
        /*return NeroFactionsItems.ITEMS.register(id,
                key -> new ArmorItem(MATERIAL, type, new Item.Properties().setId(key)));
        *///?} else {
        /*ResourceKey<ArmorMaterial> materialKey = ResourceKey.create(Registries.ARMOR_MATERIAL, MATERIAL.id());
        return NeroFactionsItems.ITEMS.register(id, key -> new ArmorItem(
                ((MappedRegistry<ArmorMaterial>) BuiltInRegistries.ARMOR_MATERIAL)
                        .createRegistrationLookup().getOrThrow(materialKey),
                type, new Item.Properties().durability(type.getDurability(DURABILITY))));
        *///?}
    }
}
