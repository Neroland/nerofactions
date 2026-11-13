package za.co.neroland.nerofactions.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.content.faction.voidcult.RiftBlade;
import za.co.neroland.nerofactions.content.faction.minerunion.UnionBreaker;
import za.co.neroland.nerofactions.content.faction.FactionArmor;
import za.co.neroland.nerofactions.content.faction.spaceguild.GuildGrapple;
import za.co.neroland.nerofactions.cosmetics.FactionBanners;
import za.co.neroland.nerofactions.cosmetics.FactionTrims;

/**
 * Item registrations, through Neroland Core's {@link RegistrationProvider}. One item: the trade
 * terminal's block item.
 *
 * <p>The items are shown in NeroFactions' own creative tab ({@link NeroFactionsCreativeTab}); every
 * Nero mod has its own tab and Core's {@code Neroland} tab holds only Core's items.
 */
public final class NeroFactionsItems {

    public static final RegistrationProvider<Item> ITEMS =
            RegistrationProvider.get(Registries.ITEM, NeroFactionsCommon.MOD_ID);

    /** The trade terminal's item form. */
    public static final RegistryEntry<BlockItem> TRADE_TERMINAL = ITEMS.register("trade_terminal",
            key -> new BlockItem(NeroFactionsBlocks.TRADE_TERMINAL.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix()));

    public static final RegistryEntry<BlockItem> FACTION_REGISTRY = ITEMS.register("faction_registry",
            key -> new BlockItem(NeroFactionsBlocks.FACTION_REGISTRY.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix()));

    public static final RegistryEntry<BlockItem> CONTRACT_BOARD = ITEMS.register("contract_board",
            key -> new BlockItem(NeroFactionsBlocks.CONTRACT_BOARD.get(), new Item.Properties().setId(key).useBlockDescriptionPrefix()));

    public static final RegistryEntry<BlockItem> QUOTA_BOARD = ITEMS.register("quota_board",
            key -> new BlockItem(NeroFactionsBlocks.QUOTA_BOARD.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix()));

    public static final RegistryEntry<UnionBreaker> UNION_BREAKER = ITEMS.register("union_breaker",
            key -> new UnionBreaker(new Item.Properties().setId(key)));

    public static final RegistryEntry<RiftBlade> RIFT_BLADE = ITEMS.register("rift_blade",
            key -> new RiftBlade(new Item.Properties().setId(key)));

    public static final RegistryEntry<GuildGrapple> GUILD_GRAPPLE = ITEMS.register("guild_grapple",
            key -> new GuildGrapple(new Item.Properties().setId(key)));

    public static final RegistryEntry<BlockItem> NAVIGATION_BEACON = ITEMS.register("navigation_beacon",
            key -> new BlockItem(NeroFactionsBlocks.NAVIGATION_BEACON.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix()));

    public static final RegistryEntry<BlockItem> VOID_ALTAR = ITEMS.register("void_altar",
            key -> new BlockItem(NeroFactionsBlocks.VOID_ALTAR.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix()));

    private static final Map<FactionArmor, List<RegistryEntry<Item>>> ARMOR = registerArmor();

    private static Map<FactionArmor, List<RegistryEntry<Item>>> registerArmor() {
        var armor = new EnumMap<FactionArmor, List<RegistryEntry<Item>>>(FactionArmor.class);
        for (FactionArmor set : FactionArmor.values()) {
            armor.put(set, List.of(set.register("helmet"), set.register("chestplate"),
                    set.register("leggings"), set.register("boots")));
        }
        return Map.copyOf(armor);
    }

    public static List<RegistryEntry<Item>> armorPieces(FactionArmor set) {
        return ARMOR.get(set);
    }

    private static final List<RegistryEntry<Item>> COSMETICS = registerCosmetics();

    private static List<RegistryEntry<Item>> registerCosmetics() {
        List<RegistryEntry<Item>> cosmetics = new ArrayList<>();
        for (OutpostFaction faction : OutpostFaction.values()) {
            if (faction == OutpostFaction.NONE) {
                continue;
            }
            String name = faction.getSerializedName();
            cosmetics.add(ITEMS.register(name + "_banner_pattern",
                    key -> FactionBanners.patternItem(faction, new Item.Properties().setId(key).stacksTo(1))));
            cosmetics.add(ITEMS.register(name + "_armor_trim_smithing_template",
                    key -> FactionTrims.template(new Item.Properties().setId(key))));
            cosmetics.add(ITEMS.register(name + "_trim_material",
                    key -> FactionTrims.material(faction, new Item.Properties().setId(key))));
        }
        return List.copyOf(cosmetics);
    }

    private NeroFactionsItems() {
    }

    /** Classload-forcing no-op: touching this class runs the static registrations above. */
    public static void init() {
    }

    /** Every NeroFactions item, in display order, for {@link NeroFactionsCreativeTab}. */
    public static List<ItemLike> creativeContents() {
        List<ItemLike> contents = new ArrayList<>(List.of(TRADE_TERMINAL.get(),
                FACTION_REGISTRY.get(), CONTRACT_BOARD.get(), QUOTA_BOARD.get(), UNION_BREAKER.get(),
                VOID_ALTAR.get(), RIFT_BLADE.get(), NAVIGATION_BEACON.get(), GUILD_GRAPPLE.get()));
        for (FactionArmor set : FactionArmor.values()) {
            armorPieces(set).forEach(piece -> contents.add(piece.get()));
        }
        COSMETICS.forEach(item -> contents.add(item.get()));
        return List.copyOf(contents);
    }
}
