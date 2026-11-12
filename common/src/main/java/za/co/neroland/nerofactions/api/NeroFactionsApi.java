package za.co.neroland.nerofactions.api;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.FactionTiers;
import za.co.neroland.nerofactions.data.FactionReputationState;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerofactions.reputation.ReputationSources;

public final class NeroFactionsApi {
    private NeroFactionsApi() {
    }

    public static Map<Identifier, FactionView> factions(MinecraftServer server) {
        return factionViews(access(server));
    }

    static Map<Identifier, FactionView> factionViews(Access access) {
        if (access == null) return Map.of();
        Map<Identifier, FactionView> result = new LinkedHashMap<>();
        access.factions().forEach((id, faction) -> result.put(id, view(faction)));
        return Map.copyOf(result);
    }

    static FactionView view(FactionDefinition faction) {
        return new FactionView(faction.id(), faction.displayName(), faction.theme(),
                Arrays.stream(FactionTier.values()).map(tier -> new FactionTierView(tier.jsonName(),
                        faction.threshold(tier) == null ? 0 : faction.threshold(tier))).toList(),
                faction.enemies());
    }

    public static Set<Identifier> membershipOf(MinecraftServer server, UUID player) {
        return membershipView(player == null ? null : access(server), player);
    }

    static Set<Identifier> membershipView(Access access, UUID player) {
        return access == null || player == null ? Set.of() : Set.copyOf(access.membership(player));
    }

    public static int standing(MinecraftServer server, UUID player, Identifier factionId) {
        return standingValue(player == null || factionId == null ? null : access(server), player, factionId);
    }

    static int standingValue(Access access, UUID player, Identifier factionId) {
        if (access == null || player == null || factionId == null || !access.factions().containsKey(factionId)) return 0;
        return access.standing(player, factionId);
    }

    public static Optional<FactionTierView> tierOf(MinecraftServer server, UUID player, Identifier factionId) {
        return tierView(player == null || factionId == null ? null : access(server), player, factionId);
    }

    static Optional<FactionTierView> tierView(Access access, UUID player, Identifier factionId) {
        if (access == null || player == null || factionId == null) return Optional.empty();
        FactionDefinition faction = access.factions().get(factionId);
        if (faction == null) return Optional.empty();
        FactionTier tier = FactionTiers.tierOf(faction, standingValue(access, player, factionId));
        return Optional.of(new FactionTierView(tier.jsonName(), faction.threshold(tier)));
    }

    public static boolean isEnemy(MinecraftServer server, Identifier factionA, Identifier factionB) {
        return enemies(factionA == null || factionB == null ? null : access(server), factionA, factionB);
    }

    static boolean enemies(Access access, Identifier factionA, Identifier factionB) {
        if (access == null || factionA == null || factionB == null || !access.factions().containsKey(factionB)) return false;
        FactionDefinition faction = access.factions().get(factionA);
        return faction != null && faction.enemies().contains(factionB);
    }

    public static int awardEvent(MinecraftServer server, UUID player, Identifier factionId,
            int amount, Identifier reasonId) {
        if (server == null || player == null || factionId == null || reasonId == null
                || amount <= 0 || amount > 1_000_000 || !server.isSameThread()
                || NeroFactionsCommon.REPUTATION_PROVIDER.boundServer() != server) return 0;
        return award(access(server), player, factionId, amount, reasonId);
    }

    static int award(Access access, UUID player, Identifier factionId, int amount, Identifier reasonId) {
        if (access == null || player == null || factionId == null || reasonId == null || amount <= 0
                || amount > 1_000_000 || !access.factions().containsKey(factionId)) return 0;
        return access.awardEvent(player, factionId, amount);
    }

    interface Access {
        Map<Identifier, FactionDefinition> factions();
        Set<Identifier> membership(UUID player);
        int standing(UUID player, Identifier faction);
        int awardEvent(UUID player, Identifier faction, int amount);
    }

    private static Access access(MinecraftServer server) {
        if (server == null) return null;
        var definitions = FactionDefinitions.factionsForServer(server);
        return new Access() {
            public Map<Identifier, FactionDefinition> factions() { return definitions; }
            public Set<Identifier> membership(UUID player) { return FactionMembership.membershipOf(server, player); }
            public int standing(UUID player, Identifier faction) {
                return FactionReputationState.get(server).getReputation(player, faction);
            }
            public int awardEvent(UUID player, Identifier faction, int amount) {
                return ReputationSources.award(server, player, faction, ReputationSources.Source.EVENT, amount);
            }
        };
    }
}
