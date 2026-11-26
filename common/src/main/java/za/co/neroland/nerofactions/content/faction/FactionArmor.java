package za.co.neroland.nerofactions.content.faction;

import java.util.Map;
import java.util.List;

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
/*import net.minecraft.core.MappedRegistry;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

public enum FactionArmor {
    UNION_EXOSUIT(OutpostFaction.MINER_UNION, "union_exosuit", Stats.UNION_EXOSUIT),
    GUILD_FLIGHT_SUIT(OutpostFaction.SPACE_GUILD, "guild_flight_suit", Stats.GUILD_FLIGHT_SUIT),
    VOID_VESTMENTS(OutpostFaction.VOID_CULT, "void_vestments", Stats.VOID_VESTMENTS),
    CORPORATE_POWER_SUIT(OutpostFaction.NERO_CORPORATION, "corporate_power_suit", Stats.CORPORATE_POWER_SUIT),
    AUTHORITY_ENVIRONMENT_SUIT(OutpostFaction.TERRAFORMING_AUTHORITY, "authority_environment_suit",
            Stats.AUTHORITY_ENVIRONMENT_SUIT),
    COLONIST_FIELD_GEAR(OutpostFaction.FREE_COLONISTS, "colonist_field_gear", Stats.COLONIST_FIELD_GEAR),
    SALVAGER_PATCHWORK(OutpostFaction.SALVAGERS, "salvager_patchwork", Stats.SALVAGER_PATCHWORK);

    /**
     * Per-set armour numbers, kept as plain data so the balance table can be tested without bootstrapping the game.
     * Toughness is per piece. The balance rule: no set may match-or-beat every other set on armour, toughness and
     * durability at once.
     */
    record Stats(int durability, int helmet, int chestplate, int leggings, int boots, float toughness) {
        static final Stats UNION_EXOSUIT = new Stats(18, 2, 6, 5, 2, 1.0F);
        static final Stats GUILD_FLIGHT_SUIT = new Stats(15, 2, 5, 4, 2, 0.5F);
        static final Stats VOID_VESTMENTS = new Stats(16, 2, 5, 4, 2, 0.25F);
        static final Stats CORPORATE_POWER_SUIT = new Stats(22, 2, 6, 5, 2, 0.5F);
        static final Stats AUTHORITY_ENVIRONMENT_SUIT = new Stats(17, 2, 5, 4, 2, 0.5F);
        static final Stats COLONIST_FIELD_GEAR = new Stats(12, 1, 3, 2, 1, 0.0F);
        static final Stats SALVAGER_PATCHWORK = new Stats(20, 2, 6, 5, 2, 0.75F);
        static final Map<String, Stats> ALL = Map.of(
                "union_exosuit", UNION_EXOSUIT, "guild_flight_suit", GUILD_FLIGHT_SUIT,
                "void_vestments", VOID_VESTMENTS, "corporate_power_suit", CORPORATE_POWER_SUIT,
                "authority_environment_suit", AUTHORITY_ENVIRONMENT_SUIT,
                "colonist_field_gear", COLONIST_FIELD_GEAR, "salvager_patchwork", SALVAGER_PATCHWORK);

        int armorTotal() {
            return helmet + chestplate + leggings + boots;
        }

        float toughnessTotal() {
            return toughness * 4;
        }
    }

    //? if >=1.21.2 {
    private final ArmorMaterial material;
    //?} else if >=1.20.5 {
    /*private final RegistryEntry<ArmorMaterial> material;
    private final int durability;
    *///?} else {
    /*private final ArmorMaterial material;
    *///?}
    private final String name;
    private final OutpostFaction faction;

    FactionArmor(OutpostFaction faction, String name, Stats stats) {
        int durability = stats.durability();
        int helmet = stats.helmet();
        int chestplate = stats.chestplate();
        int leggings = stats.leggings();
        int boots = stats.boots();
        float toughness = stats.toughness();
        this.faction = faction;
        this.name = name;
        var id = Identifier.fromNamespaceAndPath("nerofactions", name);
        TagKey<Item> repair = TagKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath("nerofactions", "repairs_" + name));
        //? if >=1.21.2 {
        this.material = new ArmorMaterial(durability, Map.of(ArmorType.HELMET, helmet, ArmorType.CHESTPLATE, chestplate,
                ArmorType.LEGGINGS, leggings, ArmorType.BOOTS, boots), 12, SoundEvents.ARMOR_EQUIP_IRON,
                toughness, 0.0F, repair, ResourceKey.create(EquipmentAssets.ROOT_ID, id));
        //?} else if >=1.20.5 {
        /*this.durability = durability;
        this.material = RegistrationProvider.get(Registries.ARMOR_MATERIAL, id.getNamespace()).register(name,
                key -> new ArmorMaterial(Map.of(Type.HELMET, helmet, Type.CHESTPLATE, chestplate,
                        Type.LEGGINGS, leggings, Type.BOOTS, boots), 12, SoundEvents.ARMOR_EQUIP_IRON,
                        () -> Ingredient.of(repair), List.of(new ArmorMaterial.Layer(id)), toughness, 0.0F));
        *///?} else {
        /*this.material = new LegacyMaterial(id, stats, repair);
        *///?}
    }

    public String id() {
        return name;
    }

    public Identifier factionId() {
        return faction.factionId();
    }

    public OutpostFaction faction() {
        return faction;
    }

    public List<RegistryEntry<Item>> pieces() {
        return NeroFactionsItems.armorPieces(this);
    }

    public boolean includes(ItemStack stack) {
        return pieces().stream().anyMatch(piece -> stack.is(piece.get()));
    }

    public int equipped(Player player) {
        var pieces = pieces();
        int count = 0;
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(pieces.get(0).get())) count++;
        if (player.getItemBySlot(EquipmentSlot.CHEST).is(pieces.get(1).get())) count++;
        if (player.getItemBySlot(EquipmentSlot.LEGS).is(pieces.get(2).get())) count++;
        if (player.getItemBySlot(EquipmentSlot.FEET).is(pieces.get(3).get())) count++;
        return count;
    }

    public RegistryEntry<Item> register(String part) {
        String id = name + "_" + part;
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
                key -> new Item(new Item.Properties().setId(key).humanoidArmor(material, type)));
        //?} else if >=1.21.2 {
        /*return NeroFactionsItems.ITEMS.register(id,
                key -> new ArmorItem(material, type, new Item.Properties().setId(key)));
        *///?} else if >=1.20.5 {
        /*ResourceKey<ArmorMaterial> materialKey = ResourceKey.create(Registries.ARMOR_MATERIAL, material.id());
        return NeroFactionsItems.ITEMS.register(id, key -> new ArmorItem(
                ((MappedRegistry<ArmorMaterial>) BuiltInRegistries.ARMOR_MATERIAL)
                        .createRegistrationLookup().getOrThrow(materialKey),
                type, new Item.Properties().durability(type.getDurability(durability))));
        *///?} else {
        /*return NeroFactionsItems.ITEMS.register(id,
                key -> new ArmorItem(material, type, new Item.Properties()));
        *///?}
    }

    //? if <1.20.5 {
    /*private record LegacyMaterial(Identifier id, Stats stats, TagKey<Item> repair) implements ArmorMaterial {
        @Override
        public int getDurabilityForType(Type type) {
            int base = switch (type) {
                case HELMET -> 11;
                case CHESTPLATE -> 16;
                case LEGGINGS -> 15;
                case BOOTS -> 13;
            };
            return base * stats.durability();
        }

        @Override
        public int getDefenseForType(Type type) {
            return switch (type) {
                case HELMET -> stats.helmet();
                case CHESTPLATE -> stats.chestplate();
                case LEGGINGS -> stats.leggings();
                case BOOTS -> stats.boots();
            };
        }

        @Override public int getEnchantmentValue() { return 12; }
        @Override public net.minecraft.sounds.SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(repair); }
        @Override public String getName() { return id.toString(); }
        @Override public float getToughness() { return stats.toughness(); }
        @Override public float getKnockbackResistance() { return 0.0F; }
    }
    *///?}
}
