package za.co.neroland.nerofactions.network;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire format for the "next tier rewards" line of {@link StandingSnapshot}. The server sends
 * machine-readable tokens instead of English prose so the client can show localised item and
 * cosmetic names (the old format was raw item-id paths like "faction banner ×1", which no
 * language file could translate).
 *
 * <p>Tokens are joined by {@link #SEPARATOR}: {@code i:<item id>:<count>} for an item reward and
 * {@code c:<cosmetic name>} for a cosmetic. Parsing is tolerant: a string from an older server
 * (plain prose) parses to an empty list, and an entry cut short by the 256-char snapshot bound
 * is dropped rather than shown half-written.
 */
public final class RewardTokens {

    public static final String SEPARATOR = ";";

    private RewardTokens() {
    }

    public static String item(String itemId, int count) {
        return "i:" + itemId + ":" + count;
    }

    public static String cosmetic(String name) {
        return "c:" + name;
    }

    /** A parsed reward: an item ({@code itemId} set) or a cosmetic ({@code cosmetic} set). */
    public record Token(String itemId, int count, String cosmetic) {
        public boolean isItem() {
            return itemId != null;
        }
    }

    /** True when the string uses this token format (as opposed to an older server's prose). */
    public static boolean isTokenised(String raw) {
        return raw.startsWith("i:") || raw.startsWith("c:");
    }

    public static List<Token> parse(String raw) {
        List<Token> out = new ArrayList<>();
        if (raw == null || !isTokenised(raw)) {
            return out;
        }
        for (String entry : raw.split(SEPARATOR, -1)) {
            if (entry.startsWith("c:") && entry.length() > 2) {
                out.add(new Token(null, 0, entry.substring(2)));
            } else if (entry.startsWith("i:")) {
                int lastColon = entry.lastIndexOf(':');
                // "i:" + namespace ":" path ":" count — need at least two colons after the prefix.
                if (lastColon <= 2 || entry.indexOf(':', 2) == lastColon) {
                    continue;
                }
                try {
                    int count = Integer.parseInt(entry.substring(lastColon + 1));
                    out.add(new Token(entry.substring(2, lastColon), count, null));
                } catch (NumberFormatException truncated) {
                    // Cut off by the snapshot length bound; drop it.
                }
            }
        }
        return out;
    }
}
