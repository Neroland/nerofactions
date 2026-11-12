package za.co.neroland.nerofactions.client;

import java.util.List;
import za.co.neroland.nerofactions.network.StandingSnapshot;

public final class FactionsClientState {
    private static volatile StandingSnapshot standing = new StandingSnapshot(List.of(), 0L, 0, 0, false);
    private static volatile za.co.neroland.nerofactions.network.ContractSnapshot contracts =
            new za.co.neroland.nerofactions.network.ContractSnapshot(List.of(), Long.MIN_VALUE, false, "");

    private FactionsClientState() {
    }

    public static StandingSnapshot standing() {
        return standing;
    }

    public static void accept(StandingSnapshot snapshot) {
        standing = snapshot;
    }

    public static za.co.neroland.nerofactions.network.ContractSnapshot contracts() { return contracts; }

    public static void acceptContracts(za.co.neroland.nerofactions.network.ContractSnapshot snapshot) { contracts = snapshot; }

    public static void clear() {
        standing = new StandingSnapshot(List.of(), 0L, 0, 0, false);
        contracts = new za.co.neroland.nerofactions.network.ContractSnapshot(List.of(), Long.MIN_VALUE, false, "");
    }
}
