package za.co.neroland.nerofactions.content.faction.salvagers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * Pure rules behind the Salvager set's "extra loot from containers opened first" bonus. The mixin on
 * {@code RandomizableContainer.unpackLootTable} rolls the container's own loot table once more (seeded from the
 * container's loot seed + 1) and hands the result here to be placed into empty slots only.
 */
public final class SalvagerLoot {
    private SalvagerLoot() {
    }

    /** One extra roll for an active Salvager unpacking a container that still had a loot table; none otherwise. */
    public static int extraRolls(boolean activeSalvager, boolean firstOpen) {
        return activeSalvager && firstOpen ? 1 : 0;
    }

    /** The extra roll's seed: deterministic from the container's loot seed. */
    public static long extraSeed(long lootSeed) {
        return lootSeed + 1;
    }

    /**
     * Assigns each non-empty extra to the next empty slot, in slot order. Occupied slots are never chosen and
     * whatever does not fit is left out (discarded, never dropped). Returns slot index to extra, in placement order.
     */
    public static <T> Map<Integer, T> placements(int size, IntPredicate slotEmpty, Predicate<T> isEmpty,
            List<T> extras) {
        Map<Integer, T> placed = new LinkedHashMap<>();
        int slot = 0;
        for (T extra : extras) {
            if (isEmpty.test(extra)) continue;
            while (slot < size && !slotEmpty.test(slot)) slot++;
            if (slot >= size) break;
            placed.put(slot++, extra);
        }
        return placed;
    }
}
