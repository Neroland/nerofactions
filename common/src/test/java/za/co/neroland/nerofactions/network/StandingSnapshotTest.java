package za.co.neroland.nerofactions.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import za.co.neroland.nerofactions.client.FactionsClientState;

class StandingSnapshotTest {
    @Test
    void roundTripPreservesSignedStandingAndClearsBetweenSessions() {
        var row = new StandingSnapshot.Row(Identifier.parse("nerofactions:miner_union"), "Miner Union",
                -101, 0, 100, true, true, 0, "Theme", "Motto", "#D5A44C", "minecraft:iron_pickaxe", "", "", "coal ×16");
        var snapshot = new StandingSnapshot(List.of(row), 123L, 30, 50, false);
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            StandingSnapshot.STREAM_CODEC.encode(buf, snapshot);
            assertEquals(snapshot, StandingSnapshot.STREAM_CODEC.decode(buf));
            FactionsClientState.accept(snapshot);
            assertEquals(snapshot, FactionsClientState.standing());
        } finally {
            FactionsClientState.clear();
            buf.release();
        }
        assertTrue(FactionsClientState.standing().factions().isEmpty());
        assertEquals(0L, FactionsClientState.standing().cooldownUntil());
    }

    @Test
    void rejectsOversizedAndNegativeListCounts() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(StandingSnapshot.MAX_FACTIONS + 1);
            assertThrows(IllegalArgumentException.class, () -> StandingSnapshot.STREAM_CODEC.decode(buf));
            buf.clear();
            buf.writeVarInt(-1);
            assertThrows(IllegalArgumentException.class, () -> StandingSnapshot.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void rejectsMalformedFactionAndTier() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(1);
            buf.writeUtf("not a faction", 256);
            buf.writeUtf("Invalid", 256);
            buf.writeInt(0);
            buf.writeVarInt(0);
            assertThrows(IllegalArgumentException.class, () -> StandingSnapshot.STREAM_CODEC.decode(buf));
            buf.clear();
            buf.writeVarInt(1);
            buf.writeUtf("nerofactions:miner_union", 256);
            buf.writeUtf("Miner Union", 256);
            buf.writeInt(0);
            buf.writeVarInt(5);
            assertThrows(IllegalArgumentException.class, () -> StandingSnapshot.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }
}
