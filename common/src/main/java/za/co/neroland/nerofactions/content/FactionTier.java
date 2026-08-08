package za.co.neroland.nerofactions.content;

import java.util.Locale;
import java.util.Optional;

/**
 * The five faction standing tiers, lowest to highest. The ladder is fixed — every faction ships
 * exactly these five tiers and only the integer <em>thresholds</em> are data-driven (see
 * {@link FactionDefinition}). Ordinal order is meaningful and load-bearing: {@link FactionTiers}
 * resolves a standing value by walking this order, and validation requires each faction's
 * thresholds to be strictly increasing along it.
 *
 * <p>Display goes through translatable keys ({@link #translationKey()}) so tier names localise;
 * faction <em>names</em>, by contrast, are data-driven strings in the faction JSON.
 */
public enum FactionTier {

    /** Everyone starts here; its threshold is always {@code 0} (validation enforces it). */
    OUTSIDER("outsider"),
    ASSOCIATE("associate"),
    MEMBER("member"),
    TRUSTED("trusted"),
    INNER_CIRCLE("inner_circle");

    private final String jsonName;

    FactionTier(String jsonName) {
        this.jsonName = jsonName;
    }

    /** The name this tier goes by in faction JSON ({@code tiers}, {@code rewards}, {@code by_tier}). */
    public String jsonName() {
        return jsonName;
    }

    /** {@code tier.nerofactions.<name>} — resolved client-side from the lang file. */
    public String translationKey() {
        return "tier.nerofactions." + jsonName;
    }

    /** The tier with this JSON name (case-insensitive), or empty for an unknown name. */
    public static Optional<FactionTier> byName(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String wanted = raw.toLowerCase(Locale.ROOT);
        for (FactionTier tier : values()) {
            if (tier.jsonName.equals(wanted)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }
}
