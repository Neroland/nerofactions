package za.co.neroland.nerofactions.registry;

import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerofactions.NeroFactionsCommon;

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

    private NeroFactionsItems() {
    }

    /** Classload-forcing no-op: touching this class runs the static registrations above. */
    public static void init() {
    }

    /** Every NeroFactions item, in display order, for {@link NeroFactionsCreativeTab}. */
    public static List<ItemLike> creativeContents() {
        return List.of(TRADE_TERMINAL.get());
    }
}
