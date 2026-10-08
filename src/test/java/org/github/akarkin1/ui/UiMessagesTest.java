package org.github.akarkin1.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
  private static final Pattern UI_KEY_REFERENCE = Pattern.compile("\\$\\{(ui\\.[^}]+)}");

  private static final List<String> SPEC_KEYS = List.of(
      "ui.home.greeting", "ui.home.no-access", "ui.home.your-nodes", "ui.home.all-nodes",
      "ui.home.no-nodes", "ui.home.start-node", "ui.home.no-regions",
      "ui.button.refresh", "ui.button.help", "ui.button.menu", "ui.button.try-again",
      "ui.button.exit-node-guide", "ui.button.get-tailscale",
      "ui.launch.starting", "ui.launch.step.submitting", "ui.launch.step.submitted",
      "ui.launch.step.waiting", "ui.launch.still-starting", "ui.launch.failed",
      "ui.launch.region-unavailable", "ui.launch.not-allowed",
      "ui.node.auto-stop", "ui.node.connect-hint", "ui.error.generic",
      "ui.help.title", "ui.help.body");

  @Test
  @DisplayName("AC-15: every ui.* key from the spec exists in both message files")
  void specKeysExistInBothFiles() throws IOException {
    Properties en = load(EN);
    Properties ru = load(RU);

    for (String key : SPEC_KEYS) {
      assertTrue(en.containsKey(key), "missing in messages.properties: " + key);
      assertTrue(ru.containsKey(key), "missing in messages_ru.properties: " + key);
    }
  }

  @Test
  @DisplayName("AC-15: both message files have the same ui.* keys")
  void sameUiKeysInBothFiles() throws IOException {
    assertEquals(uiKeys(load(EN)), uiKeys(load(RU)));
  }

  @Test
  @DisplayName("AC-15: message files contain only ASCII characters")
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
  @DisplayName("AC-15: every ${ui.…} key used in the ui sources exists in both message files")
  void usedKeysExistInBothFiles() throws IOException {
    Properties en = load(EN);
    Properties ru = load(RU);

    for (String key : usedUiKeys()) {
      assertTrue(en.containsKey(key), "used but missing in messages.properties: " + key);
      assertTrue(ru.containsKey(key), "used but missing in messages_ru.properties: " + key);
    }
  }

  @Test
  @DisplayName("AC-15: the ui sources reference ui.* keys (guards the scan above against matching nothing)")
  void uiSourcesReferenceKeys() throws IOException {
    assertFalse(usedUiKeys().isEmpty(), "no ${ui.…} keys found under " + UI_SOURCES);
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

  private static Set<String> usedUiKeys() throws IOException {
    Set<String> keys = new TreeSet<>();
    try (Stream<Path> files = Files.walk(UI_SOURCES)) {
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
