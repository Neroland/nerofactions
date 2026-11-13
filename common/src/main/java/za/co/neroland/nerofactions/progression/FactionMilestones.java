package za.co.neroland.nerofactions.progression;

import java.util.EnumSet;
import java.util.Set;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.registry.NeroFactionsItems;
import za.co.neroland.nerolandcore.event.ThresholdEvents;

public final class FactionMilestones {
    private static final Identifier MACHINE = Identifier.parse("nerofactions:signature_machine_built");
    private static final Identifier SET = Identifier.parse("nerofactions:full_set_worn");
    private static final Set<OutpostFaction> WORN = EnumSet.noneOf(OutpostFaction.class);

    private FactionMilestones() {
    }

    public static void machineCrafted(ItemStack stack) {
        if (stack.is(NeroFactionsItems.QUOTA_BOARD.get())) {
            fire(MACHINE, OutpostFaction.MINER_UNION.factionId());
        } else if (stack.is(NeroFactionsItems.NAVIGATION_BEACON.get())) {
            fire(MACHINE, OutpostFaction.SPACE_GUILD.factionId());
        } else if (stack.is(NeroFactionsItems.VOID_ALTAR.get())) {
            fire(MACHINE, OutpostFaction.VOID_CULT.factionId());
        }
    }

    public static void fullSets(Set<OutpostFaction> equipped) {
        for (var faction : equipped) {
            if (!WORN.contains(faction)) {
                fire(SET, faction.factionId());
            }
        }
        WORN.clear();
        WORN.addAll(equipped);
    }

    public static void clear() {
        WORN.clear();
    }

    private static void fire(Identifier channel, Identifier faction) {
        ThresholdEvents.fire(NeroFactionsCommon.MOD_ID,
                new ThresholdEvents.ThresholdCrossing(channel, faction.toString(), 1, 1, true));
    }
}
