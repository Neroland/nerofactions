package za.co.neroland.nerofactions.content;

/**
 * Tier resolution: which {@link FactionTier} a reputation value lands in for a given faction's
 * ladder. <b>Server-resolved only</b> — the client never asserts standing; anything shown
 * client-side is a server-computed snapshot.
 */
public final class FactionTiers {

    private FactionTiers() {
    }

    /**
     * The tier {@code value} belongs to on this faction's ladder: the <em>highest</em> tier whose
     * threshold is {@code <= value}, so a value exactly at a threshold belongs to the higher tier.
     * Negative standing (and any value below Associate) is {@link FactionTier#OUTSIDER}.
     *
     * <p>Assumes a validated definition (all five thresholds present, strictly increasing — the
     * loader guarantees it); a missing threshold is treated as unreachable rather than crashing.
     */
    public static FactionTier tierOf(FactionDefinition faction, int value) {
        FactionTier result = FactionTier.OUTSIDER;
        for (FactionTier tier : FactionTier.values()) {
            Integer threshold = faction.threshold(tier);
            if (threshold != null && value >= threshold) {
                result = tier;
            }
        }
        return result;
    }
}
