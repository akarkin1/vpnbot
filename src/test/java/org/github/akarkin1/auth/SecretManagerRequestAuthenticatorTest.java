package org.github.akarkin1.auth;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import org.github.akarkin1.util.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SecretManagerRequestAuthenticatorTest {

  private static final String HEADER = "x-telegram-bot-api-secret-token";
  private static final String SECRET_ID = "webhook-secret";
  private static final String SECRET = "s3cr3t";
  private static final Duration TTL = Duration.ofSeconds(300);

  @Mock
  private SecretsManagerClient client;

  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-09T10:00:00Z"));
  private SecretManagerRequestAuthenticator authenticator;

  @BeforeEach
  void setUp() {
    lenient().when(client.getSecretValue(any(GetSecretValueRequest.class)))
        .thenReturn(GetSecretValueResponse.builder().secretString(SECRET).build());
    authenticator = new SecretManagerRequestAuthenticator(client, SECRET_ID, TTL, clock);
  }

  @Test
  @DisplayName("2a AC-10: the right token is accepted and the secret is read by its id")
  void rightTokenAccepted() {
    assertDoesNotThrow(() -> authenticator.authenticate(request(SECRET)));

    ArgumentCaptor<GetSecretValueRequest> captor = ArgumentCaptor.forClass(GetSecretValueRequest.class);
    verify(client).getSecretValue(captor.capture());
    assertEquals(SECRET_ID, captor.getValue().secretId());
  }

  @Test
  @DisplayName("2a AC-10: the secret is fetched once within the TTL")
  void secretFetchedOncePerTtl() {
    authenticator.authenticate(request(SECRET));
    clock.advance(TTL.minusMillis(1));
    authenticator.authenticate(request(SECRET));
    assertThrows(UnauthenticatedRequestException.class, () -> authenticator.authenticate(request("wrong")));

    verify(client, times(1)).getSecretValue(any(GetSecretValueRequest.class));
  }

  @Test
  @DisplayName("2a AC-10: the secret is fetched again after the TTL")
  void secretFetchedAgainAfterTtl() {
    authenticator.authenticate(request(SECRET));
    clock.advance(TTL.plusMillis(1));
    authenticator.authenticate(request(SECRET));

    verify(client, times(2)).getSecretValue(any(GetSecretValueRequest.class));
  }

  @Test
  @DisplayName("2a AC-10: a missing token header is rejected")
  void missingTokenRejected() {
    APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent().withHeaders(new HashMap<>());

    assertThrows(UnauthenticatedRequestException.class, () -> authenticator.authenticate(request));
  }

  @Test
  @DisplayName("2a AC-10: a blank token is rejected")
  void blankTokenRejected() {
    assertThrows(UnauthenticatedRequestException.class, () -> authenticator.authenticate(request("")));
    assertThrows(UnauthenticatedRequestException.class, () -> authenticator.authenticate(request("  ")));
  }

  @Test
  @DisplayName("2a AC-10: a wrong token is rejected, also when the secret is cached")
  void wrongTokenRejected() {
    assertThrows(UnauthenticatedRequestException.class, () -> authenticator.authenticate(request("wrong")));
    authenticator.authenticate(request(SECRET));
    assertThrows(UnauthenticatedRequestException.class, () -> authenticator.authenticate(request("wrong")));
  }

  private static APIGatewayProxyRequestEvent request(String token) {
    return new APIGatewayProxyRequestEvent().withHeaders(Map.of(HEADER, token));
  }

}
