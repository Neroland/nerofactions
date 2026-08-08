package za.co.neroland.nerofactions.content;

import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * A per-tier price multiplier for one of the faction's speciality item <em>tags</em>:
 *
 * <pre>{"tag": "c:ingots/iron", "by_tier": {"associate": 0.95, "member": 0.9, ...}}</pre>
 *
 * <p>The tag is a {@code c:}-namespace or {@code minecraft:} tag id, validated only as a
 * well-formed identifier at load — tags resolve per-datapack at runtime and an empty tag simply
 * modifies nothing. Multipliers are clamped to {@code [0.5, 1.5]} by
 * {@link FactionDefinitions}' validation (out-of-band values are clamped and reported), and a tier
 * with no entry multiplies by {@code 1.0} — no discount, no markup.
 *
 * <p>Nothing consumes these yet: the trade hooks arrive with the economy integration stage. The
 * data ships now so faction packs are stable from the first release.
 *
 * @param tag    the item tag the multiplier applies to
 * @param byTier tier json-name → price multiplier; missing tiers mean {@code 1.0}
 */
public record TradeModifier(Identifier tag, Map<String, Double> byTier) {

    public static final double MIN_MULTIPLIER = 0.5D;
    public static final double MAX_MULTIPLIER = 1.5D;

    public static final Codec<TradeModifier> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.fieldOf("tag").forGetter(TradeModifier::tag),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("by_tier")
                    .forGetter(TradeModifier::byTier)
    ).apply(inst, TradeModifier::new));

    /** The price multiplier at this tier ({@code 1.0} when the tier has no entry). */
    public double multiplierFor(FactionTier tier) {
        Double value = byTier.get(tier.jsonName());
        return value == null ? 1.0D : value;
    }
}
