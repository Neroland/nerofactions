package za.co.neroland.nerofactions.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapLayerConformance;
import za.co.neroland.nerolandcore.map.MapLayerInfo;
import za.co.neroland.nerolandcore.map.MapLayerRegistry;
import za.co.neroland.nerolandcore.map.MapMarker;
import za.co.neroland.nerolandcore.map.MapQueryResult;
import za.co.neroland.nerolandcore.map.MapScope;
import za.co.neroland.nerolandcore.map.MapViewer;

/**
 * The outpost layer against Core's {@link MapLayerConformance} (two viewers, far bounds, offline, absent dimension,
 * per-item proximity, identity) and the 48-block clamp: an outpost 60 blocks off stays hidden however wide the
 * requested bounds are, and standing travels as a key plus a colour.
 */
class FactionsMapLayersTest {

    static final ResourceKey<Level> HOME = dim("home_test");
    static final ResourceKey<Level> MOON = dim("moon_test");
    static final UUID VIEWER = UUID.fromString("00000000-0000-4000-8000-000000000001");
    static final UUID STRANGER = UUID.fromString("00000000-0000-4000-8000-000000000002");
    static final UUID THIRD = UUID.fromString("00000000-0000-4000-8000-000000000003");

    static ResourceKey<Level> dim(String path) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("nerofactions_test", path));
    }

    /** Seeded outposts: one in reach (footprint 20..40), one whose centre is out of reach but footprint is not,
     *  one 60 blocks off, one far away. The second gets its pin at the footprint's nearest point. The source returns them all; the provider must apply the clamp. */
    static final class Fake implements OutpostMapSource {
        final List<Seen> seen = new ArrayList<>();

        Fake() {
            seen.add(seen(1, OutpostFaction.VOID_CULT, 20, -10, 40, 10));
            seen.add(seen(2, OutpostFaction.SPACE_GUILD, -80, -20, -40, 20));
            seen.add(seen(3, OutpostFaction.MINER_UNION, 0, 60, 20, 80));
            seen.add(seen(4, OutpostFaction.SALVAGERS, 3_000, 3_000, 3_040, 3_040));
        }

        static Seen seen(int x, OutpostFaction f, int minX, int minZ, int maxX, int maxZ) {
            return new Seen(x, f, minX, minZ, maxX, maxZ,
                    new BlockPos((minX + maxX) / 2, 64, (minZ + maxZ) / 2));
        }

        @Override
        public List<Seen> near(ResourceKey<Level> dimension, BlockPos around, int radius) {
            return HOME.equals(dimension) ? List.copyOf(seen) : List.of();
        }

        @Override
        public OutpostStanding standing(UUID viewer, OutpostFaction faction) {
            if (!VIEWER.equals(viewer)) {
                return OutpostStanding.NEUTRAL;
            }
            return faction == OutpostFaction.VOID_CULT ? OutpostStanding.HOSTILE : OutpostStanding.FRIENDLY;
        }
    }

    static FactionsMapLayers provider() {
        Fake fake = new Fake();
        return new FactionsMapLayers(server -> fake);
    }

    static MapViewer at(UUID id, int x, int z) {
        return MapViewer.forTest(id, null, HOME, new BlockPos(x, 64, z));
    }

    @Test
    void conformance() {
        MapLayerConformance.create(provider())
                .viewers(at(VIEWER, 0, 0), at(STRANGER, 5, 0))
                .dimensions(HOME, MOON)
                .bounds(MapBounds.around(0, 0, 512))
                .farBounds(MapBounds.around(20_000, 20_000, 256))
                .forbiddenIds(THIRD)
                .expectEntitledSees(FactionsMapLayers.OUTPOSTS, 2)
                .verify();
    }

    @Test
    void layerIsProximityClampedTo48() {
        MapLayerInfo info = provider().layers().get(0);
        assertEquals(MapScope.PROXIMITY, info.scope());
        assertEquals(48, info.proximityRadius());
        assertFalse(info.defaultOnMinimap());
    }

    private static MapQueryResult query(MapViewer viewer, MapBounds bounds) {
        MapLayerRegistry.register(provider());
        MapQueryResult r = MapLayerRegistry.query("nerofactions", viewer, HOME, bounds);
        assertFalse(r.failed());
        return r;
    }

    @Test
    void onlyOutpostsWithin48BlocksShowWhateverTheBounds() {
        MapQueryResult r = query(at(VIEWER, 0, 0), MapBounds.around(0, 0, 8_000));
        String inReach = Long.toHexString(1L);
        String edge = Long.toHexString(2L);
        String sixty = Long.toHexString(3L);
        List<String> ids = new ArrayList<>();
        r.markers().forEach(m -> ids.add(m.id()));
        r.areas().forEach(a -> ids.add(a.id()));
        assertTrue(ids.contains("outpost/" + inReach));
        assertTrue(ids.contains("outpost_area/" + inReach));
        assertTrue(ids.contains("outpost_area/" + edge), "footprint in reach");
        assertTrue(ids.contains("outpost/" + edge), "centre out of reach: pin at the footprint's nearest point");
        assertFalse(ids.stream().anyMatch(i -> i.endsWith(sixty)), "60 blocks off stays hidden");
        assertEquals(4, ids.size());
        MapMarker edgePin = r.markers().stream().filter(m -> m.id().equals("outpost/" + edge)).findFirst()
                .orElseThrow();
        assertEquals(-40, edgePin.x());
        assertEquals(0, edgePin.z());
        // Standing is never colour alone: every drawn footprint has a marker carrying the standing detail.
        r.areas().forEach(a -> {
            String key = a.id().substring("outpost_area/".length());
            MapMarker pin = r.markers().stream().filter(m -> m.id().equals("outpost/" + key)).findFirst()
                    .orElseThrow(() -> new AssertionError("no marker for area " + a.id()));
            assertFalse(pin.details().isEmpty(), "marker for " + key + " carries the standing");
            assertTrue(pin.details().get(0).text().startsWith("map.nerofactions.standing."), pin.details().get(0).text());
        });
    }

    @Test
    void standingIsAKeyAndAColour() {
        MapMarker m = query(at(VIEWER, 0, 0), MapBounds.around(0, 0, 512)).markers().get(0);
        assertEquals(OutpostStanding.HOSTILE.argb(), m.argb());
        assertEquals(OutpostStanding.HOSTILE.key(), m.details().get(0).text());
    }

    @Test
    void offlineOrElsewhereSeesNothing() {
        MapLayerRegistry.register(provider());
        assertTrue(MapLayerRegistry.query("nerofactions", MapViewer.forTest(VIEWER, null, null, null), HOME,
                MapBounds.around(0, 0, 512)).markers().isEmpty());
        assertTrue(MapLayerRegistry.query("nerofactions", MapViewer.forTest(VIEWER, null, MOON, new BlockPos(0, 64, 0)),
                HOME, MapBounds.around(0, 0, 512)).areas().isEmpty());
    }
}
