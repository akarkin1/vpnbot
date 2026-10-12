package org.github.akarkin1.region;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.dynamodb.RegionRequestRecord;
import org.github.akarkin1.metrics.MetricComponent;
import org.github.akarkin1.metrics.RequestMetrics;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Keeps region requests in the {@code REGION_REQUEST} records (keyed by the region id). DynamoDB
 * deletes expired records by TTL with a delay, so {@link #find} and {@link #findAll} ignore
 * expired ones themselves.
 */
@RequiredArgsConstructor
public class DynamoDbRegionRequestService implements RegionRequestService {

  /** Deploying a region is not a same-day decision: a request lives for 30 days (D-5). */
  public static final Duration REQUEST_TTL = Duration.ofDays(30);

  private final DynamoDbTable<RegionRequestRecord> requests;
  private final Clock clock;
  private final RequestMetrics metrics;

  @Override
  public Optional<RegionRequest> find(String regionId) {
    RegionRequestRecord record = metrics.time(MetricComponent.DYNAMODB,
                                              () -> requests.getItem(key(regionId)));
    if (record == null || isExpired(record)) {
      return Optional.empty();
    }
    return Optional.of(toRequest(record));
  }

  @Override
  public List<RegionRequest> findAll() {
    QueryConditional all = QueryConditional.keyEqualTo(
        Key.builder().partitionValue(RegionRequestRecord.TYPE).build());
    List<RegionRequestRecord> records = metrics.time(
        MetricComponent.DYNAMODB, () -> requests.query(all).items().stream().toList());
    return records.stream()
        .filter(record -> !isExpired(record))
        .map(DynamoDbRegionRequestService::toRequest)
        .toList();
  }

  @Override
  public void create(RegionRequest request) {
    RegionRequestRecord record = new RegionRequestRecord();
    record.setRegionId(request.regionId());
    record.setUserId(request.userId());
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
  public void delete(String regionId) {
    metrics.time(MetricComponent.DYNAMODB, () -> requests.deleteItem(key(regionId)));
  }

  private boolean isExpired(RegionRequestRecord record) {
    return record.getExpiresAt() != null
           && record.getExpiresAt() <= clock.instant().getEpochSecond();
  }

  private static RegionRequest toRequest(RegionRequestRecord record) {
    long userId = record.getUserId() == null ? 0 : record.getUserId();
    long requestedAt = record.getRequestedAt() == null ? 0 : record.getRequestedAt();
    return new RegionRequest(record.getRegionId(), userId, record.getUsername(),
                             record.getFirstName(), record.getLastName(),
                             record.getLanguageCode(), requestedAt);
  }

  private static Key key(String regionId) {
    return Key.builder()
        .partitionValue(RegionRequestRecord.TYPE)
        .sortValue(regionId)
        .build();
  }

}
