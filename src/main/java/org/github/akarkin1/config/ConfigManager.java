package org.github.akarkin1.config;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

import java.net.URI;
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
  private static final String BOT_TOKEN_PROP = "tg.bot.token";
  private static final String BOT_USERNAME_ENV = "BOT_USERNAME";
  private static final String BOT_SECRET_TOKEN_ID_ENV = "BOT_SECRET_TOKEN_ID";
  private static final String BOT_SECRET_TOKEN_ID_PROP = "bot.secret.token.id";
  private static final String REGISTERED_EVENT_EXPIRATION_TIME_SEC_ENV = "REGISTERED_EVENT_EXPIRATION_TIME_SEC";
  private static final String S3_CONFIG_BUCKET_ENV = "S3_CONFIG_BUCKET";
  private static final String S3_CONFIG_BUCKET_PROP = "s3.config.bucket";

  private static final YamlApplicationConfiguration APP_CONFIG = YamlApplicationConfiguration
      .load(APP_CONFIG_YAML);
  private static final String BOT_API_BASE_URL_PROP = "tg.bot.api.base.url";

  public static String getBotToken() {
    return envOrDefault(BOT_TOKEN_ENV, System.getProperty(BOT_TOKEN_PROP));
  }

  public static String getBotUsernameEnv() {
    return getenv(BOT_USERNAME_ENV);
  }

  public static String getEventRootDir() {
    return isTestEnvironment() ? "test-eventIds" : EVENT_ROOT_DIR;
  }

  public static boolean isTestEnvironment() {
    return System.getProperty("env", "").equals("test");
  }

  public static URI getLocalStackS3Endpoint() {
    return Optional.ofNullable(System.getProperty("localstack.s3.endpoint"))
      .map(URI::create)
      .orElse(null);
  }

  public static URI getLocalStackSMEndpoint() {
    return Optional.ofNullable(System.getProperty("localstack.secretmanager.endpoint"))
      .map(URI::create)
      .orElse(null);
  }

  public static Long getEventTtlSec() {
    String envVarVal = envOrDefault(REGISTERED_EVENT_EXPIRATION_TIME_SEC_ENV, "360");
    long longValSec = Long.parseLong(envVarVal);
    return TimeUnit.SECONDS.toMillis(longValSec);
  }

  public static String getS3ConfigBucket() {
    return envOrDefault(S3_CONFIG_BUCKET_ENV, System.getProperty(S3_CONFIG_BUCKET_PROP));
  }

  public static Set<String> getSupportedServices() {
    return APP_CONFIG.getS3().getServiceConfigs().keySet();
  }

  public static String getAppVersion() {
    return APP_CONFIG.getVersion();
  }

  public static String getSecretTokenId() {
    return envOrDefault(BOT_SECRET_TOKEN_ID_ENV, System.getProperty(BOT_SECRET_TOKEN_ID_PROP));
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

  public static String getBotApiBaseUrl() {
    return System.getProperty(BOT_API_BASE_URL_PROP);
  }
}
