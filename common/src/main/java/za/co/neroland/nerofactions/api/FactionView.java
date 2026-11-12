package za.co.neroland.nerofactions.api;

import java.util.List;
import net.minecraft.resources.Identifier;

public record FactionView(Identifier id, String displayName, String theme,
        List<FactionTierView> tiers, List<Identifier> enemies) {
    public FactionView {
        tiers = List.copyOf(tiers);
        enemies = List.copyOf(enemies);
    }
}
