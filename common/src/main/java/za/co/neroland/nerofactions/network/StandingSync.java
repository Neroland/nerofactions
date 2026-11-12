package za.co.neroland.nerofactions.network;

import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.FactionTiers;
import za.co.neroland.nerofactions.data.FactionMembershipState;
import za.co.neroland.nerofactions.data.FactionReputationState;
import za.co.neroland.nerofactions.platform.Services;

public final class StandingSync {
    private StandingSync() {
    }

    public static void send(MinecraftServer server, UUID player) {
        if (server == null || player == null || !server.isSameThread()) {
            return;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            send(online);
        }
    }

    public static void send(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null || !server.isSameThread()) {
            return;
        }
        var membership = FactionMembershipState.get(server);
        var members = membership.membershipsOf(player.getUUID());
        var state = FactionReputationState.get(server);
        var rows = new ArrayList<StandingSnapshot.Row>();
        for (var faction : FactionDefinitions.factionsForServer(server).values().stream().sorted(java.util.Comparator.comparing(f -> f.id())).toList()) {
            if (rows.size() == StandingSnapshot.MAX_FACTIONS) {
                break;
            }
            int value = state.getReputation(player.getUUID(), faction.id());
            FactionTier tier = FactionTiers.tierOf(faction, value);
            int next = tier == FactionTier.INNER_CIRCLE ? faction.threshold(tier)
                    : faction.threshold(FactionTier.values()[tier.ordinal() + 1]);
            String name = faction.displayName();
            rows.add(new StandingSnapshot.Row(faction.id(), name.substring(0, Math.min(name.length(), 256)),
                    value, tier.ordinal(), next, members.contains(faction.id()),
                    FactionTiers.isHostile(value, NeroFactionsConfig.HOSTILE_BELOW_STANDING.get()),
                    faction.threshold(tier), bounded(faction.theme()), bounded(faction.motto()), faction.color(),
                    bounded(faction.icon().toString()), bounded(faction.descriptionKey()),
                    bounded(faction.enemies().stream().map(id -> FactionDefinitions.factionsForServer(server).get(id))
                            .filter(java.util.Objects::nonNull).map(f -> f.displayName())
                            .collect(java.util.stream.Collectors.joining(", "))),
                    bounded(tier == FactionTier.INNER_CIRCLE ? "" : faction.rewardsFor(FactionTier.values()[tier.ordinal() + 1])
                            .stream().map(reward -> reward.item().map(id -> id.getPath().replace('_', ' ') + " ×" + reward.count())
                                    .orElseGet(() -> reward.cosmetic().orElse("").replace('_', ' ')))
                            .collect(java.util.stream.Collectors.joining(", ")))));
        }
        Services.NETWORK.sendToPlayer(player, new StandingSnapshot(rows, membership.cooldownUntil(player.getUUID()),
                NeroFactionsConfig.JOIN_COOLDOWN_MINUTES.get(), NeroFactionsConfig.SWITCH_PENALTY_POINTS.get(),
                NeroFactionsConfig.ALLOW_MULTIPLE_FACTIONS.get()));
    }

    private static String bounded(String text) {
        return text.substring(0, Math.min(256, text.length()));
    }
}
