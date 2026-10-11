package org.github.akarkin1.config;

import org.github.akarkin1.config.YamlApplicationConfiguration.AWSConfiguration;
import org.github.akarkin1.config.YamlApplicationConfiguration.EcsConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * cleanup-2026-10 §4.7 rule 1: {@link Settings#fromEnvironment()} is the one place that reads the
 * environment and {@code application.yml} (the cases of the deleted {@code ConfigManagerTest} moved
 * here). Environment variables cannot be set from a test in this codebase (no extra dependency),
 * so the env-dependent cases check the defaults and are skipped where a variable happens to be set.
 * {@code BOT_SECRET_TOKEN_ID} is required: without it {@code fromEnvironment()} fails, so the
 * default cases only run where it is set. On the test classpath {@code application.yml} is the one
 * in {@code src/test/resources}; the real file is checked through {@link MainApplicationYaml}.
 */
class SettingsTest {

  private static final String BOT_SECRET_TOKEN_ID = "BOT_SECRET_TOKEN_ID";

  @Test
  @DisplayName("§4.7 rule 1: without BOT_SECRET_TOKEN_ID fromEnvironment() fails with IllegalStateException, as ConfigManager.getSecretTokenId did")
  void secretTokenIdRequired() {
    assumeTrue(System.getenv(BOT_SECRET_TOKEN_ID) == null, BOT_SECRET_TOKEN_ID + " is set");

    assertThrows(IllegalStateException.class, Settings::fromEnvironment);
  }

  @Test
  @DisplayName("3 AC-A8: without CONFIG_TABLE_NAME the table name is vpnbot")
  void configTableNameDefault() {
    assumeSecretTokenIdSet();
    assumeTrue(System.getenv("CONFIG_TABLE_NAME") == null, "CONFIG_TABLE_NAME is set");

    assertEquals("vpnbot", Settings.fromEnvironment().configTableName());
  }

  @Test
  @DisplayName("3 AC-A12: without CONFIG_CACHE_ENABLED the config cache is off")
  void configCacheDisabledByDefault() {
    assumeSecretTokenIdSet();
    assumeTrue(System.getenv("CONFIG_CACHE_ENABLED") == null, "CONFIG_CACHE_ENABLED is set");

    assertFalse(Settings.fromEnvironment().configCacheEnabled());
  }

  @Test
  @DisplayName("§4.7 rule 1: without CONFIG_CACHE_TTL_SEC the cache TTL is 300 s, as before")
  void configCacheTtlDefault() {
    assumeSecretTokenIdSet();
    assumeTrue(System.getenv("CONFIG_CACHE_TTL_SEC") == null, "CONFIG_CACHE_TTL_SEC is set");

    assertEquals(Duration.ofSeconds(300), Settings.fromEnvironment().configCacheTtl());
  }

  @Test
  @DisplayName("2a §4.5: without METRICS_ENABLED metrics are on")
  void metricsEnabledByDefault() {
    assumeSecretTokenIdSet();
    assumeTrue(System.getenv("METRICS_ENABLED") == null, "METRICS_ENABLED is set");

    assertTrue(Settings.fromEnvironment().metricsEnabled());
  }

  @Test
  @DisplayName("§4.7 rule 1: the plain env values are passed through as they are")
  void envValuesPassedThrough() {
    assumeSecretTokenIdSet();

    Settings settings = Settings.fromEnvironment();

    assertAll(
        () -> assertEquals(System.getenv(BOT_SECRET_TOKEN_ID), settings.secretTokenId()),
        () -> assertEquals(System.getenv("BOT_TOKEN_SECRET_ID"), settings.botTokenSecretId()),
        () -> assertEquals(System.getenv("BOT_USERNAME"), settings.botUsername()));
  }

  @Test
  @DisplayName("§4.7 rule 1: the version and the sections come from the application.yml on the classpath (the test one here)")
  void applicationYamlFromClasspath() {
    assumeSecretTokenIdSet();

    Settings settings = Settings.fromEnvironment();

    assertEquals("test", settings.appVersion());
    assertNotNull(settings.auth());
    assertTrue(settings.auth().isEnabled());
  }

  @Test
  @DisplayName("§4.7: the real application.yml has the version placeholder, auth enabled, the ECS tag names and the region maps that Settings carries")
  void mainApplicationYamlSections() {
    YamlApplicationConfiguration yaml = MainApplicationYaml.load();
    EcsConfiguration ecs = yaml.getEcs();
    AWSConfiguration aws = yaml.getAws();

    assertAll(
        () -> assertEquals("${project.version}", yaml.getVersion()),
        () -> assertTrue(yaml.getAuth().isEnabled()),
        () -> assertEquals("tailscale-node", ecs.getServiceName()),
        () -> assertEquals("vpn-container", ecs.getEssentialContainerName()),
        () -> assertEquals("TAILSCALE_HOSTNAME", ecs.getHostNameEnv()),
        () -> assertEquals("ServiceName", ecs.getServiceNameTag()),
        () -> assertEquals("Hostname", ecs.getHostNameTag()),
        () -> assertEquals("RunBy", ecs.getRunByTag()),
        () -> assertEquals("RunByName", ecs.getRunByNameTag()),
        () -> assertEquals("ChatId", ecs.getChatIdTag()),
        () -> assertEquals("Lang", ecs.getLanguageTag()),
        () -> assertEquals("Frankfurt", aws.getRegionCities().get("eu-central-1")),
        () -> assertEquals("DE", aws.getRegionCountries().get("eu-central-1")),
        () -> assertEquals(aws.getRegionCities().keySet(), aws.getRegionCountries().keySet()));
  }

  private static void assumeSecretTokenIdSet() {
    assumeTrue(System.getenv(BOT_SECRET_TOKEN_ID) != null,
               BOT_SECRET_TOKEN_ID + " is not set: fromEnvironment() fails without it");
  }

}
