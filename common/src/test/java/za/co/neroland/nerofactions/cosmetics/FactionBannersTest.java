package za.co.neroland.nerofactions.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.cosmetics.FactionBanners.BannerScheme;
import za.co.neroland.nerofactions.cosmetics.FactionBanners.Layer;

/**
 * Component-level proof of the vanilla-banner cosmetics: every shipped faction has a designed
 * scheme in its documented palette, every layer references a <b>real vanilla banner-pattern id</b>
 * (the whitelist below is the vanilla registry's shipped contents — building the actual holders
 * needs a bootstrapped game and is exercised at runtime), and the schemes are visually distinct.
 */
class FactionBannersTest {

    /** The vanilla banner-pattern registry ids ({@code minecraft:banner_pattern}). */
    private static final Set<String> VANILLA_PATTERNS = Set.of(
            "base", "border", "bricks", "circle", "creeper", "cross", "curly_border",
            "diagonal_left", "diagonal_right", "diagonal_up_left", "diagonal_up_right", "flow",
            "flower", "globe", "gradient", "gradient_up", "guster", "half_horizontal",
            "half_horizontal_bottom", "half_vertical", "half_vertical_right", "mojang", "piglin",
            "rhombus", "skull", "small_stripes", "square_bottom_left", "square_bottom_right",
            "square_top_left", "square_top_right", "straight_cross", "stripe_bottom",
            "stripe_center", "stripe_downleft", "stripe_downright", "stripe_left", "stripe_middle",
            "stripe_right", "stripe_top", "triangle_bottom", "triangle_top", "triangles_bottom",
            "triangles_top");

    /** The documented palette: faction path → expected base colour. */
    private static final Map<String, DyeColor> BASE_COLOURS = Map.of(
            "space_guild", DyeColor.BLUE,
            "miner_union", DyeColor.ORANGE,
            "nero_corporation", DyeColor.BLACK,
            "void_cult", DyeColor.PURPLE,
            "terraforming_authority", DyeColor.GREEN,
            "free_colonists", DyeColor.BROWN,
            "salvagers", DyeColor.GRAY);

    private static Identifier faction(String path) {
        return Identifier.fromNamespaceAndPath("nerofactions", path);
    }

    @Test
    void everyShippedFactionHasADesignedSchemeInItsDocumentedBaseColour() {
        for (Map.Entry<String, DyeColor> expected : BASE_COLOURS.entrySet()) {
            BannerScheme scheme = FactionBanners.schemeFor(faction(expected.getKey()))
                    .orElseThrow(() -> new AssertionError(expected.getKey() + " has no scheme"));
            assertEquals(expected.getValue(), scheme.base(),
                    expected.getKey() + " base colour follows the documented palette");
            assertTrue(scheme.layers().size() >= 2 && scheme.layers().size() <= 3,
                    expected.getKey() + " carries 2-3 pattern layers");
        }
    }

    @Test
    void everyLayerReferencesARealVanillaBannerPattern() {
        for (String path : BASE_COLOURS.keySet()) {
            for (Layer layer : FactionBanners.schemeFor(faction(path)).orElseThrow().layers()) {
                assertEquals("minecraft", layer.pattern().getNamespace(),
                        path + " layers use vanilla patterns only (no custom art exists)");
                assertTrue(VANILLA_PATTERNS.contains(layer.pattern().getPath()),
                        path + " pattern " + layer.pattern() + " must be a vanilla pattern id");
            }
        }
        for (Layer layer : FactionBanners.FALLBACK.layers()) {
            assertTrue(VANILLA_PATTERNS.contains(layer.pattern().getPath()),
                    "fallback pattern " + layer.pattern() + " must be a vanilla pattern id");
        }
    }

    @Test
    void schemesAreVisuallyDistinct() {
        Set<List<Object>> seen = new HashSet<>();
        for (String path : BASE_COLOURS.keySet()) {
            BannerScheme scheme = FactionBanners.schemeFor(faction(path)).orElseThrow();
            assertTrue(seen.add(List.of(scheme.base(), scheme.layers())),
                    path + " must not share its exact design with another faction");
        }
        assertEquals(BASE_COLOURS.size(), seen.size());
    }

    @Test
    void spaceGuildSchemeIsExactlyAsDesigned() {
        BannerScheme scheme = FactionBanners.schemeFor(faction("space_guild")).orElseThrow();
        assertEquals(DyeColor.BLUE, scheme.base());
        assertEquals(List.of(
                new Layer(Identifier.parse("minecraft:stripe_center"), DyeColor.WHITE),
                new Layer(Identifier.parse("minecraft:triangle_top"), DyeColor.LIGHT_GRAY),
                new Layer(Identifier.parse("minecraft:border"), DyeColor.WHITE)),
                scheme.layers(), "blue/silver — the documented Space Guild palette");
    }

    @Test
    void unknownFactionsFallBackToTheNeutralScheme() {
        Identifier unknown = Identifier.parse("somepack:new_faction");
        assertTrue(FactionBanners.schemeFor(unknown).isEmpty(), "no designed scheme");
        BannerScheme scheme = FactionBanners.schemeOrFallback(unknown);
        assertEquals(FactionBanners.FALLBACK, scheme);
        assertEquals(DyeColor.WHITE, scheme.base());
        assertNotEquals(0, scheme.layers().size());
    }
}
