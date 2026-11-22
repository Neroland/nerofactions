package za.co.neroland.nerofactions.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerolandcore.map.MapArea;
import za.co.neroland.nerolandcore.map.MapAreaStyle;
import za.co.neroland.nerolandcore.map.MapBounds;
import za.co.neroland.nerolandcore.map.MapLabel;
import za.co.neroland.nerolandcore.map.MapLayerInfo;
import za.co.neroland.nerolandcore.map.MapLayerProvider;
import za.co.neroland.nerolandcore.map.MapLayerRegistry;
import za.co.neroland.nerolandcore.map.MapLayerSink;
import za.co.neroland.nerolandcore.map.MapMarker;
import za.co.neroland.nerolandcore.map.MapScope;
import za.co.neroland.nerolandcore.map.MapViewer;

/**
 * NeroFactions' map layer, offered through Neroland Core's map registry; NeroFactions depends on no map mod.
 *
 * <p>{@code outposts} is a {@link za.co.neroland.nerolandcore.map.MapScope#PROXIMITY} layer clamped to
 * {@value #RADIUS} blocks: an outpost (a worldgen structure with no owner) shows only while the viewer, online and
 * in the same dimension, stands within {@value #RADIUS} blocks of its footprint. That is roughly "has it in sight",
 * so the map never works as a structure locator. The footprint is drawn as an area with a marker at its centre, or
 * at the footprint's nearest point when the centre is out of range. Both carry the viewer's own standing as a detail key (hostile / neutral /
 * friendly) and a matching colour. A map consumer keeps proximity markers transient.
 *
 * <p>{@code outposts_discovered} is a {@link MapScope#DISCOVERED} layer: the outposts this viewer has stood inside,
 * read from their own visit record (online or offline, any distance), drawn the same way. Nobody else's visits are
 * ever read. The proximity layer stays identical for everyone in range (Core's proximity contract), so a
 * discovered outpost in reach appears on both layers; a map can hide either.
 */
public final class FactionsMapLayers implements MapLayerProvider {

    private static final Logger LOG = LoggerFactory.getLogger("NeroFactions/Map");

    /** Roughly what a player can see from where they stand. */
    public static final int RADIUS = 48;

    public static final Identifier OUTPOSTS = Identifier.fromNamespaceAndPath(NeroFactionsCommon.MOD_ID, "outposts");
    public static final Identifier OUTPOSTS_DISCOVERED =
            Identifier.fromNamespaceAndPath(NeroFactionsCommon.MOD_ID, "outposts_discovered");
    static final Identifier ICON = Identifier.fromNamespaceAndPath(NeroFactionsCommon.MOD_ID, "outpost");

    private static final List<MapLayerInfo> LAYERS = List.of(
            MapLayerInfo.proximityBuilt(OUTPOSTS, "map.layer.nerofactions.outposts", ICON).withProximityRadius(RADIUS),
            MapLayerInfo.of(OUTPOSTS_DISCOVERED, "map.layer.nerofactions.outposts_discovered", ICON,
                    MapScope.DISCOVERED));

    private final Function<MinecraftServer, OutpostMapSource> sources;

    FactionsMapLayers(Function<MinecraftServer, OutpostMapSource> sources) {
        this.sources = sources;
    }

    /** Registers the provider with Core's map registry. Never throws. */
    public static void register() {
        try {
            MapLayerRegistry.register(new FactionsMapLayers(OutpostMapSource::of));
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("NeroFactions map layer not registered ({})", e.getClass().getSimpleName());
        }
    }

    @Override
    public String modId() {
        return NeroFactionsCommon.MOD_ID;
    }

    @Override
    public List<MapLayerInfo> layers() {
        return LAYERS;
    }

    @Override
    public void query(MapViewer viewer, ResourceKey<Level> dimension, MapBounds bounds, MapLayerSink sink) {
        if (viewer == null || dimension == null || sink == null) {
            return;
        }
        OutpostMapSource source = sources.apply(viewer.server());
        if (source == null) {
            return;
        }
        List<OutpostMapSource.Seen> discovered = viewer.id() == null ? List.of()
                : source.discovered(viewer.id(), dimension);
        if (bounds != null) {
            queryDiscovered(viewer, dimension, bounds, sink, source, discovered);
        }
        if (viewer.online() && dimension.equals(viewer.dimension().orElse(null))) {
            queryNearby(viewer, dimension, sink, source);
        }
    }

    private static void queryDiscovered(MapViewer viewer, ResourceKey<Level> dimension, MapBounds bounds,
            MapLayerSink sink, OutpostMapSource source, List<OutpostMapSource.Seen> discovered) {
        List<OutpostMapSource.Seen> inView = new ArrayList<>();
        for (OutpostMapSource.Seen outpost : discovered) {
            if (bounds.intersects(new MapBounds(outpost.minX(), outpost.minZ(), outpost.maxX(), outpost.maxZ()))) {
                inView.add(outpost);
            }
        }
        BlockPos centre = new BlockPos(bounds.centreX(), 0, bounds.centreZ());
        inView.sort(Comparator.comparingLong(s -> distSqr(s, centre)));
        for (OutpostMapSource.Seen outpost : inView) {
            if (sink.isFull(OUTPOSTS_DISCOVERED)) {
                return;
            }
            OutpostStanding standing = source.standing(viewer.id(), outpost.faction());
            MapLabel name = MapLabel.translatable("map.nerofactions.outpost." + outpost.faction().getSerializedName());
            String key = Long.toHexString(outpost.startChunk());
            sink.area(new MapArea.Rect("discovered_area/" + key, OUTPOSTS_DISCOVERED, dimension,
                    MapAreaStyle.of(standing.argb() & 0x33FFFFFF, standing.argb()).withLabel(name),
                    outpost.minX(), outpost.minZ(), outpost.maxX(), outpost.maxZ()));
            sink.marker(MapMarker.builder("discovered/" + key, OUTPOSTS_DISCOVERED, dimension, outpost.centre())
                    .label(name).argb(standing.argb()).detail(MapLabel.translatable(standing.key())).build());
        }
    }

    private static void queryNearby(MapViewer viewer, ResourceKey<Level> dimension, MapLayerSink sink,
            OutpostMapSource source) {
        BlockPos at = viewer.position().orElseThrow();
        List<OutpostMapSource.Seen> seen = new ArrayList<>(source.near(dimension, at, RADIUS));
        seen.sort(Comparator.comparingLong(s -> distSqr(s, at)));
        for (OutpostMapSource.Seen outpost : seen) {
            if (sink.isFull(OUTPOSTS)) {
                return;
            }
            MapBounds box = new MapBounds(outpost.minX(), outpost.minZ(), outpost.maxX(), outpost.maxZ());
            if (!viewer.within(dimension, box, RADIUS)) {
                continue;
            }
            OutpostStanding standing = source.standing(viewer.id(), outpost.faction());
            MapLabel name = MapLabel.translatable("map.nerofactions.outpost." + outpost.faction().getSerializedName());
            MapLabel standingLine = MapLabel.translatable(standing.key());
            String key = Long.toHexString(outpost.startChunk());
            sink.area(new MapArea.Rect("outpost_area/" + key, OUTPOSTS, dimension,
                    MapAreaStyle.of(standing.argb() & 0x33FFFFFF, standing.argb()).withLabel(name),
                    box.minX(), box.minZ(), box.maxX(), box.maxZ()));
            sink.marker(MapMarker.builder("outpost/" + key, OUTPOSTS, dimension, markerAt(viewer, dimension, box, outpost.centre()))
                    .label(name).argb(standing.argb()).detail(standingLine).build());
        }
    }

    /**
     * Where the outpost's marker goes: its centre when that is in range, otherwise the point of its footprint nearest
     * the viewer, which is in range whenever the footprint is. Every drawn footprint therefore has a marker carrying
     * the standing detail, so standing is never shown by colour alone.
     */
    static BlockPos markerAt(MapViewer viewer, ResourceKey<Level> dimension, MapBounds box, BlockPos centre) {
        if (viewer.within(dimension, centre.getX(), centre.getZ(), RADIUS)) {
            return centre;
        }
        BlockPos at = viewer.position().orElseThrow();
        return new BlockPos(box.nearestX(at.getX()), centre.getY(), box.nearestZ(at.getZ()));
    }

    private static long distSqr(OutpostMapSource.Seen s, BlockPos at) {
        long dx = (long) Math.max(s.minX(), Math.min(at.getX(), s.maxX())) - at.getX();
        long dz = (long) Math.max(s.minZ(), Math.min(at.getZ(), s.maxZ())) - at.getZ();
        return dx * dx + dz * dz;
    }
}
