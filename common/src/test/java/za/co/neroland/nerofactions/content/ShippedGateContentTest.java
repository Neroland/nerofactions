package za.co.neroland.nerofactions.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.progression.Gate;
import za.co.neroland.nerolandcore.progression.GateScope;

/**
 * The seven shipped inner-circle gate definitions must decode with Core's real
 * {@link Gate#DATA_CODEC} — the exact codec {@code GateDefinitions} runs at load — and carry the
 * designed composition: player scope, and the Core arc prerequisite matching the faction's era
 * (early factions require {@code industrial_power}, space-era {@code reached_orbit}, late/alien
 * {@code first_colony}). File-walk pattern follows {@link ShippedFactionContentTest}.
 */
class ShippedGateContentTest {

    /** faction path -> the required Core arc gate. The designed era mapping, verbatim. */
    private static final Map<String, String> REQUIRED_CORE_GATE = Map.of(
            "miner_union", "nerolandcore:industrial_power",
            "free_colonists", "nerolandcore:industrial_power",
            "space_guild", "nerolandcore:reached_orbit",
            "nero_corporation", "nerolandcore:reached_orbit",
            "salvagers", "nerolandcore:reached_orbit",
            "void_cult", "nerolandcore:first_colony",
            "terraforming_authority", "nerolandcore:first_colony");

    private static final String SUFFIX = "_inner_circle";

    @Test
    void allSevenShippedGatesParseWithCoresCodecAndComposeByEra() throws Exception {
        Path gatesDir = shippedDir(Path.of("data", "nerofactions", "neroland_gates"));
        Path factionsDir = shippedDir(Path.of("data", "nerofactions", "nerofactions", "factions"));

        Map<String, Gate.Data> parsed = new LinkedHashMap<>();
        List<String> complaints = new ArrayList<>();
        try (Stream<Path> files = Files.list(gatesDir)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".json")) {
                    continue;
                }
                String gatePath = name.substring(0, name.length() - ".json".length());
                try (BufferedReader reader = Files.newBufferedReader(file)) {
                    JsonElement json = JsonParser.parseReader(reader);
                    Optional<Gate.Data> data = Gate.DATA_CODEC.parse(JsonOps.INSTANCE, json)
                            .resultOrPartial(error -> complaints.add(gatePath + ": " + error));
                    if (data.isEmpty()) {
                        fail("Shipped gate " + gatePath + " did not decode at all");
                    }
                    parsed.put(gatePath, data.get());
                }
            }
        }
        assertEquals(List.of(), complaints,
                "shipped gate JSONs must decode against Core's Gate.DATA_CODEC without complaint");
        assertEquals(REQUIRED_CORE_GATE.size(), parsed.size(),
                "exactly one inner-circle gate per shipped faction");

        for (Map.Entry<String, Gate.Data> entry : parsed.entrySet()) {
            String gatePath = entry.getKey();
            Gate.Data gate = entry.getValue();
            assertTrue(gatePath.endsWith(SUFFIX), gatePath + " must follow <faction>" + SUFFIX);
            String factionPath = gatePath.substring(0, gatePath.length() - SUFFIX.length());

            assertTrue(Files.isRegularFile(factionsDir.resolve(factionPath + ".json")),
                    gatePath + " must gate a shipped faction (" + factionPath + ")");
            assertEquals(GateScope.PLAYER, gate.scope(),
                    gatePath + " must be player-scoped: standing is personal, never server-wide");

            String requiredArcGate = REQUIRED_CORE_GATE.get(factionPath);
            assertTrue(requiredArcGate != null, "unexpected shipped gate " + gatePath);
            assertEquals(List.of(requiredArcGate),
                    gate.requires().stream().map(Object::toString).toList(),
                    gatePath + " must require exactly its era's Core arc gate");

            assertFalse(gate.title().isBlank(), gatePath + " must carry a display title");
            assertTrue(gate.title().endsWith("Inner Circle"), gatePath + " title names the tier");
        }
    }

    /** Walks up from the test JVM's working dir to the shipped resource tree (per-node cwds). */
    private static Path shippedDir(Path underResources) {
        Path relative = Path.of("common", "src", "main", "resources").resolve(underResources);
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return fail("could not locate " + underResources + " from " + System.getProperty("user.dir"));
    }
}
