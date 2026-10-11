package org.github.akarkin1.auth;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.dynamodb.AccessRequestRecord;
import org.github.akarkin1.metrics.MetricComponent;
import org.github.akarkin1.metrics.RequestMetrics;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Keeps access requests in the {@code ACCESS_REQUEST} records (keyed by the user id). DynamoDB
 * deletes expired records by TTL with a delay, so {@link #find} ignores expired ones itself.
 */
@RequiredArgsConstructor
public class DynamoDbAccessRequestService implements AccessRequestService {

  /** An undecided request expires after a week; the user can ask again. */
  public static final Duration REQUEST_TTL = Duration.ofDays(7);

  private final DynamoDbTable<AccessRequestRecord> requests;
  private final Clock clock;
  private final RequestMetrics metrics;

  @Override
  public Optional<AccessRequest> find(long userId) {
    AccessRequestRecord record = metrics.time(MetricComponent.DYNAMODB,
                                              () -> requests.getItem(key(userId)));
    if (record == null || isExpired(record)) {
      return Optional.empty();
    }
    long requestedAt = record.getRequestedAt() == null ? 0 : record.getRequestedAt();
    return Optional.of(new AccessRequest(userId, record.getUsername(), record.getFirstName(),
                                         record.getLastName(), record.getLanguageCode(),
                                         requestedAt));
  }

  @Override
  public void create(AccessRequest request) {
    AccessRequestRecord record = new AccessRequestRecord();
    record.setUserId(String.valueOf(request.userId()));
    record.setUsername(request.username());
    record.setFirstName(request.firstName());
    record.setLastName(request.lastName());
    record.setLanguageCode(request.languageCode());
    record.setRequestedAt(request.requestedAt());
    record.setExpiresAt(Instant.ofEpochMilli(request.requestedAt()).plus(REQUEST_TTL)
                            .getEpochSecond());
    metrics.time(MetricComponent.DYNAMODB, () -> requests.putItem(record));
  }

  @Override
  public void delete(long userId) {
    metrics.time(MetricComponent.DYNAMODB, () -> requests.deleteItem(key(userId)));
  }

  private boolean isExpired(AccessRequestRecord record) {
    return record.getExpiresAt() != null
           && record.getExpiresAt() <= clock.instant().getEpochSecond();
  }

  private static Key key(long userId) {
    return Key.builder()
        .partitionValue(AccessRequestRecord.TYPE)
        .sortValue(String.valueOf(userId))
        .build();
  }

}
