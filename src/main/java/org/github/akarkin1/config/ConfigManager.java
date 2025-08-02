package org.github.akarkin1.config;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

import java.util.Optional;
import java.util.Set;
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
  private static final String REGISTERED_EVENT_EXPIRATION_TIME_SEC_ENV = "REGISTERED_EVENT_EXPIRATION_TIME_SEC";
  private static final String S3_CONFIG_BUCKET = "S3_CONFIG_BUCKET";

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

  public static String getS3ConfigBucket() {
    return envOrDefault(S3_CONFIG_BUCKET, System.getProperty(S3_CONFIG_BUCKET));
  }

  public static Set<String> getSupportedServices() {
    return APP_CONFIG.getS3().getServiceConfigs().keySet();
  }

  public static String getAppVersion() {
    return APP_CONFIG.getVersion();
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
