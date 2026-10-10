package org.github.akarkin1.config;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

import static java.lang.System.getenv;

@Log4j2
@UtilityClass
public class ConfigManager {

  private static final String APP_CONFIG_YAML = "application.yml";

  private static final String BOT_USERNAME_ENV = "BOT_USERNAME";
  private static final String BOT_SECRET_TOKEN_ID_ENV = "BOT_SECRET_TOKEN_ID";
  private static final String CONFIG_CACHE_TTL_SEC_ENV = "CONFIG_CACHE_TTL_SEC";
  private static final String CONFIG_CACHE_ENABLED_ENV = "CONFIG_CACHE_ENABLED";
  private static final String CONFIG_TABLE_NAME_ENV = "CONFIG_TABLE_NAME";
  private static final String METRICS_ENABLED_ENV = "METRICS_ENABLED";
  private static final String BOT_TOKEN_SECRET_ID_ENV = "BOT_TOKEN_SECRET_ID";

  private static final YamlApplicationConfiguration APP_CONFIG = YamlApplicationConfiguration
      .load(APP_CONFIG_YAML);

  public static String getBotUsernameEnv() {
    return getenv(BOT_USERNAME_ENV);
  }

  public static String getAppVersion() {
    return APP_CONFIG.getVersion();
  }

  public static String getSecretTokenId() {
    return envOrThrow(BOT_SECRET_TOKEN_ID_ENV, () -> new IllegalStateException(
        "Environment variable 'BOT_SECRET_TOKEN_ID_ENV' is not set"));
  }

  public static Duration getConfigCacheTtl() {
    String envValSec = envOrDefault(CONFIG_CACHE_TTL_SEC_ENV, "300");
    return Duration.ofSeconds(Long.parseLong(envValSec));
  }

  public static boolean isConfigCacheEnabled() {
    return "true".equalsIgnoreCase(envOrDefault(CONFIG_CACHE_ENABLED_ENV, "false"));
  }

  public static String getConfigTableName() {
    return envOrDefault(CONFIG_TABLE_NAME_ENV, "vpnbot");
  }

  public static boolean isMetricsEnabled() {
    return "true".equalsIgnoreCase(envOrDefault(METRICS_ENABLED_ENV, "true"));
  }

  public static String getBotTokenSecretId() {
    return getenv(BOT_TOKEN_SECRET_ID_ENV);
  }

  public static YamlApplicationConfiguration getApplicationYaml() {
    return APP_CONFIG;
  }

  private static String envOrDefault(String envVarName, String defaultValue) {
    return Optional.ofNullable(getenv(envVarName)).orElse(defaultValue);
  }

  private static String envOrThrow(String envVarName,
                                   Supplier<RuntimeException> exceptionSupplier) {
    return Optional.ofNullable(getenv(envVarName))
        .orElseThrow(exceptionSupplier);
  }

}
