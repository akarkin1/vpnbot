package org.github.akarkin1.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Environment variables cannot be set from a test in this codebase (no extra dependency), so only
 * the defaults are checked; the tests are skipped where the variable happens to be set.
 */
class ConfigManagerTest {

  @Test
  @DisplayName("3 AC-A8: without CONFIG_TABLE_NAME the table name is vpnbot")
  void configTableNameDefault() {
    assumeTrue(System.getenv("CONFIG_TABLE_NAME") == null, "CONFIG_TABLE_NAME is set");

    assertEquals("vpnbot", ConfigManager.getConfigTableName());
  }

  @Test
  @DisplayName("3 AC-A12: without CONFIG_CACHE_ENABLED the config cache is off")
  void configCacheDisabledByDefault() {
    assumeTrue(System.getenv("CONFIG_CACHE_ENABLED") == null, "CONFIG_CACHE_ENABLED is set");

    assertFalse(ConfigManager.isConfigCacheEnabled());
  }

}
