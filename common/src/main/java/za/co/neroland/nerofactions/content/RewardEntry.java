package za.co.neroland.nerofactions.content;

import java.util.Optional;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * One reward granted when a player crosses into a tier. Two honest 0.1.0 shapes only:
 *
 * <ul>
 *   <li>{@code {"type":"item","id":"minecraft:iron_ingot","count":8}} — a vanilla item stack,
 *       granted on tier crossing once the reward engine lands (a later stage; the data ships now so
 *       packs are stable).</li>
 *   <li>{@code {"type":"cosmetic","cosmetic":"banner_pattern"}} (or {@code "trim_material"}) — a
 *       reference into the faction's {@link Cosmetics} block. Forward references: the cosmetic
 *       content itself arrives in a later stage.</li>
 * </ul>
 *
 * <p>The codec is deliberately flat and permissive (every field beyond {@code type} optional); all
 * cross-field rules live in {@link #problem}, so a bad entry is <em>pruned with a report</em> by
 * {@link FactionDefinitions} rather than failing the whole file.
 *
 * @param type     {@code "item"} or {@code "cosmetic"} — anything else is pruned at load
 * @param item     the item id, required when {@code type} is {@code "item"}
 * @param count    stack count for item rewards (codec-clamped to at least 1)
 * @param cosmetic {@code "banner_pattern"} or {@code "trim_material"}, required for cosmetic rewards
 */
public record RewardEntry(String type, Optional<Identifier> item, int count, Optional<String> cosmetic) {

    public static final String TYPE_ITEM = "item";
    public static final String TYPE_COSMETIC = "cosmetic";
    public static final String COSMETIC_BANNER_PATTERN = "banner_pattern";
    public static final String COSMETIC_TRIM_MATERIAL = "trim_material";

    public static final Codec<RewardEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("type").forGetter(RewardEntry::type),
            Identifier.CODEC.optionalFieldOf("id").forGetter(RewardEntry::item),
            Codec.intRange(1, 6400).optionalFieldOf("count", 1).forGetter(RewardEntry::count),
            Codec.STRING.optionalFieldOf("cosmetic").forGetter(RewardEntry::cosmetic)
    ).apply(inst, RewardEntry::new));

    /**
     * Why this entry cannot be granted, or empty when it is valid. {@code itemExists} is the item
     * registry seam ({@link FactionDefinitions} passes the real registry; tests pass their own), and
     * {@code hasCosmetics} says whether the owning faction ships a {@link Cosmetics} block for a
     * cosmetic entry to reference.
     */
    public Optional<String> problem(Predicate<Identifier> itemExists, boolean hasCosmetics) {
        switch (type) {
            case TYPE_ITEM -> {
                if (item.isEmpty()) {
                    return Optional.of("item reward without an \"id\"");
                }
                if (!itemExists.test(item.get())) {
                    return Optional.of("item reward names unknown item " + item.get());
                }
                return Optional.empty();
            }
            case TYPE_COSMETIC -> {
                if (cosmetic.isEmpty()) {
                    return Optional.of("cosmetic reward without a \"cosmetic\" field");
                }
                String which = cosmetic.get();
                if (!COSMETIC_BANNER_PATTERN.equals(which) && !COSMETIC_TRIM_MATERIAL.equals(which)) {
                    return Optional.of("cosmetic reward names unknown cosmetic \"" + which + "\"");
                }
                if (!hasCosmetics) {
                    return Optional.of("cosmetic reward but the faction has no cosmetics block");
                }
                return Optional.empty();
            }
            default -> {
                return Optional.of("unknown reward type \"" + type + "\"");
            }
        }
    }
}
