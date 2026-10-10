package org.github.akarkin1.translation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translator over the real message bundles in {@code src/main/resources} (the test classpath has
 * its own {@code messages*.properties}). Russian for language codes starting with {@code ru},
 * English otherwise; replaces {@code ${key}} and then formats the params, like
 * {@link ResourceBasedTranslator}.
 */
public class MainMessagesTranslator implements Translator {

  public static final Path EN = Path.of("src/main/resources/messages.properties");
  public static final Path RU = Path.of("src/main/resources/messages_ru.properties");
  private static final Pattern KEY = Pattern.compile("\\$\\{([^}]+)}");

  private final Properties en = load(EN);
  private final Properties ru = load(RU);

  @Override
  public String translate(String langCode, String message, Object... params) {
    Matcher matcher = KEY.matcher(message);
    StringBuilder translated = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(translated, Matcher.quoteReplacement(value(langCode, matcher.group(1))));
    }
    matcher.appendTail(translated);
    return translated.toString().formatted(params);
  }

  /** The value of {@code key} in the language of {@code langCode}; fails for a missing key. */
  public String value(String langCode, String key) {
    Properties messages = langCode != null && langCode.startsWith("ru") ? ru : en;
    String value = messages.getProperty(key);
    if (value == null) {
      throw new IllegalArgumentException("Missing message key " + key + " for " + langCode);
    }
    return value;
  }

  public static Properties load(Path file) {
    Properties properties = new Properties();
    try (InputStream in = Files.newInputStream(file)) {
      properties.load(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return properties;
  }

}
