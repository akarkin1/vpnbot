package org.github.akarkin1.config;

import org.github.akarkin1.config.YamlApplicationConfiguration.AWSConfiguration;
import org.github.akarkin1.config.YamlApplicationConfiguration.EcsConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * cleanup-2026-10 §4.7 rule 1: {@link Settings} is the one place that reads the environment and
 * {@code application.yml}. The cases of the deleted {@code ConfigManagerTest} run through
 * {@link Settings#from} with an explicit environment, so they do not depend on the process
 * environment; the real {@code application.yml} is checked through {@link MainApplicationYaml}.
 */
class SettingsTest {

  /** The one required variable; everything else is left to its default. */
  private static final Map<String, String> MINIMAL_ENV = Map.of("BOT_SECRET_TOKEN_ID", "secret-id");

  @Test
  @DisplayName("§4.7 rule 1: without BOT_SECRET_TOKEN_ID the settings fail with IllegalStateException, as ConfigManager.getSecretTokenId did")
  void secretTokenIdRequired() {
    assertThrows(IllegalStateException.class,
                 () -> Settings.from(Map.<String, String>of()::get, new YamlApplicationConfiguration()));
  }

  @Test
  @DisplayName("3 AC-A8, 3 AC-A12, 2a §4.5: the defaults – table vpnbot, cache off, TTL 300 s, metrics on")
  void defaults() {
    Settings settings = Settings.from(MINIMAL_ENV::get, new YamlApplicationConfiguration());

    assertAll(
        () -> assertEquals("vpnbot", settings.configTableName()),
        () -> assertFalse(settings.configCacheEnabled()),
        () -> assertEquals(Duration.ofSeconds(300), settings.configCacheTtl()),
        () -> assertTrue(settings.metricsEnabled()),
        () -> assertEquals("secret-id", settings.webhookSecretTokenId()));
  }

  @Test
  @DisplayName("§4.7 rule 1: the env values are passed through, flags are parsed case-insensitively")
  void envValuesPassedThrough() {
    Map<String, String> env = new HashMap<>(MINIMAL_ENV);
    env.put("CONFIG_TABLE_NAME", "vpnbot-test");
    env.put("BOT_TOKEN_SECRET_ID", "token-id");
    env.put("BOT_USERNAME", "vpn_bot");
    env.put("METRICS_ENABLED", "FALSE");
    env.put("CONFIG_CACHE_ENABLED", "True");
    env.put("CONFIG_CACHE_TTL_SEC", "42");

    Settings settings = Settings.from(env::get, new YamlApplicationConfiguration());

    assertAll(
        () -> assertEquals("vpnbot-test", settings.configTableName()),
        () -> assertEquals("token-id", settings.botTokenSecretId()),
        () -> assertEquals("vpn_bot", settings.botUsername()),
        () -> assertFalse(settings.metricsEnabled()),
        () -> assertTrue(settings.configCacheEnabled()),
        () -> assertEquals(Duration.ofSeconds(42), settings.configCacheTtl()));
  }

  @Test
  @DisplayName("§4.7 rule 1: the version and the yaml sections are taken from the parsed application.yml")
  void yamlSectionsCarried() {
    YamlApplicationConfiguration yaml = MainApplicationYaml.load();

    Settings settings = Settings.from(MINIMAL_ENV::get, yaml);

    assertAll(
        () -> assertEquals(yaml.getVersion(), settings.appVersion()),
        () -> assertSame(yaml.getAuth(), settings.auth()),
        () -> assertSame(yaml.getEcs(), settings.ecs()),
        () -> assertSame(yaml.getAws(), settings.aws()));
  }

  @Test
  @DisplayName("§4.7 rule 1: fromEnvironment() reads the application.yml on the classpath (the test one here: version test)")
  void fromEnvironmentReadsClasspathYaml() {
    if (System.getenv("BOT_SECRET_TOKEN_ID") == null) {
      assertThrows(IllegalStateException.class, Settings::fromEnvironment);
      return;
    }
    assertEquals("test", Settings.fromEnvironment().appVersion());
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

}
