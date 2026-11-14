package za.co.neroland.nerofactions.link;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.data.ContractState;
import za.co.neroland.nerofactions.data.FactionMembershipState;
import za.co.neroland.nerofactions.data.FactionReputationState;
import za.co.neroland.nerofactions.data.OutpostVisits;
import za.co.neroland.nerofactions.link.FactionsLinkSnapshots.GearSet;

/**
 * Direct stores behind {@link FactionsLinkSnapshots.View} — the same reads production makes, no
 * server required. Shared by the link tests.
 */
final class LinkFixture implements FactionsLinkSnapshots.View {

    /**
     * Two sets mirroring {@code FactionArmor} ids. The real enum is not loaded here: its armour
     * materials need a bootstrapped registry, which plain-JVM tests do not have.
     */
    static final List<GearSet> GEAR = List.of(
            new GearSet(Identifier.parse("nerofactions:miner_union"), "union_exosuit"),
            new GearSet(Identifier.parse("nerofactions:space_guild"), "guild_flight_suit"));

    final Map<Identifier, FactionDefinition> factions = new LinkedHashMap<>();
    final FactionReputationState reputation = new FactionReputationState();
    final FactionMembershipState membership = new FactionMembershipState();
    final ContractState contractState = new ContractState();
    final OutpostVisits visits = new OutpostVisits();
    final Set<UUID> online = new HashSet<>();

    LinkFixture(FactionDefinition... definitions) {
        for (FactionDefinition definition : definitions) {
            factions.put(definition.id(), definition);
        }
    }

    static FactionDefinition faction(String path, String displayName) {
        return new FactionDefinition(
                Identifier.fromNamespaceAndPath("nerofactions", path),
                displayName, "A test faction.",
                Map.of("outsider", 0, "associate", 100, "member", 400,
                        "trusted", 1000, "inner_circle", 2500),
                Map.of(), List.of(), List.of(), Optional.empty());
    }

    FactionsLinkSnapshots snapshots() {
        return new FactionsLinkSnapshots(() -> this);
    }

    @Override public Map<Identifier, FactionDefinition> factions() { return factions; }
    @Override public int standing(UUID player, Identifier faction) { return reputation.getReputation(player, faction); }
    @Override public Set<Identifier> memberships(UUID player) { return membership.membershipsOf(player); }
    @Override public long joinedAt(UUID player, Identifier faction) { return membership.joinedAt(player, faction); }
    @Override public long cooldownUntil(UUID player) { return membership.cooldownUntil(player); }
    @Override public Map<Identifier, Long> leftFactions(UUID player) { return membership.leftFactions(player); }
    @Override public boolean online(UUID player) { return online.contains(player); }
    @Override public long worldDay() { return 5; }
    @Override public long contractDay(UUID player) { return contractState.day(player); }
    @Override public boolean rerollAvailable(UUID player) { return contractState.rerollAvailable(player); }
    @Override public List<ContractState.Entry> contracts(UUID player) { return contractState.entries(player); }
    @Override public JsonArray outpostsVisited(UUID player) { return visits.export(player); }
    @Override public List<GearSet> gearSets() { return GEAR; }
}
