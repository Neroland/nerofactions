package za.co.neroland.nerofactions.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerofactions.NeroFactionsCommon;

/**
 * NeroFactions' own creative tab, registered cross-loader via Core's {@link RegistrationProvider}
 * over the vanilla {@code CREATIVE_MODE_TAB} registry. Every Nero mod has its own tab; Core's
 * "Neroland" tab holds only Core's items.
 */
public final class NeroFactionsCreativeTab {

    public static final RegistrationProvider<CreativeModeTab> TABS =
            RegistrationProvider.get(Registries.CREATIVE_MODE_TAB, NeroFactionsCommon.MOD_ID);

    // NOTE: vanilla CreativeModeTab.builder takes (Row, column); the no-arg overload and
    // withTabsBefore/After are NeoForge-only extensions, so they are avoided here (common = raw vanilla).
    public static final RegistryEntry<CreativeModeTab> NEROFACTIONS = TABS.register("nerofactions",
            key -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.nerofactions"))
                    .icon(() -> new ItemStack(NeroFactionsItems.TRADE_TERMINAL.get()))
                    .displayItems((params, output) -> NeroFactionsItems.creativeContents().forEach(output::accept))
                    .build());

    private NeroFactionsCreativeTab() {
    }

    /** Force class-load so the static tab registration runs. Call after {@link NeroFactionsItems#init()}. */
    public static void init() {
    }
}
