package za.co.neroland.nerofactions.content.faction.spaceguild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class GuildGrappleTest {
    @Test
    void pullsTowardsAnchorWithBoundedSpeed() {
        Vec3 anchor = new Vec3(6, 8, 0);
        Vec3 pull = GuildGrapple.impulse(anchor);
        assertEquals(1.45, pull.length(), 0.0001);
        assertEquals(1, anchor.normalize().dot(pull.normalize()), 0.0001);
        assertTrue(GuildGrapple.impulse(new Vec3(12, 0, 0)).length() <= 1.55);
    }

    @Test
    void rejectsCloseFarAndInvalidAnchors() {
        assertEquals(Vec3.ZERO, GuildGrapple.impulse(new Vec3(1, 0, 0)));
        assertEquals(Vec3.ZERO, GuildGrapple.impulse(new Vec3(13, 0, 0)));
        assertEquals(Vec3.ZERO, GuildGrapple.impulse(new Vec3(Double.NaN, 0, 0)));
    }
}
