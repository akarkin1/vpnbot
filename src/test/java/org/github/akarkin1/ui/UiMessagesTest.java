package org.github.akarkin1.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the real message bundles in {@code src/main/resources}. They are read from the file
 * system because the test classpath has its own {@code messages*.properties}.
 */
class UiMessagesTest {

  private static final Path EN = Path.of("src/main/resources/messages.properties");
  private static final Path RU = Path.of("src/main/resources/messages_ru.properties");
  private static final Path UI_SOURCES = Path.of("src/main/java/org/github/akarkin1/ui");
  private static final Path MAIN_SOURCES = Path.of("src/main/java");
  private static final Pattern UI_KEY_REFERENCE = Pattern.compile("\\$\\{(ui\\.[^}]+)}");

  private static final List<String> SPEC_KEYS = List.of(
      "ui.home.greeting", "ui.home.no-access", "ui.home.your-nodes", "ui.home.all-nodes",
      "ui.home.no-nodes", "ui.home.start-node", "ui.home.no-regions",
      "ui.button.refresh", "ui.button.help", "ui.button.menu", "ui.button.try-again",
      "ui.button.exit-node-guide", "ui.button.get-tailscale",
      "ui.launch.starting", "ui.launch.step.submitting", "ui.launch.step.submitted",
      "ui.launch.step.waiting", "ui.launch.failed",
      "ui.launch.region-unavailable", "ui.launch.not-allowed",
      "ui.node.auto-stop", "ui.node.connect-hint", "ui.error.generic",
      "ui.help.title", "ui.help.body",
      "ui.node.idle-warning", "ui.node.stopped-idle", "ui.node.stopped", "ui.button.start-again");

  /** Keys added by Phase 2b (§4.9) with their English and Russian values. */
  private static final Map<String, List<String>> PHASE_2B_KEYS = Map.ofEntries(
      Map.entry("ui.button.stop", List.of("Stop", "Остановить")),
      Map.entry("ui.button.yes-stop", List.of("Yes, stop", "Да, остановить")),
      Map.entry("ui.button.cancel", List.of("Cancel", "Отмена")),
      Map.entry("ui.button.use", List.of("Use", "Использовать")),
      Map.entry("ui.button.start-another", List.of("Start another", "Запустить ещё один")),
      Map.entry("ui.reuse.existing", List.of("You already have a VPN server running in this region.",
                                             "У вас уже запущен VPN-сервер в этом регионе.")),
      Map.entry("ui.node.stopping", List.of("is stopping.", "останавливается.")),
      Map.entry("ui.node.already-stopped", List.of("This VPN server is not running anymore.",
                                                   "Этот VPN-сервер уже не запущен.")),
      Map.entry("ui.node.stopped-by-admin", List.of("was stopped by an administrator.",
                                                    "остановлен администратором.")),
      Map.entry("ui.stop.confirm", List.of("Stop", "Остановить")),
      Map.entry("ui.stop.not-allowed", List.of("You are not allowed to stop this VPN server.",
                                               "У вас нет прав на остановку этого VPN-сервера.")));

  /** Keys changed by stop-in-place (§4.1) with their English and Russian values. */
  private static final Map<String, List<String>> STOP_IN_PLACE_KEYS = Map.of(
      "ui.node.stopped-idle", List.of("Stopped: no devices were connected for 10 minutes.",
                                      "Остановлен: 10 минут без подключённых устройств."));

  /** Keys removed by Phase 2a (§4.2, §4.3): old /runNodeIn progress messages and what only they used. */
  private static final List<String> REMOVED_KEYS = List.of(
      "ui.launch.still-starting",
      "command.run-node.node.running.message",
      "command.run-node.task.started.message",
      "command.run-node.status.check-failed.error",
      "command.run-node.node.start-failed.error",
      "command.run-node.node.start-succeed.message",
      "common.node.details.message",
      "common.node.location.message");

  @Test
  @DisplayName("2a AC-12: every ui.* key from the specs exists in both message files")
  void specKeysExistInBothFiles() throws IOException {
    Properties en = load(EN);
    Properties ru = load(RU);

    for (String key : SPEC_KEYS) {
      assertTrue(en.containsKey(key), "missing in messages.properties: " + key);
      assertTrue(ru.containsKey(key), "missing in messages_ru.properties: " + key);
    }
  }

  @Test
  @DisplayName("2b AC-12: every new Phase 2b key exists in both message files with the values of §4.9")
  void phase2bKeysExistInBothFiles() throws IOException {
    Properties en = load(EN);
    Properties ru = load(RU);

    PHASE_2B_KEYS.forEach((key, values) -> {
      assertEquals(values.get(0), en.getProperty(key), "messages.properties " + key);
      assertEquals(values.get(1), ru.getProperty(key), "messages_ru.properties " + key);
    });
  }

  @Test
  @DisplayName("stop-in-place AC-4: ui.node.stopped-idle has the values of §4.1 in both message files")
  void stopInPlaceKeysHaveSpecValues() throws IOException {
    Properties en = load(EN);
    Properties ru = load(RU);

    STOP_IN_PLACE_KEYS.forEach((key, values) -> {
      assertEquals(values.get(0), en.getProperty(key), "messages.properties " + key);
      assertEquals(values.get(1), ru.getProperty(key), "messages_ru.properties " + key);
    });
  }

  @Test
  @DisplayName("AC-15: both message files have the same ui.* keys")
  void sameUiKeysInBothFiles() throws IOException {
    assertEquals(uiKeys(load(EN)), uiKeys(load(RU)));
  }

  @Test
  @DisplayName("AC-15, stop-in-place AC-4: message files contain only ASCII characters")
  void filesAreAscii() throws IOException {
    for (Path file : List.of(EN, RU)) {
      List<String> lines = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i);
        assertTrue(line.chars().allMatch(c -> c < 0x80),
                   file + ":" + (i + 1) + " has a non-ASCII character: " + line);
      }
    }
  }

  @Test
  @DisplayName("AC-15: ui.* values contain no %, <, > or &")
  void uiValuesHaveNoFormatSpecifiersOrHtml() throws IOException {
    for (Path file : List.of(EN, RU)) {
      Properties properties = load(file);
      for (String key : uiKeys(properties)) {
        String value = properties.getProperty(key);
        assertFalse(value.contains("%"), file + " " + key + " contains %: " + value);
        assertFalse(value.contains("<") || value.contains(">") || value.contains("&"),
                    file + " " + key + " contains <, > or &: " + value);
      }
    }
  }

  @Test
  @DisplayName("2a AC-12: every ${ui.…} key used in src/main/java exists in both message files")
  void usedKeysExistInBothFiles() throws IOException {
    Properties en = load(EN);
    Properties ru = load(RU);

    for (String key : usedUiKeys(MAIN_SOURCES)) {
      assertTrue(en.containsKey(key), "used but missing in messages.properties: " + key);
      assertTrue(ru.containsKey(key), "used but missing in messages_ru.properties: " + key);
    }
  }

  @Test
  @DisplayName("AC-15: the ui sources reference ui.* keys (guards the scan above against matching nothing)")
  void uiSourcesReferenceKeys() throws IOException {
    assertFalse(usedUiKeys(UI_SOURCES).isEmpty(), "no ${ui.…} keys found under " + UI_SOURCES);
  }

  @Test
  @DisplayName("2a AC-12: keys removed by Phase 2a are gone from both message files")
  void removedKeysAreGone() throws IOException {
    Properties en = load(EN);
    Properties ru = load(RU);

    for (String key : REMOVED_KEYS) {
      assertFalse(en.containsKey(key), "still in messages.properties: " + key);
      assertFalse(ru.containsKey(key), "still in messages_ru.properties: " + key);
    }
  }

  private static Properties load(Path file) throws IOException {
    Properties properties = new Properties();
    try (InputStream in = Files.newInputStream(file)) {
      properties.load(in);
    }
    return properties;
  }

  private static Set<String> uiKeys(Properties properties) {
    Set<String> keys = new TreeSet<>();
    for (String key : properties.stringPropertyNames()) {
      if (key.startsWith("ui.")) {
        keys.add(key);
      }
    }
    return keys;
  }

  private static Set<String> usedUiKeys(Path sources) throws IOException {
    Set<String> keys = new TreeSet<>();
    try (Stream<Path> files = Files.walk(sources)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        Matcher matcher = UI_KEY_REFERENCE.matcher(Files.readString(file));
        while (matcher.find()) {
          keys.add(matcher.group(1));
        }
      }
    }
    return keys;
  }

}
