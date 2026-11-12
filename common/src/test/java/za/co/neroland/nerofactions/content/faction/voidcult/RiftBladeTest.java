package za.co.neroland.nerofactions.content.faction.voidcult;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import za.co.neroland.nerofactions.content.FactionDefinition;

class RiftBladeTest {
    private static final FactionDefinition CULT = new FactionDefinition(
            Identifier.parse("nerofactions:void_cult"), "Void Cult", "test",
            Map.of("outsider", 0, "associate", 100, "member", 400, "trusted", 1000, "inner_circle", 2500),
            Map.of(), List.of(), List.of(), Optional.empty());

    @Test
    void onlyEligibleMembersBlindSurvivingTargets() {
        assertEquals(RiftBlade.HitEffect.BLINDNESS, RiftBlade.hitEffect(CULT, true, 400, false, true));
        assertEquals(RiftBlade.HitEffect.NONE, RiftBlade.hitEffect(CULT, true, 399, false, true));
        assertEquals(RiftBlade.HitEffect.NONE, RiftBlade.hitEffect(CULT, false, 2500, false, true));
        assertEquals(RiftBlade.HitEffect.NONE, RiftBlade.hitEffect(null, true, 2500, false, true));
    }

    @Test
    void aLethalHitOnlySiphonsFromAHostileMob() {
        assertEquals(RiftBlade.HitEffect.SIPHON, RiftBlade.hitEffect(CULT, true, 400, true, true));
        assertEquals(RiftBlade.HitEffect.NONE, RiftBlade.hitEffect(CULT, true, 400, true, false));
        assertEquals(RiftBlade.HitEffect.NONE, RiftBlade.hitEffect(CULT, true, 399, true, true));
    }
}
