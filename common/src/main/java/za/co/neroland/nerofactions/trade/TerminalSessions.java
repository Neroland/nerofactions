package za.co.neroland.nerofactions.trade;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.Identifier;

/**
 * The trade terminal's <b>transient</b> multi-faction selection: which of a player's factions the
 * terminal last showed them, so sneak-use can cycle through their memberships. Deliberately
 * <b>in-memory only</b> — no SavedData, no persistence, no timestamps: it is a UI convenience,
 * not a record, and a restart simply forgets it (the terminal then reopens on the player's
 * best-standing faction).
 *
 * <p><b>POPIA/GDPR:</b> the map holds player UUID → faction id for the current process only. It
 * is wiped wholesale when the server stops ({@link #clearAll}, called from every loader's
 * server-stopped hook beside {@code FactionDefinitions.forgetServer}), and an entry self-corrects
 * the moment it no longer names one of the player's member factions.
 */
public final class TerminalSessions {

    private static final Map<UUID, Identifier> SELECTED = new ConcurrentHashMap<>();

    private TerminalSessions() {
    }

    /**
     * The faction the terminal should show {@code player} without cycling: the remembered
     * selection if it is still one of {@code memberships}, else {@code preferred} (the caller's
     * best-standing pick), which becomes the new selection.
     */
    public static Identifier current(UUID player, List<Identifier> memberships, Identifier preferred) {
        Identifier remembered = SELECTED.get(player);
        if (remembered != null && memberships.contains(remembered)) {
            return remembered;
        }
        SELECTED.put(player, preferred);
        return preferred;
    }

    /**
     * Advances the selection to the next faction after the current one in {@code memberships}
     * (which the caller passes in a stable sorted order), wrapping around, and remembers it.
     */
    public static Identifier next(UUID player, List<Identifier> memberships, Identifier preferred) {
        Identifier current = current(player, memberships, preferred);
        int index = memberships.indexOf(current);
        Identifier advanced = memberships.get((index + 1) % memberships.size());
        SELECTED.put(player, advanced);
        return advanced;
    }

    /**
     * Drops one player's selection (nothing else references it). Also the erasure path:
     * {@code NeroFactionsData.eraseLocal} calls this so an erasure request clears even this
     * transient in-memory row in the same request — erasure means <em>everything</em>, not just
     * what is on disk.
     */
    public static void clear(UUID player) {
        if (player != null) {
            SELECTED.remove(player);
        }
    }

    /** Whether a selection is currently remembered for {@code player}. Package-private test seam. */
    static boolean hasSelection(UUID player) {
        return player != null && SELECTED.containsKey(player);
    }

    /** Wipes every selection; called when a server stops. */
    public static void clearAll() {
        SELECTED.clear();
    }
}
