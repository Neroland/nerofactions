package za.co.neroland.nerofactions.content.faction.voidcult;

//? if <1.20.5 {
/*import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public final class LegacyVoidReach {
    public static final double BONUS = 0.75D;
    private static final AttributeModifier REACH = new AttributeModifier(
            UUID.nameUUIDFromBytes("nerofactions:void_rite_reach".getBytes(StandardCharsets.UTF_8)),
            "nerofactions:void_rite_reach", BONUS, AttributeModifier.Operation.ADDITION);

    private LegacyVoidReach() {
    }

    public static void update(AttributeInstance range, boolean enabled) {
        if (range == null) {
            return;
        }
        if (enabled) {
            if (!range.hasModifier(REACH)) {
                range.addTransientModifier(REACH);
            }
        } else {
            range.removeModifier(REACH.getId());
        }
    }
}
*///?}
