package org.github.akarkin1.config;

import java.net.URI;
import java.util.Set;

public interface ConfigManager {

  String getBotToken();

  String getBotUsername();

  String getEventRootDir();

  URI getLocalStackS3Endpoint();

  URI getLocalStackSMEndpoint();

  Long getEventTtlMillis();

  String getS3ConfigBucket();

  Set<String> getSupportedServices();

  String getAppVersion();

  String getSecretTokenId();

  String getBotApiBaseUrl();
}
