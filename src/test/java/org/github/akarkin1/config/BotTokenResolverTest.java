package org.github.akarkin1.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BotTokenResolverTest {

  private static final String SECRET_ID = "vpn-tg-bot-token";

  @Mock
  private SecretsManagerClient client;

  @Test
  @DisplayName("2a AC-11: a set secret id resolves to the secret value from Secrets Manager")
  void secretIdSet() {
    when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString("secret-token").build());

    String token = new BotTokenResolver(client).resolve(SECRET_ID);

    assertEquals("secret-token", token);
    ArgumentCaptor<GetSecretValueRequest> captor = ArgumentCaptor.forClass(GetSecretValueRequest.class);
    verify(client).getSecretValue(captor.capture());
    assertEquals(SECRET_ID, captor.getValue().secretId());
  }

}
