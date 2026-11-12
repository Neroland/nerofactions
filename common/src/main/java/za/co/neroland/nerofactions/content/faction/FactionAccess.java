package za.co.neroland.nerofactions.content.faction;

import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.FactionTiers;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

public enum FactionAccess {
    OPEN,
    SURCHARGE,
    REFUSE_STANDING,
    REFUSE_RIVAL;

    public boolean refusesService() {
        return this == REFUSE_STANDING || this == REFUSE_RIVAL;
    }

    public static FactionAccess evaluate(MinecraftServer server, UUID player, FactionDefinition host) {
        Map<Identifier, FactionDefinition> factions = FactionDefinitions.factionsForServer(server);
        var memberships = FactionMembership.membershipOf(server, player);
        return evaluate(host, memberships, factions, faction -> ReputationApi.getReputation(player, faction),
                NeroFactionsConfig.HOSTILE_BELOW_STANDING.get());
    }

    static FactionAccess evaluate(FactionDefinition host, Iterable<Identifier> memberships,
            Map<Identifier, FactionDefinition> factions, ToIntFunction<Identifier> standing, int hostileBelow) {
        if (FactionTiers.isHostile(standing.applyAsInt(host.id()), hostileBelow)) {
            return REFUSE_STANDING;
        }
        FactionAccess access = OPEN;
        for (Identifier member : memberships) {
            FactionDefinition rival = factions.get(member);
            if (rival == null || !host.enemies().contains(member)) {
                continue;
            }
            FactionTier tier = FactionTiers.tierOf(rival, standing.applyAsInt(member));
            if (tier.ordinal() >= FactionTier.TRUSTED.ordinal()) {
                return REFUSE_RIVAL;
            }
            if (tier == FactionTier.MEMBER) {
                access = SURCHARGE;
            }
        }
        return access;
    }
}
