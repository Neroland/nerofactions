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
        //? if <1.20.5 {
        /*legacyReachUntil = 0;
        *///?}
        standing = new StandingSnapshot(List.of(), 0L, 0, 0, false);
        contracts = new za.co.neroland.nerofactions.network.ContractSnapshot(List.of(), Long.MIN_VALUE, false, "");
    }

    //? if <1.20.5 {
    /*private static int legacyReachUntil;

    public static void acceptLegacyVoidReach(za.co.neroland.nerofactions.network.LegacyVoidReachPayload payload) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        legacyReachUntil = player == null ? 0 : player.tickCount + payload.ticks();
    }

    public static double legacyVoidReachBonus() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && !player.isCreative() && !player.isSpectator() && player.tickCount < legacyReachUntil
                ? za.co.neroland.nerofactions.content.faction.voidcult.LegacyVoidReach.BONUS : 0.0D;
    }
    *///?}
}
