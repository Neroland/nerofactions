package za.co.neroland.nerofactions.cosmetics;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.content.FactionDefinition;

/**
 * The faction cosmetics of 0.1.0: <b>pre-styled vanilla banners</b>. Each faction gets a banner
 * {@link ItemStack} in its base colour with two or three vanilla banner-pattern layers in its
 * palette, named after the faction — built entirely in code from vanilla content, because
 * <b>no custom art can be authored for this release</b>.
 *
 * <p><b>The scope decision, written down for the changelog:</b> the faction JSONs'
 * {@code cosmetics.banner_pattern} and {@code cosmetics.trim_material} fields remain
 * validated-but-dormant <em>forward references</em>. Real custom banner-pattern and armour-trim
 * registries require texture assets that cannot be created in this release, so they are deferred;
 * until they land, <em>both</em> cosmetic reward kinds ({@code "banner_pattern"} and
 * {@code "trim_material"}) resolve to the faction's pre-styled vanilla banner — an honest,
 * craftable-looking cosmetic instead of a texture-less registry entry. Do not remove the JSON
 * fields; packs already validate against them.
 *
 * <p>The class splits pure from game-bound the ecosystem way: {@link #schemeFor} is plain data
 * (colours + vanilla pattern <em>ids</em> — testable on a bare JVM), and {@link #build} is the
 * only part that touches registries ({@code minecraft:banner_pattern} is data-driven in 26.x, so
 * holders are resolved through the server's {@link HolderLookup.Provider}). A pattern id missing
 * from the running game's registry is skipped with a warn rather than failing the grant.
 */
public final class FactionBanners {

    /** One pattern layer: a vanilla banner-pattern id plus the dye it is rendered in. */
    public record Layer(Identifier pattern, DyeColor colour) {

        static Layer of(String vanillaPatternId, DyeColor colour) {
            return new Layer(Identifier.withDefaultNamespace(vanillaPatternId), colour);
        }
    }

    /** A faction's banner design: the banner item's base colour plus its pattern layers. */
    public record BannerScheme(DyeColor base, List<Layer> layers) {
    }

    /**
     * The seven shipped designs, straight from each faction's documented palette. Keys are the
     * shipped faction ids; any other faction (datapack additions) gets {@link #FALLBACK}.
     */
    private static final Map<Identifier, BannerScheme> SCHEMES = Map.of(
            id("space_guild"), new BannerScheme(DyeColor.BLUE, List.of(
                    Layer.of("stripe_center", DyeColor.WHITE),
                    Layer.of("triangle_top", DyeColor.LIGHT_GRAY),
                    Layer.of("border", DyeColor.WHITE))),
            id("miner_union"), new BannerScheme(DyeColor.ORANGE, List.of(
                    Layer.of("rhombus", DyeColor.GRAY),
                    Layer.of("stripe_bottom", DyeColor.GRAY),
                    Layer.of("border", DyeColor.BLACK))),
            id("nero_corporation"), new BannerScheme(DyeColor.BLACK, List.of(
                    Layer.of("stripe_downright", DyeColor.YELLOW),
                    Layer.of("square_top_left", DyeColor.YELLOW),
                    Layer.of("border", DyeColor.GRAY))),
            id("void_cult"), new BannerScheme(DyeColor.PURPLE, List.of(
                    Layer.of("circle", DyeColor.BLACK),
                    Layer.of("gradient_up", DyeColor.BLACK))),
            id("terraforming_authority"), new BannerScheme(DyeColor.GREEN, List.of(
                    Layer.of("flower", DyeColor.WHITE),
                    Layer.of("stripe_bottom", DyeColor.WHITE))),
            id("free_colonists"), new BannerScheme(DyeColor.BROWN, List.of(
                    Layer.of("half_horizontal", DyeColor.GREEN),
                    Layer.of("bricks", DyeColor.BROWN),
                    Layer.of("border", DyeColor.GREEN))),
            id("salvagers"), new BannerScheme(DyeColor.GRAY, List.of(
                    Layer.of("cross", DyeColor.RED),
                    Layer.of("bricks", DyeColor.LIGHT_GRAY),
                    Layer.of("border", DyeColor.BLACK))));

    /** What a datapack-added faction without a designed scheme gets: neutral but recognisable. */
    static final BannerScheme FALLBACK = new BannerScheme(DyeColor.WHITE, List.of(
            Layer.of("rhombus", DyeColor.GRAY),
            Layer.of("border", DyeColor.GRAY)));

    private FactionBanners() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroFactionsCommon.MOD_ID, path);
    }

    /** The designed scheme for a shipped faction, or empty for anything else. Pure. */
    public static Optional<BannerScheme> schemeFor(Identifier faction) {
        return Optional.ofNullable(SCHEMES.get(faction));
    }

    /** The scheme actually used for a faction — designed if shipped, {@link #FALLBACK} otherwise. */
    public static BannerScheme schemeOrFallback(Identifier faction) {
        return SCHEMES.getOrDefault(faction, FALLBACK);
    }

    /**
     * Builds the faction's banner stack: base-colour banner item, pattern layers as 26.x item
     * components ({@code minecraft:banner_patterns}), and a literal custom name derived from the
     * faction's data-driven display name ("Space Guild Banner" — literal because faction names are
     * datapack strings, not lang keys).
     */
    public static ItemStack build(HolderLookup.Provider registries, FactionDefinition faction) {
        BannerScheme scheme = schemeOrFallback(faction.id());
        ItemStack stack = new ItemStack(bannerItem(scheme.base()));
        BannerPatternLayers.Builder layers = new BannerPatternLayers.Builder();
        HolderLookup.RegistryLookup<BannerPattern> lookup =
                registries.lookupOrThrow(Registries.BANNER_PATTERN);
        for (Layer layer : scheme.layers()) {
            ResourceKey<BannerPattern> key =
                    ResourceKey.create(Registries.BANNER_PATTERN, layer.pattern());
            Optional<Holder.Reference<BannerPattern>> holder = lookup.get(key);
            if (holder.isPresent()) {
                layers.add(holder.get(), layer.colour());
            } else {
                // A datapack removed a vanilla pattern; the banner simply loses that layer.
                NeroFactionsCommon.LOGGER.warn(
                        "[NeroFactions] Banner pattern {} is not registered; skipping that layer "
                                + "of the {} banner.", layer.pattern(), faction.id());
            }
        }
        stack.set(DataComponents.BANNER_PATTERNS, layers.build());
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(faction.displayName() + " Banner"));
        return stack;
    }

    /**
     * The vanilla banner item in this base colour, resolved by its stable data id
     * ({@code minecraft:<colour>_banner}) — 26.x ships no per-colour item constants, and the data
     * ids are the compatibility surface anyway. Falls back to the white banner if a colour id is
     * somehow absent (defensive; vanilla always registers all sixteen).
     */
    static Item bannerItem(DyeColor base) {
        Identifier id = Identifier.withDefaultNamespace(base.getName() + "_banner");
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            return BuiltInRegistries.ITEM.getValue(id);
        }
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("white_banner"));
    }
}
