package org.github.akarkin1.config;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static java.lang.System.getenv;

@Log4j2
@UtilityClass
public class ConfigManager {

  private static final String APP_CONFIG_YAML = "application.yml";

  private static final String EVENT_ROOT_DIR = "/mnt/efs/eventIds";
  private static final String BOT_TOKEN_ENV = "BOT_TOKEN";
  private static final String BOT_USERNAME_ENV = "BOT_USERNAME";
  private static final String BOT_SECRET_TOKEN_ID_ENV = "BOT_SECRET_TOKEN_ID";
  private static final String STATUS_CHECK_PAUSE_MS_ENV = "STATUS_CHECK_PAUSE_MS";
  private static final String OP_WAIT_TIMEOUT_SEC_ENV = "OPERATION_WAIT_TIMEOUT_SEC";
  private static final String RESTART_SLEEP_TIME_SEC_ENV = "RESTART_SLEEP_TIME_SEC";
  private static final String REGISTERED_EVENT_EXPIRATION_TIME_SEC_ENV = "REGISTERED_EVENT_EXPIRATION_TIME_SEC";
  private static final String USED_REGIONS_ENV = "USED_REGIONS";

  private static final YamlApplicationConfiguration APP_CONFIG = YamlApplicationConfiguration
      .load(APP_CONFIG_YAML);

  public static String getBotToken() {
    return getenv(BOT_TOKEN_ENV);
  }

  public static String getBotUsernameEnv() {
    return getenv(BOT_USERNAME_ENV);
  }

  public static String getEventRootDir() {
    return EVENT_ROOT_DIR;
  }

  public static Long getEventTtlSec() {
    String envVarVal = envOrDefault(REGISTERED_EVENT_EXPIRATION_TIME_SEC_ENV, "360");
    long longValSec = Long.parseLong(envVarVal);
    return TimeUnit.SECONDS.toMillis(longValSec);
  }

  public static String getAppVersion() {
    return APP_CONFIG.getVersion();
  }

  public static long getStatusCheckWaitIntervalMs() {
    String envValMs = envOrDefault(STATUS_CHECK_PAUSE_MS_ENV, "500");
    return Long.parseLong(envValMs);
  }

  public static long getOperationTimeoutMs() {
    String envValSec = envOrDefault(OP_WAIT_TIMEOUT_SEC_ENV, "120");
    long longValSec = Long.parseLong(envValSec);
    return TimeUnit.SECONDS.toMillis(longValSec);
  }

  public static long getRestartPauseMs() {
    String envValSec = envOrDefault(RESTART_SLEEP_TIME_SEC_ENV, "30");
    long longValSec = Long.parseLong(envValSec);
    return TimeUnit.SECONDS.toMillis(longValSec);
  }

  public static List<String> getUsedRegions() {
    String envValStr = envOrDefault(USED_REGIONS_ENV, "");
    return List.of(envValStr.split(","));
  }

  public static String getSecretTokenId() {
    return envOrThrow(BOT_SECRET_TOKEN_ID_ENV, () -> new IllegalStateException(
        "Environment variable 'BOT_SECRET_TOKEN_ID_ENV' is not set"));
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
