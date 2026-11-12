package za.co.neroland.nerofactions.contracts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.ValidationIssue;

public final class ContractDefinitions {
    private static final String DIRECTORY = "nerofactions/contracts";
    private static MinecraftServer loadedFor;
    private static ResourceManager loadedFrom;
    private static Map<Identifier, ContractDefinition> definitions = Map.of();
    private static List<ValidationIssue> issues = List.of();

    private ContractDefinitions() {
    }

    public static synchronized Map<Identifier, ContractDefinition> forServer(MinecraftServer server) {
        if (loadedFor == server && loadedFrom == server.getResourceManager()) return definitions;
        var loaded = new LinkedHashMap<Identifier, ContractDefinition>();
        var problems = new ArrayList<ValidationIssue>();
        var factions = FactionDefinitions.factionsForServer(server);
        var resources = server.getResourceManager();
        for (var file : resources.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
            String path = file.getKey().getPath();
            Identifier id = Identifier.fromNamespaceAndPath(file.getKey().getNamespace(),
                    path.substring(DIRECTORY.length() + 1, path.length() - 5));
            if (loaded.size() >= 4096) {
                problems.add(ValidationIssue.dropped(id, "contract pool limit reached"));
                continue;
            }
            try (var reader = file.getValue().openAsReader()) {
                var parsed = ContractDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader));
                if (parsed.result().isEmpty()) {
                    problems.add(ValidationIssue.dropped(id, "invalid contract JSON or field bounds"));
                    continue;
                }
                var contract = parsed.result().orElseThrow().withId(id);
                String problem = contract.problem().orElse(null);
                if (problem == null && !factions.containsKey(contract.faction())) problem = "unknown faction";
                if (problem == null && contract.item().isPresent() && !BuiltInRegistries.ITEM.containsKey(contract.item().get())) {
                    problem = "unknown reward item";
                }
                if (problem == null && !contract.target().startsWith("#")) {
                    Identifier target = Identifier.parse(contract.target());
                    boolean known = contract.type().equals("kill") ? BuiltInRegistries.ENTITY_TYPE.containsKey(target)
                            : BuiltInRegistries.ITEM.containsKey(target);
                    if (!known) problem = "unknown objective target";
                }
                if (problem != null) problems.add(ValidationIssue.dropped(id, problem));
                else loaded.put(id, contract);
            } catch (Exception failure) {
                problems.add(ValidationIssue.dropped(id, "unreadable contract (" + failure.getClass().getSimpleName() + ")"));
            }
        }
        definitions = Map.copyOf(loaded);
        issues = List.copyOf(problems);
        loadedFor = server;
        loadedFrom = resources;
        if (!issues.isEmpty()) NeroFactionsCommon.LOGGER.warn("[NeroFactions] Rejected {} contract definitions.", issues.size());
        return definitions;
    }

    public static synchronized List<ValidationIssue> issuesForServer(MinecraftServer server) {
        forServer(server);
        return issues;
    }

    public static synchronized void forgetServer() {
        loadedFor = null;
        loadedFrom = null;
        definitions = Map.of();
        issues = List.of();
    }
}
