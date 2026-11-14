package za.co.neroland.nerofactions.content.faction.terraformingauthority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.content.faction.terraformingauthority.AtmosphereProcessorBlock.Output;

class AtmosphereProcessorTest {
    private static final Predicate<Output> ALWAYS = out -> true;

    @Test
    void radiusGrowsOnlyAfterFourSuccessfulPulsesAndRespectsTheLimit() {
        assertEquals(1, AtmosphereProcessorBlock.radius(0, 6));
        assertEquals(1, AtmosphereProcessorBlock.radius(3, 6));
        assertEquals(2, AtmosphereProcessorBlock.radius(4, 6));
        assertEquals(6, AtmosphereProcessorBlock.radius(100, 6));
        assertEquals(1, AtmosphereProcessorBlock.radius(-4, 0));
        assertEquals(8, AtmosphereProcessorBlock.radius(100, 99));
    }

    @Test
    void naturalGroundStillConverts() {
        assertEquals(Output.PODZOL, AtmosphereProcessorBlock.conversion("minecraft:dirt", false));
        assertEquals(Output.GRASS_BLOCK, AtmosphereProcessorBlock.conversion("minecraft:dirt", true));
        assertEquals(Output.MOSS_BLOCK, AtmosphereProcessorBlock.conversion("minecraft:sand", false));
        assertEquals(Output.MOSS_BLOCK, AtmosphereProcessorBlock.conversion("minecraft:gravel", true));
        assertEquals(Output.OAK_SAPLING, AtmosphereProcessorBlock.conversion("minecraft:grass_block", true));
        assertNull(AtmosphereProcessorBlock.conversion("minecraft:grass_block", false));
    }

    @Test
    void permittedTargetConverts() {
        assertEquals(Output.PODZOL,
                AtmosphereProcessorBlock.plan("minecraft:dirt", false, true, ALWAYS, ALWAYS));
    }

    @Test
    void buildingMaterialsNeverConvertAndNeverReachThePermissionSeam() {
        AtomicInteger asked = new AtomicInteger();
        Predicate<Output> seam = out -> { asked.incrementAndGet(); return true; };
        for (String id : new String[] {"minecraft:stone", "minecraft:cobblestone", "minecraft:oak_planks",
                "minecraft:stone_bricks", "minecraft:deepslate", "minecraft:glass", "minecraft:smooth_stone"}) {
            assertNull(AtmosphereProcessorBlock.conversion(id, false), id);
            assertNull(AtmosphereProcessorBlock.conversion(id, true), id);
            assertNull(AtmosphereProcessorBlock.plan(id, true, true, ALWAYS, seam), id);
        }
        assertEquals(0, asked.get());
    }

    @Test
    void refusedTargetIsSkippedSoNothingIsSpent() {
        AtomicInteger asked = new AtomicInteger();
        Predicate<Output> refuse = out -> { asked.incrementAndGet(); return false; };
        // A null plan is what makes the pulse loop skip the target before it shrinks the
        // reagent stack or consumes energy.
        assertNull(AtmosphereProcessorBlock.plan("minecraft:dirt", false, true, ALWAYS, refuse));
        assertEquals(1, asked.get());
    }

    @Test
    void cheapChecksRunBeforeTheLoaderEventIsPosted() {
        AtomicInteger asked = new AtomicInteger();
        Predicate<Output> seam = out -> { asked.incrementAndGet(); return true; };
        assertNull(AtmosphereProcessorBlock.plan("minecraft:dirt", false, false, ALWAYS, seam));
        assertNull(AtmosphereProcessorBlock.plan("minecraft:dirt", false, true, out -> false, seam));
        assertEquals(0, asked.get());
    }
}
