package za.co.neroland.nerofactions.content;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * One faction, loaded from a datapack JSON under
 * {@code data/<namespace>/nerofactions/factions/<path>.json}. The id is the file's namespace + path
 * (stamped via {@link #withId} by {@link FactionDefinitions}), so a pack overrides a shipped
 * faction simply by shipping the same id.
 *
 * <p>The JSON shape:
 *
 * <pre>
 * {
 *   "display_name": "Space Guild",                 // the source of truth for the name (not lang)
 *   "theme": "One sentence of flavour.",
 *   "tiers": {"outsider": 0, "associate": 100, "member": 400, "trusted": 1000, "inner_circle": 2500},
 *   "rewards": {"associate": [ RewardEntry... ], ...},
 *   "enemies": ["nerofactions:salvagers", ...],    // asymmetric graphs are deliberate and valid
 *   "trade": [ TradeModifier... ],
 *   "cosmetics": { Cosmetics }
 * }
 * </pre>
 *
 * <p>The {@code tiers} and {@code rewards} maps are decoded with <em>string</em> keys on purpose:
 * an unknown tier name must be pruned with a {@link ValidationIssue} rather than failing the whole
 * file, which a strict enum codec would do. All structural rules — the five tiers present, Outsider
 * at 0, strictly increasing thresholds, known enemies, sane multipliers — are enforced by
 * {@link FactionDefinitions}' validation, so any instance obtained from the loader is clean.
 *
 * <p>Unknown JSON keys are tolerated (RecordCodecBuilder ignores them), so data files may carry a
 * {@code "_comment"} field.
 */
public record FactionDefinition(
        Identifier id,
        String displayName,
        String theme,
        Map<String, Integer> tiers,
        Map<String, List<RewardEntry>> rewards,
        List<Identifier> enemies,
        List<TradeModifier> trade,
        Optional<Cosmetics> cosmetics,
        String color, String motto, Identifier icon, String descriptionKey) {

    public FactionDefinition(Identifier id, String displayName, String theme,
            Map<String, Integer> tiers, Map<String, List<RewardEntry>> rewards,
            List<Identifier> enemies, List<TradeModifier> trade, Optional<Cosmetics> cosmetics) {
        this(id, displayName, theme, tiers, rewards, enemies, trade, cosmetics,
                "#90B4A0", "", Identifier.fromNamespaceAndPath("minecraft", "book"), "");
    }

    private static final Codec<String> COLOR = Codec.STRING.validate(value ->
            value.matches("#[0-9a-fA-F]{6}") ? com.mojang.serialization.DataResult.success(value)
                    : com.mojang.serialization.DataResult.error(() -> "color must be #RRGGBB"));

    /** Placeholder until {@link FactionDefinitions} stamps the file-derived id on. */
    public static final Identifier UNNAMED =
            Identifier.fromNamespaceAndPath("nerofactions", "unnamed_faction");

    public static final Codec<FactionDefinition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.optionalFieldOf("id", UNNAMED).forGetter(FactionDefinition::id),
            Codec.STRING.fieldOf("display_name").forGetter(FactionDefinition::displayName),
            Codec.STRING.fieldOf("theme").forGetter(FactionDefinition::theme),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("tiers")
                    .forGetter(FactionDefinition::tiers),
            Codec.unboundedMap(Codec.STRING, RewardEntry.CODEC.listOf())
                    .optionalFieldOf("rewards", Map.of()).forGetter(FactionDefinition::rewards),
            Identifier.CODEC.listOf().optionalFieldOf("enemies", List.of())
                    .forGetter(FactionDefinition::enemies),
            TradeModifier.CODEC.listOf().optionalFieldOf("trade", List.of())
                    .forGetter(FactionDefinition::trade),
            Cosmetics.CODEC.optionalFieldOf("cosmetics").forGetter(FactionDefinition::cosmetics),
            COLOR.optionalFieldOf("color", "#90B4A0").forGetter(FactionDefinition::color),
            Codec.STRING.optionalFieldOf("motto", "").forGetter(FactionDefinition::motto),
            Identifier.CODEC.optionalFieldOf("icon", Identifier.fromNamespaceAndPath("minecraft", "book"))
                    .forGetter(FactionDefinition::icon),
            Codec.STRING.optionalFieldOf("description_key", "").forGetter(FactionDefinition::descriptionKey)
    ).apply(inst, FactionDefinition::new));

    /** The reputation threshold of this tier, or null if absent (impossible after validation). */
    public Integer threshold(FactionTier tier) {
        return tiers.get(tier.jsonName());
    }

    /** The rewards granted on crossing into this tier (empty for tiers with no table). */
    public List<RewardEntry> rewardsFor(FactionTier tier) {
        List<RewardEntry> list = rewards.get(tier.jsonName());
        return list == null ? List.of() : list;
    }

    // --- withers (validation replaces parts without re-decoding) -------------

    public FactionDefinition withId(Identifier newId) {
        return new FactionDefinition(newId, displayName, theme, tiers, rewards, enemies, trade, cosmetics, color, motto, icon, descriptionKey);
    }

    public FactionDefinition withTiers(Map<String, Integer> newTiers) {
        return new FactionDefinition(id, displayName, theme, newTiers, rewards, enemies, trade, cosmetics, color, motto, icon, descriptionKey);
    }

    public FactionDefinition withRewards(Map<String, List<RewardEntry>> newRewards) {
        return new FactionDefinition(id, displayName, theme, tiers, newRewards, enemies, trade, cosmetics, color, motto, icon, descriptionKey);
    }

    public FactionDefinition withEnemies(List<Identifier> newEnemies) {
        return new FactionDefinition(id, displayName, theme, tiers, rewards, newEnemies, trade, cosmetics, color, motto, icon, descriptionKey);
    }

    public FactionDefinition withTrade(List<TradeModifier> newTrade) {
        return new FactionDefinition(id, displayName, theme, tiers, rewards, enemies, newTrade, cosmetics, color, motto, icon, descriptionKey);
    }
}
