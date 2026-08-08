package za.co.neroland.nerofactions.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import za.co.neroland.nerolandcore.registry.CoreCreativeTab;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerofactions.NeroFactionsCommon;

/**
 * Item registrations, through Neroland Core's {@link RegistrationProvider}. One item: the trade
 * terminal's block item.
 *
 * <p>There is deliberately <b>no NeroFactions creative tab</b>: the item joins Core's shared
 * {@code Neroland} tab via {@link CoreCreativeTab}, so a player with five Nero mods installed gets
 * one tab rather than five (the ecosystem convention). Core reads the tab's contents lazily when
 * it is displayed, so contributing after Core has already built the tab is fine.
 */
public final class NeroFactionsItems {

    public static final RegistrationProvider<Item> ITEMS =
            RegistrationProvider.get(Registries.ITEM, NeroFactionsCommon.MOD_ID);

    /** The trade terminal's item form. */
    public static final RegistryEntry<BlockItem> TRADE_TERMINAL = ITEMS.register("trade_terminal",
            key -> new BlockItem(NeroFactionsBlocks.TRADE_TERMINAL.get(),
                    new Item.Properties().setId(key).useBlockDescriptionPrefix()));

    private NeroFactionsItems() {
    }

    /** Classload-forcing no-op: touching this class runs the static registrations above. */
    public static void init() {
    }

    /** Adds every NeroFactions item to Core's shared creative tab. */
    public static void addToCreativeTab() {
        CoreCreativeTab.add(TRADE_TERMINAL::get);
    }
}
