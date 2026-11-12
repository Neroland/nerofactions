package za.co.neroland.nerofactions.contracts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.resources.Identifier;

public final class DailyContracts {
    private DailyContracts() {
    }

    public static List<ContractDefinition> roll(List<ContractDefinition> pool, long seed, long day,
            UUID player, Identifier faction, int tier) {
        var eligible = new ArrayList<>(pool.stream().filter(c -> c.faction().equals(faction)
                && tier >= c.minTier() && tier <= c.maxTier()).sorted(Comparator.comparing(c -> c.id())).toList());
        var random = new Random(mix(seed) ^ mix(day) ^ mix(player.getMostSignificantBits())
                ^ Long.rotateLeft(mix(player.getLeastSignificantBits()), 17) ^ mix(faction.toString().hashCode()));
        var result = new ArrayList<ContractDefinition>();
        while (!eligible.isEmpty() && result.size() < 3) {
            int total = eligible.stream().mapToInt(ContractDefinition::weight).sum();
            int draw = random.nextInt(total);
            for (int i = 0; i < eligible.size(); i++) {
                draw -= eligible.get(i).weight();
                if (draw < 0) {
                    result.add(eligible.remove(i));
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
