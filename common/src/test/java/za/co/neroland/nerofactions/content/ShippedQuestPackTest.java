package za.co.neroland.nerofactions.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * The shipped quest pack ({@code data/nerofactions/neroquests/}) is inert data for an optional quest
 * mod, so nothing at build time would otherwise catch a broken file. This checks the pack against
 * the published pack contract on a bare JVM: every quest parses, uses translation keys that exist in
 * {@code en_us.json}, names only this pack's quests as prerequisites, points its {@code gate_open} /
 * {@code visible_gate} / {@code mod_hook} references at gate files this jar ships (hooks under
 * {@code hook/}), keeps its {@code reputation_tier} crossings on server scope, and is listed exactly
 * once in the chapter.
 */
class ShippedQuestPackTest {

    private static final String NAMESPACE = "nerofactions";
    private static final String CHAPTER = "standing";

    @Test
    void shippedStandingChapterFollowsThePackContract() throws Exception {
        Path questsDir = shippedDir(Path.of("data", NAMESPACE, "neroquests", "quests", CHAPTER));
        Path chapterFile = shippedDir(Path.of("data", NAMESPACE, "neroquests", "chapters"))
                .resolve(CHAPTER + ".json");
        Path gatesDir = shippedDir(Path.of("data", NAMESPACE, "neroland_gates"));
        JsonObject lang = parse(shippedDir(Path.of("assets", NAMESPACE, "lang")).resolve("en_us.json"));

        Map<String, JsonObject> quests = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(questsDir)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".json")) {
                    quests.put(NAMESPACE + ":" + CHAPTER + "/" + name.substring(0, name.length() - 5),
                            parse(file));
                }
            }
        }
        assertTrue(quests.size() >= 19, "the standing chapter ships at least 19 quests");

        List<String> complaints = new ArrayList<>();
        JsonObject chapter = parse(chapterFile);
        assertTrue(lang.has(chapter.get("title").getAsString()), "chapter title key is translated");
        boolean serverAllowed = chapter.has("allow_server_scope")
                && chapter.get("allow_server_scope").getAsBoolean();
        Set<String> listed = new TreeSet<>();
        for (JsonElement entry : chapter.getAsJsonArray("quests")) {
            String id = entry.getAsJsonObject().get("quest").getAsString();
            if (!listed.add(id)) {
                complaints.add("chapter lists " + id + " twice");
            }
            if (!quests.containsKey(id)) {
                complaints.add("chapter lists unknown quest " + id);
            }
        }
        assertEquals(new TreeSet<>(quests.keySet()), listed, "every quest is placed in the chapter");

        for (Map.Entry<String, JsonObject> entry : quests.entrySet()) {
            String id = entry.getKey();
            JsonObject quest = entry.getValue();
            String expectedKeyBase = "quest." + NAMESPACE + "." + CHAPTER + "."
                    + id.substring(id.lastIndexOf('/') + 1);
            requireLang(lang, quest, "title", expectedKeyBase + ".title", id, complaints);
            requireLang(lang, quest, "description", expectedKeyBase + ".desc", id, complaints);

            boolean server = quest.has("scope") && "server".equals(quest.get("scope").getAsString());
            if (server && !serverAllowed) {
                complaints.add(id + " is server-scoped but the chapter does not allow it");
            }
            for (JsonElement prerequisite : arrayOf(quest, "prerequisites")) {
                String other = prerequisite.getAsString();
                if (!quests.containsKey(other)) {
                    complaints.add(id + " requires unknown or foreign quest " + other);
                }
            }
            if (quest.has("visible_gate")) {
                requireGateFile(gatesDir, quest.get("visible_gate").getAsString(), id, complaints);
            }
            JsonArray objectives = arrayOf(quest, "objectives");
            if (objectives.isEmpty()) {
                complaints.add(id + " has no objectives");
            }
            for (JsonElement element : objectives) {
                JsonObject objective = element.getAsJsonObject();
                String type = objective.get("type").getAsString();
                switch (type) {
                    case "neroquests:gate_open" ->
                            requireGateFile(gatesDir, objective.get("gate").getAsString(), id, complaints);
                    case "neroquests:custom_event" -> {
                        String channel = objective.get("channel").getAsString();
                        if (!channel.startsWith(NAMESPACE + ":")) {
                            complaints.add(id + " listens on a foreign channel " + channel);
                        }
                        boolean world = !objective.has("audience")
                                || "world".equals(objective.get("audience").getAsString());
                        if (world && !server) {
                            complaints.add(id + " is a world-audience crossing in a player quest");
                        }
                    }
                    case "neroquests:craft_item", "neroquests:collect_item", "neroquests:kill_entity",
                            "neroquests:reach_dimension", "neroquests:quest_complete" -> {
                        // Shape-checked by NeroQuests at load; nothing to cross-reference here.
                    }
                    default -> complaints.add(id + " uses an objective type this pack never meant: " + type);
                }
            }
            for (JsonElement element : arrayOf(quest, "rewards")) {
                JsonObject reward = element.getAsJsonObject();
                String type = reward.get("type").getAsString();
                switch (type) {
                    case "neroquests:gate", "neroquests:reputation" ->
                            complaints.add(id + " uses the spine-only reward " + type);
                    case "neroquests:mod_hook" -> {
                        String gate = reward.get("gate").getAsString();
                        if (!gate.startsWith(NAMESPACE + ":hook/")) {
                            complaints.add(id + " names a mod_hook outside " + NAMESPACE + ":hook/: " + gate);
                        }
                        requireGateFile(gatesDir, gate, id, complaints);
                    }
                    case "neroquests:xp", "neroquests:currency", "neroquests:item", "neroquests:loot_table" -> {
                        // Plain rewards.
                    }
                    default -> complaints.add(id + " uses an unknown reward type " + type);
                }
            }
        }
        assertEquals(List.of(), complaints, "the shipped quest pack breaks the pack contract");
    }

    private static void requireLang(JsonObject lang, JsonObject quest, String field, String expectedKey,
            String id, List<String> complaints) {
        if (!quest.has(field)) {
            complaints.add(id + " has no " + field);
            return;
        }
        String key = quest.get(field).getAsString();
        if (!expectedKey.equals(key)) {
            complaints.add(id + " " + field + " key should be " + expectedKey + " but is " + key);
        }
        if (!lang.has(key) || lang.get(key).getAsString().isBlank()) {
            complaints.add(id + " " + field + " key " + key + " is missing from en_us.json");
        }
    }

    private static void requireGateFile(Path gatesDir, String gate, String id, List<String> complaints) {
        String prefix = NAMESPACE + ":";
        if (!gate.startsWith(prefix)) {
            complaints.add(id + " references a gate outside this jar: " + gate);
            return;
        }
        Path file = gatesDir.resolve(gate.substring(prefix.length()) + ".json");
        if (!Files.isRegularFile(file)) {
            complaints.add(id + " references gate " + gate + " but no gate file ships for it");
        }
    }

    private static JsonArray arrayOf(JsonObject object, String field) {
        return object.has(field) ? object.getAsJsonArray(field) : new JsonArray();
    }

    private static JsonObject parse(Path file) throws Exception {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
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
