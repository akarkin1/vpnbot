package org.github.akarkin1.auth;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class SecretManagerRequestAuthenticator implements RequestAuthenticator {

  private final static String SECRET_TOKEN_HEADER = "x-telegram-bot-api-secret-token";

  private final SecretsManagerClient client;
  private final String secretTokenId;
  private final Duration ttl;
  private final Clock clock;

  private String cachedSecretValue;
  private Instant cachedUntil;

  @Override
  public void authenticate(APIGatewayProxyRequestEvent request)
      throws UnauthenticatedRequestException {
    log.debug("Request={}", request);
    Map<String, String> headers = request.getHeaders();
    log.debug("Headers={}", headers);
    if (!headers.containsKey(SECRET_TOKEN_HEADER)) {
      log.warn("{} is missing", SECRET_TOKEN_HEADER);
      throw new UnauthenticatedRequestException();
    }

    String userTokenValue = headers.get(SECRET_TOKEN_HEADER);

    if (StringUtils.isBlank(userTokenValue)) {
      log.warn("{} is blank", SECRET_TOKEN_HEADER);
      throw new UnauthenticatedRequestException();
    }

    if (!userTokenValue.equals(secretValue())) {
      throw new UnauthenticatedRequestException();
    }

    log.info("Request authenticated successfully");
  }

  private synchronized String secretValue() {
    Instant now = clock.instant();
    if (cachedSecretValue == null || !now.isBefore(cachedUntil)) {
      cachedSecretValue = fetchSecretValue();
      cachedUntil = now.plus(ttl);
    }
    return cachedSecretValue;
  }

  private String fetchSecretValue() {
    GetSecretValueRequest smRequest = GetSecretValueRequest.builder()
        .secretId(secretTokenId)
        .build();

    GetSecretValueResponse smResponse = client.getSecretValue(smRequest);
    if (smResponse.secretString() != null) {
      return smResponse.secretString();
    }
    // Handle binary secret if needed
    byte[] decodedBinarySecret = smResponse.secretBinary().asByteArray();
    return new String(decodedBinarySecret);
  }

}
