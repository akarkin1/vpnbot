package org.github.akarkin1.config;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.regions.Region;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.lang.System.getenv;

@Log4j2
@UtilityClass
public class ConfigManager {

  private static final String EVENT_ROOT_DIR = "/mnt/tgbot/eventIds";
  private static final String BOT_TOKEN_ENV = "BOT_TOKEN";
  private static final String BOT_USERNAME_ENV = "BOT_USERNAME";
  private static final String VERSION_RES_PATH = "/version";
  public static final String REGISTERED_EVENT_EXPIRATION_TIME_SEC_ENV = "REGISTERED_EVENT_EXPIRATION_TIME_SEC";

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
    try (InputStream in = ConfigManager.class.getResourceAsStream(VERSION_RES_PATH)) {
      assert in != null : "resource 'version' is missing";
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
        return reader.readLine();
      }
    } catch (Exception e) {
      log.error("Failed to read version: ", e);
      return "<unknown>";
    }
  }

  public List<Region> getSupportedRegions() {
    AppConfig config = AppConfig.getInstance();
    return config.getApp().getSupportedRegions()
        .stream()
        .map(Region::of)
        .collect(Collectors.toList());
  }

  private static String envOrDefault(String envVarName, String defaultValue) {
    return Optional.ofNullable(getenv(envVarName)).orElse(defaultValue);
  }

}
