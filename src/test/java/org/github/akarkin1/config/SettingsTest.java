package org.github.akarkin1.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The defaults, read through {@link Settings#from} with an environment that lacks the variable. */
class SettingsTest {

  /** The one required variable; everything else is left to its default. */
  private static final Map<String, String> MINIMAL_ENV = Map.of("BOT_SECRET_TOKEN_ID", "secret-id");

  @Test
  @DisplayName("3 AC-A8: without CONFIG_TABLE_NAME the table name is vpnbot")
  void configTableNameDefault() {
    assertEquals("vpnbot", settings().configTableName());
  }

  @Test
  @DisplayName("3 AC-A12: without CONFIG_CACHE_ENABLED the config cache is off")
  void configCacheDisabledByDefault() {
    assertFalse(settings().configCacheEnabled());
  }

  private static Settings settings() {
    return Settings.from(MINIMAL_ENV::get, new YamlApplicationConfiguration());
  }

}
