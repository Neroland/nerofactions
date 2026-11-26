package za.co.neroland.nerofactions.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;

//? if >=1.20.5 {
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//? if >=1.20.5 {
import net.minecraft.world.item.component.ItemLore;
//?}
import net.minecraft.world.level.ItemLike;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.content.faction.corporation.ExecutiveStamp;
import za.co.neroland.nerofactions.content.faction.freecolonists.SettlerMultitool;
import za.co.neroland.nerofactions.content.faction.voidcult.RiftBlade;
import za.co.neroland.nerofactions.content.faction.minerunion.UnionBreaker;
import za.co.neroland.nerofactions.content.faction.FactionArmor;
import za.co.neroland.nerofactions.content.faction.spaceguild.GuildGrapple;
import za.co.neroland.nerofactions.content.faction.salvagers.SalvageScanner;
import za.co.neroland.nerofactions.content.faction.terraformingauthority.SurveySpade;
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

    //? if >=1.20.5 {
    public static final RegistryEntry<BlockItem> QUOTA_BOARD = ITEMS.register("quota_board",
            key -> new BlockItem(NeroFactionsBlocks.QUOTA_BOARD.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix().component(DataComponents.LORE,
                            new ItemLore(List.of(Component.translatable("tooltip.nerofactions.quota_board"))))));
    //?} else {
    /*public static final RegistryEntry<BlockItem> QUOTA_BOARD = ITEMS.register("quota_board",
            key -> loreBlock(NeroFactionsBlocks.QUOTA_BOARD.get(), "tooltip.nerofactions.quota_board"));
    *///?}

    public static final RegistryEntry<UnionBreaker> UNION_BREAKER = ITEMS.register("union_breaker",
            key -> new UnionBreaker(new Item.Properties().setId(key)));

    public static final RegistryEntry<RiftBlade> RIFT_BLADE = ITEMS.register("rift_blade",
            key -> new RiftBlade(new Item.Properties().setId(key)));

    public static final RegistryEntry<GuildGrapple> GUILD_GRAPPLE = ITEMS.register("guild_grapple",
            key -> new GuildGrapple(new Item.Properties().setId(key)));
    public static final RegistryEntry<SalvageScanner> SALVAGE_SCANNER = ITEMS.register("salvage_scanner",
            key -> new SalvageScanner(new Item.Properties().setId(key)));
    public static final RegistryEntry<SurveySpade> SURVEY_SPADE = ITEMS.register("survey_spade",
            key -> new SurveySpade(new Item.Properties().setId(key)));

    public static final RegistryEntry<SettlerMultitool> SETTLER_MULTITOOL = ITEMS.register("settler_multitool",
            key -> new SettlerMultitool(new Item.Properties().setId(key)));

    //? if >=1.20.5 {
    public static final RegistryEntry<BlockItem> NAVIGATION_BEACON = ITEMS.register("navigation_beacon",
            key -> new BlockItem(NeroFactionsBlocks.NAVIGATION_BEACON.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix().component(DataComponents.LORE,
                            new ItemLore(List.of(Component.translatable("tooltip.nerofactions.navigation_beacon"))))));

    public static final RegistryEntry<BlockItem> VOID_ALTAR = ITEMS.register("void_altar",
            key -> new BlockItem(NeroFactionsBlocks.VOID_ALTAR.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix().component(DataComponents.LORE,
                            new ItemLore(List.of(Component.translatable("tooltip.nerofactions.void_altar"))))));

    public static final RegistryEntry<BlockItem> DECONSTRUCTOR = ITEMS.register("deconstructor",
            key -> new BlockItem(NeroFactionsBlocks.DECONSTRUCTOR.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix().component(DataComponents.LORE,
                            new ItemLore(List.of(Component.translatable("tooltip.nerofactions.deconstructor"))))));

    public static final RegistryEntry<BlockItem> COMMUNITY_WORKBENCH = ITEMS.register("community_workbench",
            key -> new BlockItem(NeroFactionsBlocks.COMMUNITY_WORKBENCH.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix().component(DataComponents.LORE,
                            new ItemLore(List.of(Component.translatable("tooltip.nerofactions.community_workbench"))))));

    public static final RegistryEntry<BlockItem> ATMOSPHERE_PROCESSOR = ITEMS.register("atmosphere_processor",
            key -> new BlockItem(NeroFactionsBlocks.ATMOSPHERE_PROCESSOR.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix().component(DataComponents.LORE,
                            new ItemLore(List.of(Component.translatable("tooltip.nerofactions.atmosphere_processor"))))));

    public static final RegistryEntry<BlockItem> CORPORATE_TERMINAL = ITEMS.register("corporate_terminal",
            key -> new BlockItem(NeroFactionsBlocks.CORPORATE_TERMINAL.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix().component(DataComponents.LORE,
                            new ItemLore(List.of(Component.translatable("tooltip.nerofactions.corporate_terminal"))))));
    public static final RegistryEntry<ExecutiveStamp> EXECUTIVE_STAMP = ITEMS.register("executive_stamp",
            key -> new ExecutiveStamp(new Item.Properties().setId(key).component(DataComponents.LORE,
                    new ItemLore(List.of(Component.translatable("tooltip.nerofactions.executive_stamp"))))));
    public static final RegistryEntry<Item> CORPORATE_WRIT = ITEMS.register("corporate_writ",
            key -> new Item(new Item.Properties().setId(key).component(DataComponents.LORE,
                    new ItemLore(List.of(Component.translatable("tooltip.nerofactions.corporate_writ"))))));
    //?} else {
    /*public static final RegistryEntry<BlockItem> NAVIGATION_BEACON = ITEMS.register("navigation_beacon",
            key -> loreBlock(NeroFactionsBlocks.NAVIGATION_BEACON.get(), "tooltip.nerofactions.navigation_beacon"));

    public static final RegistryEntry<BlockItem> VOID_ALTAR = ITEMS.register("void_altar",
            key -> loreBlock(NeroFactionsBlocks.VOID_ALTAR.get(), "tooltip.nerofactions.void_altar"));

    public static final RegistryEntry<BlockItem> DECONSTRUCTOR = ITEMS.register("deconstructor",
            key -> loreBlock(NeroFactionsBlocks.DECONSTRUCTOR.get(), "tooltip.nerofactions.deconstructor"));

    public static final RegistryEntry<BlockItem> COMMUNITY_WORKBENCH = ITEMS.register("community_workbench",
            key -> loreBlock(NeroFactionsBlocks.COMMUNITY_WORKBENCH.get(), "tooltip.nerofactions.community_workbench"));

    public static final RegistryEntry<BlockItem> ATMOSPHERE_PROCESSOR = ITEMS.register("atmosphere_processor",
            key -> loreBlock(NeroFactionsBlocks.ATMOSPHERE_PROCESSOR.get(), "tooltip.nerofactions.atmosphere_processor"));

    public static final RegistryEntry<BlockItem> CORPORATE_TERMINAL = ITEMS.register("corporate_terminal",
            key -> loreBlock(NeroFactionsBlocks.CORPORATE_TERMINAL.get(), "tooltip.nerofactions.corporate_terminal"));
    public static final RegistryEntry<ExecutiveStamp> EXECUTIVE_STAMP = ITEMS.register("executive_stamp",
            key -> new ExecutiveStamp(new Item.Properties()));
    public static final RegistryEntry<Item> CORPORATE_WRIT = ITEMS.register("corporate_writ",
            key -> loreItem("tooltip.nerofactions.corporate_writ"));

    private static BlockItem loreBlock(net.minecraft.world.level.block.Block block, String key) {
        return new BlockItem(block, new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level,
                    List<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
                lines.add(Component.translatable(key));
            }
        };
    }

    private static Item loreItem(String key) {
        return new Item(new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level,
                    List<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
                lines.add(Component.translatable(key));
            }
        };
    }
    *///?}

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
        //? if <1.20.5 {
        /*FactionBanners.registerLegacyPatterns();
        *///?}
    }

    /** Every NeroFactions item, in display order, for {@link NeroFactionsCreativeTab}. */
    public static List<ItemLike> creativeContents() {
        List<ItemLike> contents = new ArrayList<>(List.of(TRADE_TERMINAL.get(),
                FACTION_REGISTRY.get(), CONTRACT_BOARD.get(), QUOTA_BOARD.get(), UNION_BREAKER.get(),
                VOID_ALTAR.get(), RIFT_BLADE.get(), NAVIGATION_BEACON.get(), GUILD_GRAPPLE.get(),
                DECONSTRUCTOR.get(), COMMUNITY_WORKBENCH.get(), ATMOSPHERE_PROCESSOR.get(), CORPORATE_TERMINAL.get(),
                EXECUTIVE_STAMP.get(), CORPORATE_WRIT.get(), SALVAGE_SCANNER.get(), SURVEY_SPADE.get(), SETTLER_MULTITOOL.get()));
        for (FactionArmor set : FactionArmor.values()) {
            armorPieces(set).forEach(piece -> contents.add(piece.get()));
        }
        COSMETICS.forEach(item -> contents.add(item.get()));
        return List.copyOf(contents);
    }
}
