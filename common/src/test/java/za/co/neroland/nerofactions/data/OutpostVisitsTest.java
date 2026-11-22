package za.co.neroland.nerofactions.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.content.faction.OutpostFaction;

/** Discovered outposts on the visit record: per player, capped, old saves load, erased with the player. */
class OutpostVisitsTest {

    static final UUID A = UUID.fromString("00000000-0000-4000-8000-00000000000a");
    static final UUID B = UUID.fromString("00000000-0000-4000-8000-00000000000b");
    static final Identifier HOME = Identifier.parse("minecraft:overworld");
    static final Identifier MOON = Identifier.parse("nerospace:luna");

    static BoundingBox box(int x, int z) {
        return new BoundingBox(x, 60, z, x + 20, 80, z + 20);
    }

    @Test
    void discoveredOutpostsArePerPlayerAndPerDimension() {
        OutpostVisits visits = new OutpostVisits();
        assertTrue(visits.discover(A, OutpostFaction.VOID_CULT, HOME, box(0, 0), 64));
        assertFalse(visits.discover(A, OutpostFaction.VOID_CULT, HOME, box(0, 0), 64), "same outpost twice");
        assertTrue(visits.discover(A, OutpostFaction.SALVAGERS, MOON, box(100, 100), 70));
        assertEquals(1, visits.discovered(A, HOME).size());
        assertEquals(10, visits.discovered(A, HOME).get(0).centreX());
        assertEquals(1, visits.discovered(A, MOON).size());
        assertTrue(visits.discovered(B, HOME).isEmpty(), "never another player's rows");
        assertFalse(visits.discover(A, OutpostFaction.NONE, HOME, box(5, 5), 64));
    }

    @Test
    void cappedPerPlayerOldestDropped() {
        OutpostVisits visits = new OutpostVisits();
        for (int i = 0; i <= OutpostVisits.MAX_DISCOVERED; i++) {
            visits.discover(A, OutpostFaction.MINER_UNION, HOME, box(i * 100, 0), 64);
        }
        assertEquals(OutpostVisits.MAX_DISCOVERED, visits.discovered(A, HOME).size());
        assertEquals(100, visits.discovered(A, HOME).get(0).minX(), "the first one was dropped");
    }

    @Test
    void roundTripsAndOldSavesLoad() {
        OutpostVisits visits = new OutpostVisits();
        visits.record(A, OutpostFaction.VOID_CULT, HOME);
        visits.discover(A, OutpostFaction.VOID_CULT, HOME, box(0, 0), 64);
        JsonElement saved = OutpostVisits.CODEC.encodeStart(JsonOps.INSTANCE, visits).getOrThrow();
        OutpostVisits loaded = OutpostVisits.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(1, loaded.discovered(A, HOME).size());

        // A save written before discovered outposts: the same shape without the "outposts" field.
        OutpostVisits before = new OutpostVisits();
        before.record(A, OutpostFaction.VOID_CULT, HOME);
        JsonElement old = OutpostVisits.CODEC.encodeStart(JsonOps.INSTANCE, before).getOrThrow();
        old.getAsJsonObject().getAsJsonArray("players").forEach(p -> p.getAsJsonObject().remove("outposts"));
        OutpostVisits legacy = OutpostVisits.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
        assertTrue(legacy.discovered(A, HOME).isEmpty());
        assertEquals(1, legacy.export(A).size());
    }

    @Test
    void exportCarriesDiscoveredRowsAndForgetErasesThem() {
        OutpostVisits visits = new OutpostVisits();
        visits.record(A, OutpostFaction.VOID_CULT, HOME);
        visits.discover(A, OutpostFaction.VOID_CULT, HOME, box(0, 0), 64);
        JsonArray all = visits.exportAll(A);
        assertEquals(2, all.size());
        assertEquals(1, visits.export(A).size(), "the link export stays coordinate-free");
        visits.discover(B, OutpostFaction.SALVAGERS, HOME, box(50, 50), 64);
        assertTrue(visits.knownPlayers().contains(B), "a player with only discovered rows is known to the sweep");
        visits.forget(A);
        assertTrue(visits.discovered(A, HOME).isEmpty());
        assertEquals(0, visits.exportAll(A).size());
        assertEquals(1, visits.discovered(B, HOME).size());
    }
}
