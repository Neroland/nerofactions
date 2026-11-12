package za.co.neroland.nerofactions.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import za.co.neroland.nerofactions.menu.RegistryMenu;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

public final class NeroFactionsMenus {
    public static final RegistrationProvider<MenuType<?>> MENUS = RegistrationProvider.get(Registries.MENU, "nerofactions");
    public static final RegistryEntry<MenuType<RegistryMenu>> REGISTRY = MENUS.register("faction_registry",
            key -> new MenuType<>(RegistryMenu::new, FeatureFlags.VANILLA_SET));
    public static final RegistryEntry<MenuType<za.co.neroland.nerofactions.menu.ContractBoardMenu>> CONTRACT_BOARD = MENUS.register("contract_board",
            key -> new MenuType<>(za.co.neroland.nerofactions.menu.ContractBoardMenu::new, FeatureFlags.VANILLA_SET));

    private NeroFactionsMenus() {
    }

    public static void init() {
    }
}
