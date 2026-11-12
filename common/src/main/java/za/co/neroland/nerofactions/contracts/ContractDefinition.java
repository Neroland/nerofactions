package za.co.neroland.nerofactions.contracts;

import java.util.Optional;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

public record ContractDefinition(Identifier id, Identifier faction, String type, String target,
        int count, int reputation, long credits, Optional<Identifier> item, int itemCount,
        int minTier, int maxTier, int weight) {
    private static final Identifier UNNAMED = Identifier.parse("nerofactions:unnamed_contract");
    public static final Codec<ContractDefinition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.optionalFieldOf("id", UNNAMED).forGetter(ContractDefinition::id),
            Identifier.CODEC.fieldOf("faction").forGetter(ContractDefinition::faction),
            Codec.STRING.fieldOf("type").forGetter(ContractDefinition::type),
            Codec.STRING.fieldOf("target").forGetter(ContractDefinition::target),
            Codec.intRange(1, 4096).fieldOf("count").forGetter(ContractDefinition::count),
            Codec.intRange(0, 10000).fieldOf("reputation").forGetter(ContractDefinition::reputation),
            Codec.LONG.validate(value -> value >= 0 && value <= 1000000
                    ? com.mojang.serialization.DataResult.success(value)
                    : com.mojang.serialization.DataResult.error(() -> "credits out of bounds")).optionalFieldOf("credits", 0L).forGetter(ContractDefinition::credits),
            Identifier.CODEC.optionalFieldOf("item").forGetter(ContractDefinition::item),
            Codec.intRange(1, 64).optionalFieldOf("item_count", 1).forGetter(ContractDefinition::itemCount),
            Codec.intRange(0, 4).optionalFieldOf("min_tier", 0).forGetter(ContractDefinition::minTier),
            Codec.intRange(0, 4).optionalFieldOf("max_tier", 4).forGetter(ContractDefinition::maxTier),
            Codec.intRange(1, 1000).optionalFieldOf("weight", 1).forGetter(ContractDefinition::weight)
    ).apply(inst, ContractDefinition::new));

    public Optional<String> problem() {
        if (id == null || faction == null || type == null || target == null || target.length() > 256
                || id.toString().length() > 256 || faction.toString().length() > 256
                || count < 1 || count > 4096 || reputation < 0 || reputation > 10000 || credits < 0 || credits > 1000000
                || weight < 1 || weight > 1000 || minTier < 0 || maxTier > 4 || itemCount < 1 || itemCount > 64) {
            return Optional.of("contract fields out of bounds");
        }
        if (!java.util.Set.of("deliver", "kill", "craft", "visit_structure").contains(type)) {
            return Optional.of("unknown contract type");
        }
        String path = target.startsWith("#") ? target.substring(1) : target;
        if (Identifier.tryParse(path) == null) return Optional.of("invalid target identifier");
        if (minTier > maxTier) return Optional.of("inverted tier band");
        if (type.equals("visit_structure") && !target.startsWith("#")) {
            return Optional.of("structure targets must be tags");
        }
        if (type.equals("visit_structure") && count != 1) return Optional.of("structure visits use count 1");
        return Optional.empty();
    }

    public ContractDefinition withId(Identifier value) {
        return new ContractDefinition(value, faction, type, target, count, reputation, credits, item,
                itemCount, minTier, maxTier, weight);
    }
}
