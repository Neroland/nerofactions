package za.co.neroland.nerofactions.content.faction.corporation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import za.co.neroland.nerofactions.content.faction.corporation.CorporateCounter.StampOutcome;

class CorporateStampTest {
    @Test
    void withACurrencyProviderAnEligibleStampReservesOneWrit() {
        var reserved = new AtomicInteger();
        assertEquals(StampOutcome.STAMPED, CorporateCounter.stampOutcome(true, true, true,
                () -> reserved.incrementAndGet() <= 3));
        assertEquals(1, reserved.get());
        assertEquals(StampOutcome.CAPPED, CorporateCounter.stampOutcome(true, true, true, () -> false));
    }

    @Test
    void withoutACurrencyProviderTheStampRefusesAndReservesNothing() {
        var reserved = new AtomicInteger();
        assertEquals(StampOutcome.NO_CURRENCY, CorporateCounter.stampOutcome(true, false, true,
                () -> reserved.incrementAndGet() > 0));
        assertEquals(StampOutcome.DENIED, CorporateCounter.stampOutcome(false, false, true,
                () -> reserved.incrementAndGet() > 0));
        assertEquals(StampOutcome.BAD_INPUT, CorporateCounter.stampOutcome(true, true, false,
                () -> reserved.incrementAndGet() > 0));
        assertEquals(0, reserved.get());
    }
}
