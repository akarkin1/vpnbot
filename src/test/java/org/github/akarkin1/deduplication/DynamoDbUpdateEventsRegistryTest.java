package org.github.akarkin1.deduplication;

import org.github.akarkin1.dynamodb.TgUpdateLock;
import org.github.akarkin1.metrics.MetricComponent;
import org.github.akarkin1.metrics.RecordingRequestMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Update;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DynamoDbUpdateEventsRegistryTest {

  private static final Instant NOW = Instant.parse("2026-10-10T12:00:00.123Z");
  private static final int UPDATE_ID = 123456789;

  @Mock
  private DynamoDbTable<TgUpdateLock> updateLocks;

  @Captor
  private ArgumentCaptor<PutItemEnhancedRequest<TgUpdateLock>> request;

  private final RecordingRequestMetrics metrics = new RecordingRequestMetrics();

  private DynamoDbUpdateEventsRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new DynamoDbUpdateEventsRegistry(updateLocks, Clock.fixed(NOW, ZoneOffset.UTC),
                                                metrics);
  }

  @Test
  @DisplayName("3 AC-A5: a new update is locked with a conditional PutItem and is not a duplicate")
  void firstRegistration() {
    assertTrue(registry.register(update(UPDATE_ID)));

    verify(updateLocks).putItem(request.capture());
    TgUpdateLock lock = request.getValue().item();
    assertEquals("TG_UPDATE_LOCK", lock.getType());
    assertEquals("123456789", lock.getUpdateId());
    assertEquals(NOW.getEpochSecond() + 86_400, lock.getExpiresAt());
    assertEquals(NOW.toEpochMilli(), lock.getReceivedAt());
    assertEquals("attribute_not_exists(pk)", request.getValue().conditionExpression().expression());
  }

  @Test
  @DisplayName("3 AC-A5: an update that is already locked is a duplicate")
  @SuppressWarnings("unchecked")
  void duplicate() {
    doThrow(ConditionalCheckFailedException.builder().message("The conditional request failed")
                .build())
        .when(updateLocks).putItem(any(PutItemEnhancedRequest.class));

    assertFalse(registry.register(update(UPDATE_ID)));
  }

  @Test
  @DisplayName("3 AC-A5: any other DynamoDB failure fails open (the update is processed)")
  @SuppressWarnings("unchecked")
  void otherDynamoDbFailureFailsOpen() {
    doThrow(DynamoDbException.builder().message("Throughput exceeded").build())
        .when(updateLocks).putItem(any(PutItemEnhancedRequest.class));

    assertTrue(registry.register(update(UPDATE_ID)));
  }

  @Test
  @DisplayName("3 AC-A5: any other runtime failure fails open (the update is processed)")
  @SuppressWarnings("unchecked")
  void runtimeFailureFailsOpen() {
    doThrow(new IllegalStateException("connection reset"))
        .when(updateLocks).putItem(any(PutItemEnhancedRequest.class));

    assertTrue(registry.register(update(UPDATE_ID)));
  }

  @Test
  @DisplayName("3 AC-A7: the PutItem runs inside a DYNAMODB timing")
  @SuppressWarnings("unchecked")
  void putTimed() {
    AtomicBoolean timed = new AtomicBoolean();
    doAnswer(invocation -> {
      timed.set(metrics.isTiming(MetricComponent.DYNAMODB));
      return null;
    }).when(updateLocks).putItem(any(PutItemEnhancedRequest.class));

    registry.register(update(UPDATE_ID));

    assertTrue(timed.get(), "PutItem ran outside a DYNAMODB timing");
  }

  private static Update update(int updateId) {
    Update update = new Update();
    update.setUpdateId(updateId);
    return update;
  }

}
