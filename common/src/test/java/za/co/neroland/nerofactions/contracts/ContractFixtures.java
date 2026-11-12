package za.co.neroland.nerofactions.contracts;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import net.minecraft.resources.Identifier;

public final class ContractFixtures {
    public static final Identifier FACTION = Identifier.parse("nerofactions:miner_union");

    private ContractFixtures() {
    }

    public static List<ContractDefinition> pool() {
        return IntStream.range(0, 12).mapToObj(i -> new ContractDefinition(
                Identifier.parse("nerofactions:miner_union/test_" + i), FACTION, "deliver",
                "minecraft:iron_ingot", 16, 25, 10, Optional.empty(), 1, 0, 4, i + 1)).toList();
    }
}
