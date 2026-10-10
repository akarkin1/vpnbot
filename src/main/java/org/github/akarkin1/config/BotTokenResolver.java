package org.github.akarkin1.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

@Log4j2
@RequiredArgsConstructor
public class BotTokenResolver {

  private final SecretsManagerClient client;

  public String resolve(String secretId, String fallbackToken) {
    if (StringUtils.isBlank(secretId)) {
      log.info("Bot token secret id is not set, using the bot token from the environment");
      return fallbackToken;
    }

    GetSecretValueRequest request = GetSecretValueRequest.builder()
        .secretId(secretId)
        .build();
    return client.getSecretValue(request).secretString();
  }

}
