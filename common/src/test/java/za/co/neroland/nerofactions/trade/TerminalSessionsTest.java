package za.co.neroland.nerofactions.trade;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Plain-JVM tests for the transient terminal-session map, focused on its two privacy jobs:
 * {@link TerminalSessions#clear} is part of the erasure path ({@code NeroFactionsData.eraseLocal})
 * and must forget exactly one player; {@link TerminalSessions#clearAll} runs on server stop so the
 * UUID-keyed convenience data never outlives the process's current server.
 */
class TerminalSessionsTest {

    private static final Identifier GUILD = Identifier.parse("nerofactions:test_guild");
    private static final Identifier UNION = Identifier.parse("nerofactions:test_union");

    @AfterEach
    void wipe() {
        TerminalSessions.clearAll();
    }

    @Test
    void clearForgetsOnlyThatPlayer() {
        UUID erased = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        TerminalSessions.current(erased, List.of(GUILD, UNION), GUILD);
        TerminalSessions.current(kept, List.of(GUILD, UNION), UNION);
        assertTrue(TerminalSessions.hasSelection(erased));

        TerminalSessions.clear(erased);

        assertFalse(TerminalSessions.hasSelection(erased), "the erased player's row must be gone");
        assertTrue(TerminalSessions.hasSelection(kept), "another player's row must survive");
        assertEquals(UNION, TerminalSessions.current(kept, List.of(GUILD, UNION), GUILD),
                "the kept player's remembered selection still answers");
    }

    @Test
    void clearAllWipesEverySelection() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        TerminalSessions.current(a, List.of(GUILD), GUILD);
        TerminalSessions.current(b, List.of(UNION), UNION);

        TerminalSessions.clearAll();

        assertFalse(TerminalSessions.hasSelection(a));
        assertFalse(TerminalSessions.hasSelection(b));
    }

    @Test
    void nullPlayerIsSafeOnBothSeams() {
        assertDoesNotThrow(() -> TerminalSessions.clear(null));
        assertFalse(TerminalSessions.hasSelection(null));
    }
}
