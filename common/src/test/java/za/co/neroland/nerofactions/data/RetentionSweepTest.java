package za.co.neroland.nerofactions.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plain-JVM tests for the retention sweep's decision core and day-roll schedule. The live wiring
 * ({@code sweep(server)}: Core's {@code PlayerActivity.stalerThan} + both stores'
 * {@code knownPlayers} + {@code NeroFactionsData.eraseLocal}) needs a server; the parameterised
 * seams below are exactly what it delegates to.
 */
class RetentionSweepTest {

    private static final long T0 = 1_700_000_000_000L;

    @BeforeEach
    @AfterEach
    void resetSchedule() {
        RetentionSweep.onServerStopped();
    }

    @Test
    void staleKnownPlayersArePurgedActiveOnesKept() {
        UUID inactive = UUID.randomUUID();
        UUID active = UUID.randomUUID();
        UUID staleStranger = UUID.randomUUID(); // stale per activity, but this mod holds nothing

        List<UUID> erased = new ArrayList<>();
        int purged = RetentionSweep.sweep(365, Set.of(inactive, active),
                days -> {
                    assertEquals(365, days, "the configured window must reach the activity query");
                    return List.of(inactive, staleStranger);
                },
                erased::add);

        assertEquals(1, purged);
        assertEquals(List.of(inactive), erased,
                "only players who are BOTH known to this mod AND stale may be purged");
    }

    @Test
    void zeroDisablesTheSweepWithoutConsultingActivity() {
        boolean[] queried = {false};
        List<UUID> erased = new ArrayList<>();

        int purged = RetentionSweep.sweep(0, Set.of(UUID.randomUUID()),
                days -> {
                    queried[0] = true;
                    return List.of();
                },
                erased::add);

        assertEquals(0, purged);
        assertFalse(queried[0], "0 = disabled; the activity record must not even be consulted");
        assertTrue(erased.isEmpty());
    }

    @Test
    void nothingKnownMeansNothingToDo() {
        boolean[] queried = {false};

        int purged = RetentionSweep.sweep(365, Set.of(), days -> {
            queried[0] = true;
            return List.of();
        }, uuid -> {
            throw new AssertionError("nothing may be erased");
        });

        assertEquals(0, purged);
        assertFalse(queried[0]);
    }

    @Test
    void dayRollFiresOnceOnLoadAndOncePerDay() {
        assertTrue(RetentionSweep.dueNow(T0), "the first check after a world loads must run");
        assertFalse(RetentionSweep.dueNow(T0), "the same instant must not run twice");
        assertFalse(RetentionSweep.dueNow(T0 + 60_000L), "a minute later, same day: no run");

        long nextDay = (Math.floorDiv(T0, RetentionSweep.DAY_MS) + 1) * RetentionSweep.DAY_MS;
        assertTrue(RetentionSweep.dueNow(nextDay), "the day roll must fire");
        assertFalse(RetentionSweep.dueNow(nextDay + 1L), "and only once that day");
    }

    @Test
    void serverStopForgetsTheBookmarkSoTheNextWorldSweepsOnLoad() {
        assertTrue(RetentionSweep.dueNow(T0));
        RetentionSweep.onServerStopped();
        assertTrue(RetentionSweep.dueNow(T0), "a fresh world must sweep on load, same day or not");
    }
}
