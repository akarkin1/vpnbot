package org.github.akarkin1.config;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

@RequiredArgsConstructor
public class BotTokenResolver {

  private final SecretsManagerClient client;

  public String resolve(String secretId) {
    if (StringUtils.isBlank(secretId)) {
      throw new IllegalStateException("BOT_TOKEN_SECRET_ID is not set");
    }

    GetSecretValueRequest request = GetSecretValueRequest.builder()
        .secretId(secretId)
        .build();
    return client.getSecretValue(request).secretString();
  }

}
