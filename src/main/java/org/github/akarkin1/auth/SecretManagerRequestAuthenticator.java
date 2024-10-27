package org.github.akarkin1.auth;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class SecretManagerRequestAuthenticator implements RequestAuthenticator {

  private final static String SECRET_TOKEN_HEADER = "X-Telegram-Bot-Api-Secret-Token";

  private final SecretsManagerClient client;
  private final String secretTokenId;

  @Override
  public void authenticate(APIGatewayProxyRequestEvent request)
      throws UnauthorizedRequestException {
    log.debug("Request={}", request);
    Map<String, String> headers = request.getHeaders();
    log.debug("Headers={}", headers);
    if (!headers.containsKey(SECRET_TOKEN_HEADER)) {
      log.warn("{} is missing", SECRET_TOKEN_HEADER);
      throw new UnauthorizedRequestException();
    }

    String userTokenValue = headers.get(SECRET_TOKEN_HEADER);

    if (StringUtils.isBlank(userTokenValue)) {
      log.warn("{} is blank", SECRET_TOKEN_HEADER);
      throw new UnauthorizedRequestException();
    }

    GetSecretValueRequest smRequest = GetSecretValueRequest.builder()
        .secretId(secretTokenId)
        .build();

    GetSecretValueResponse smResponse = client.getSecretValue(smRequest);
    String smSecretValue;

    if (smResponse.secretString() != null) {
      smSecretValue = smResponse.secretString();
    } else {
      // Handle binary secret if needed
      byte[] decodedBinarySecret = smResponse.secretBinary().asByteArray();
      smSecretValue = new String(decodedBinarySecret);
    }

    if (!userTokenValue.equals(smSecretValue)) {
      smSecretValue = null;
      throw new UnauthorizedRequestException();
    }

    log.info("Request authenticated successfully");
  }

}
