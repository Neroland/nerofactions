package za.co.neroland.nerofactions.map;

/**
 * The viewer's own standing with an outpost's faction, as the map shows it: a glyph key plus a colour, never colour
 * alone. Hostile is the mod's own refusal rule ({@code FactionTiers.isHostile}); friendly is Associate or above.
 */
public enum OutpostStanding {
    HOSTILE("map.nerofactions.standing.hostile", 0xFFD55E00),
    NEUTRAL("map.nerofactions.standing.neutral", 0xFFF2F5F8),
    FRIENDLY("map.nerofactions.standing.friendly", 0xFF009E73);

    private final String key;
    private final int argb;

    OutpostStanding(String key, int argb) {
        this.key = key;
        this.argb = argb;
    }

    /** The detail line's lang key; a map renders it as the hostile / neutral / friendly badge. */
    public String key() {
        return key;
    }

    public int argb() {
        return argb;
    }
}
