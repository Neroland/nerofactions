package za.co.neroland.nerofactions.content.faction.minerunion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.faction.minerunion.UnionBreaker.MiningShape;

class UnionBreakerTest {
    @Test
    void memberClearsOnePlaneAndTrustedExtendsItInward() {
        BlockPos center = new BlockPos(4, 30, 7);
        for (Direction inward : Direction.values()) {
            var member = MiningShape.positions(center, inward, FactionTier.MEMBER);
            var trusted = MiningShape.positions(center, inward, FactionTier.TRUSTED);
            assertEquals(8, new HashSet<>(member).size());
            assertEquals(17, new HashSet<>(trusted).size());
            assertFalse(trusted.contains(center));
            assertFalse(trusted.contains(center.relative(inward.getOpposite())));
            assertTrue(trusted.contains(center.relative(inward)));
            assertTrue(trusted.containsAll(member));
        }
        assertTrue(MiningShape.positions(center, Direction.UP, FactionTier.ASSOCIATE).isEmpty());
    }

    @Test
    void dominantLookAxisDeterminesTheExcavationDirection() {
        assertEquals(Direction.DOWN, MiningShape.inward(new Vec3(0.3, -0.9, 0.1)));
        assertEquals(Direction.WEST, MiningShape.inward(new Vec3(-0.9, 0.4, 0.1)));
        assertEquals(Direction.SOUTH, MiningShape.inward(new Vec3(0.1, 0.3, 0.9)));
    }
}
