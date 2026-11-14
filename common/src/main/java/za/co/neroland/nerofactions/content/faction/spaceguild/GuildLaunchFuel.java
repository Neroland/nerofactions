package za.co.neroland.nerofactions.content.faction.spaceguild;

public final class GuildLaunchFuel {
    private GuildLaunchFuel() {
    }

    /**
     * Returns the Guild-adjusted amount for a validated launch quote. Nerospace currently
     * exposes read-only launch information and has no hook to apply such a quote.
     */
    public static long quotedUnits(long baseUnits) {
        if (baseUnits < 0) {
            throw new IllegalArgumentException("Launch fuel must be non-negative");
        }
        return baseUnits;
    }
}
