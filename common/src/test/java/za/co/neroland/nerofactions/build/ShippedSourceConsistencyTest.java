package za.co.neroland.nerofactions.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Guards three per-line build facts that the compiler cannot see: the raw shared source must sit in the
 * Stonecutter state of the active node (that node compiles it unprocessed), 1.21.1 needs a classic item
 * model for every item, and 26.3 ignores the old {@code conditions} array in block loot tables.
 */
class ShippedSourceConsistencyTest {

    private static final Pattern IF = Pattern.compile("//\\?\\s*(?:}\\s*else\\s+)?if\\s+(<=|>=|<|>|=)?\\s*([0-9.]+)\\s*\\{\\s*$");
    private static final Pattern ELSE = Pattern.compile("//\\?\\s*}\\s*else\\s*\\{\\s*$");
    private static final Pattern END = Pattern.compile("//\\?\\s*}\\s*$");

    @Test
    void rawSharedSourceMatchesTheActiveStonecutterNode() throws IOException {
        Path root = repoRoot();
        String active = activeVersion(root);
        List<String> problems = new ArrayList<>();
        for (String dir : List.of("common/src/main/java", "fabric/src/main/java", "forge/src/main/java",
                "neoforge/src/main/java")) {
            Path base = root.resolve(dir);
            if (!Files.isDirectory(base)) continue;
            try (Stream<Path> files = Files.walk(base)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    for (String problem : stateProblems(Files.readAllLines(file), active)) {
                        problems.add(root.relativize(file) + ":" + problem);
                    }
                }
            }
        }
        assertEquals(List.of(), problems, "raw source must be in the " + active + " state");
    }

    @Test
    void stateCheckFlagsACommentedActiveBranchAndALiveInactiveOne() {
        List<String> wrong = List.of(
                "//? if <26.3 {",
                "/*@Override",
                "int shape() { return 1; }",
                "*///?} else {",
                "int shape() { return 2; }",
                "//?}");
        assertEquals(2, stateProblems(wrong, "26.2").size());
        List<String> right = List.of(
                "//? if <26.3 {",
                "@Override",
                "int shape() { return 1; }",
                "//?} else {",
                "/*int shape() { return 2; }",
                "*///?}");
        assertEquals(List.of(), stateProblems(right, "26.2"));
        assertEquals(2, stateProblems(right, "26.3").size(), "the same text is wrong for a 26.3 node");
    }

    @Test
    void everyItemHasAClassicModelForTheOldestLine() throws IOException {
        Path assets = repoRoot().resolve("common/src/main/resources/assets/nerofactions");
        assertEquals(List.of(), missingClassicModels(assets));
    }

    @Test
    void classicModelCheckFlagsAnItemWithOnlyAnItemDefinition() throws IOException {
        Path assets = Files.createTempDirectory("nf-assets");
        Files.createDirectories(assets.resolve("items"));
        Files.createDirectories(assets.resolve("models/item"));
        Files.writeString(assets.resolve("items/kept.json"), "{}");
        Files.writeString(assets.resolve("models/item/kept.json"), "{}");
        Files.writeString(assets.resolve("items/lost.json"), "{}");
        assertEquals(List.of("lost"), missingClassicModels(assets));
    }

    @Test
    void every26_3BlockLootTableUsesTheSingleConditionShape() throws IOException {
        Path main = repoRoot().resolve("common/src/main");
        Path base = main.resolve("resources/data/nerofactions/loot_table/blocks");
        Path overlay = main.resolve("resources-26.3/data/nerofactions/loot_table/blocks");
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.list(base)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                Path effective = overlay.resolve(file.getFileName().toString());
                if (!Files.isRegularFile(effective)) effective = file;
                if (usesLegacyConditions(JsonParser.parseString(Files.readString(effective)))) {
                    problems.add(file.getFileName().toString());
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void legacyConditionCheckFlagsTheArrayForm() {
        assertTrue(usesLegacyConditions(JsonParser.parseString(
                "{\"pools\":[{\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}")));
        assertEquals(false, usesLegacyConditions(JsonParser.parseString(
                "{\"pools\":[{\"condition\":{\"type\":\"minecraft:survives_explosion\"}}]}")));
    }

    static List<String> stateProblems(List<String> lines, String active) {
        List<String> problems = new ArrayList<>();
        boolean inChain = false;
        boolean taken = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Boolean branch = null;
            Matcher m = IF.matcher(line);
            if (m.find()) {
                if (!line.contains("else")) taken = false;
                boolean holds = !taken && holds(m.group(1), m.group(2), active);
                taken |= holds;
                inChain = true;
                branch = holds;
            } else if (ELSE.matcher(line).find()) {
                branch = !taken;
                taken = true;
            } else if (END.matcher(line).find()) {
                inChain = false;
                continue;
            }
            if (branch == null || !inChain || i + 1 >= lines.size()) continue;
            boolean commented = lines.get(i + 1).trim().startsWith("/*");
            if (branch == commented) {
                problems.add((i + 1) + (branch ? " active branch is commented out" : " inactive branch is live"));
            }
        }
        return problems;
    }

    static boolean holds(String op, String version, String active) {
        int c = compare(active, version);
        return switch (op == null ? "=" : op) {
            case "<" -> c < 0;
            case "<=" -> c <= 0;
            case ">" -> c > 0;
            case ">=" -> c >= 0;
            default -> c == 0;
        };
    }

    static int compare(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? Integer.parseInt(x[i]) : 0;
            int q = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    static List<String> missingClassicModels(Path assets) throws IOException {
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.list(assets.resolve("items"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String id = file.getFileName().toString();
                if (!Files.isRegularFile(assets.resolve("models/item").resolve(id))) {
                    missing.add(id.substring(0, id.length() - ".json".length()));
                }
            }
        }
        return missing;
    }

    static boolean usesLegacyConditions(JsonElement element) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) if (usesLegacyConditions(child)) return true;
            return false;
        }
        if (!element.isJsonObject()) return false;
        JsonObject object = element.getAsJsonObject();
        if (object.has("conditions") && object.get("conditions").isJsonArray()) return true;
        for (var entry : object.entrySet()) if (usesLegacyConditions(entry.getValue())) return true;
        return false;
    }

    private static String activeVersion(Path root) throws IOException {
        Matcher m = Pattern.compile("stonecutter\\.active\\s+'([0-9.]+)'")
                .matcher(Files.readString(root.resolve("stonecutter.gradle")));
        assertTrue(m.find(), "stonecutter.active not found");
        return m.group(1);
    }

    private static Path repoRoot() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("stonecutter.gradle"))) {
            root = root.getParent();
        }
        assertNotNull(root);
        return root;
    }
}
