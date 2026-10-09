package org.github.akarkin1.config;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

@RequiredArgsConstructor
public class BotTokenResolver {

  private final SecretsManagerClient client;

  public String resolve(String secretId, String fallbackToken) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

}
