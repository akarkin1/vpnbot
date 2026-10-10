package org.github.akarkin1.deduplication;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.dynamodb.TgUpdateLock;
import org.github.akarkin1.metrics.MetricComponent;
import org.github.akarkin1.metrics.RequestMetrics;
import org.telegram.telegrambots.meta.api.objects.Update;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Writes a {@link TgUpdateLock} per update with a condition that it does not exist yet, so a
 * re-delivered update is detected atomically. Fails open: a failing lock must not block the bot.
 */
@Log4j2
@RequiredArgsConstructor
public class DynamoDbUpdateEventsRegistry implements UpdateEventsRegistry {

  /** Telegram keeps undelivered updates for at most 24 h. */
  private static final Duration LOCK_TTL = Duration.ofHours(24);
  private static final Expression NOT_EXISTS = Expression.builder()
      .expression("attribute_not_exists(pk)")
      .build();

  private final DynamoDbTable<TgUpdateLock> updateLocks;
  private final Clock clock;
  private final RequestMetrics metrics;

  @Override
  public boolean register(Update update) {
    Instant now = clock.instant();
    TgUpdateLock lock = new TgUpdateLock();
    lock.setUpdateId(String.valueOf(update.getUpdateId()));
    lock.setExpiresAt(now.plus(LOCK_TTL).getEpochSecond());
    lock.setReceivedAt(now.toEpochMilli());
    PutItemEnhancedRequest<TgUpdateLock> request = PutItemEnhancedRequest.builder(TgUpdateLock.class)
        .item(lock)
        .conditionExpression(NOT_EXISTS)
        .build();

    try {
      metrics.time(MetricComponent.DYNAMODB, () -> updateLocks.putItem(request));
      return true;
    } catch (ConditionalCheckFailedException e) {
      return false;
    } catch (Exception e) {
      log.error("Failed to register update {}, processing it anyway", update.getUpdateId(), e);
      return true;
    }
  }

}
