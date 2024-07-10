package org.github.akarkin1;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.lang.System.getenv;

@Log4j2
@UtilityClass
public class ConfigManager {

  private static final String EVENT_ROOT_DIR = "/mnt/tgbot/eventIds";
  private static final String BOT_TOKEN_ENV = "BOT_TOKEN";
  private static final String BOT_USERNAME_ENV = "BOT_USERNAME";
  private static final String STATUS_CHECK_PAUSE_MS_ENV = "STATUS_CHECK_PAUSE_MS";
  private static final String OP_WAIT_TIMEOUT_SEC_ENV = "OPERATION_WAIT_TIMEOUT_SEC";
  private static final String VERSION_RES_PATH = "/version";

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
    String envVarVal = Optional.ofNullable(getenv("REGISTERED_EVENT_EXPIRATION_TIME_SEC"))
        .orElse("360");
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

  public static long getStatusCheckWaitIntervalMs() {
    String envValMs = Optional.ofNullable(getenv(STATUS_CHECK_PAUSE_MS_ENV))
        .orElse("500");
    return Long.parseLong(envValMs);
  }

  public static long getOperationTimeoutMs() {
    String envValSec = Optional.ofNullable(getenv(OP_WAIT_TIMEOUT_SEC_ENV))
        .orElse("120");
    long longValSec = Long.parseLong(envValSec);
    return TimeUnit.SECONDS.toMillis(longValSec);
  }
}
