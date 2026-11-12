package za.co.neroland.nerofactions.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import za.co.neroland.nerofactions.contracts.ContractFixtures;
import za.co.neroland.nerofactions.data.ContractState;

class ContractPayloadTest {
    @Test
    void intentRejectsForgedMenusDaysContractsAndBounds() {
        var contract = ContractFixtures.pool().get(0);
        var entry = new ContractState.Entry(0, contract, 16, false);
        var request = new ContractIntent(4, contract.faction(), contract.id(), 5, 0, 1);
        assertTrue(request.matches(4, 5, entry));
        assertFalse(request.matches(3, 5, entry));
        assertFalse(request.matches(4, 6, entry));
        assertFalse(request.matches(4, 5, new ContractState.Entry(0, ContractFixtures.pool().get(1), 16, false)));
        assertFalse(request.matches(4, 5, null));
        assertFalse(new ContractIntent(4, null, contract.id(), 5, 0, 1).valid());
        assertFalse(new ContractIntent(4, contract.faction(), contract.id(), 5, -1, 1).valid());
        assertFalse(new ContractIntent(4, contract.faction(), contract.id(), 5, 3, 1).valid());
        assertFalse(new ContractIntent(4, contract.faction(), contract.id(), 5, 0, 3).valid());
    }

    @Test
    void snapshotRoundTripsAndOversizedListsAreRejected() {
        var row = new ContractSnapshot.Row(ContractFixtures.FACTION, Identifier.parse("nerofactions:miner_union/test_0"),
                0, "deliver", "minecraft:iron_ingot", 4, 16, 25, 10L, false);
        var snapshot = new ContractSnapshot(List.of(row), 5, true, "gui.nerofactions.contract.delivered");
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ContractSnapshot.STREAM_CODEC.encode(buf, snapshot);
            assertEquals(snapshot, ContractSnapshot.STREAM_CODEC.decode(buf));
            buf.clear();
            buf.writeVarInt(ContractSnapshot.MAX_ENTRIES + 1);
            assertThrows(IllegalArgumentException.class, () -> ContractSnapshot.STREAM_CODEC.decode(buf));
            buf.clear();
            buf.writeVarInt(-1);
            assertThrows(IllegalArgumentException.class, () -> ContractSnapshot.STREAM_CODEC.decode(buf));
            za.co.neroland.nerofactions.client.FactionsClientState.acceptContracts(snapshot);
            za.co.neroland.nerofactions.client.FactionsClientState.clear();
            assertTrue(za.co.neroland.nerofactions.client.FactionsClientState.contracts().entries().isEmpty());
        } finally {
            buf.release();
        }
    }

}
