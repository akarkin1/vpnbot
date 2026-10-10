package org.github.akarkin1.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.dynamodb.UserRecord;
import org.github.akarkin1.metrics.MetricComponent;
import org.github.akarkin1.metrics.RequestMetrics;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Keeps user permissions in the {@code USER} records; a user without permissions has no record. */
@Log4j2
@RequiredArgsConstructor
public class DynamoDbPermissionsService implements PermissionsService {

  private static final Set<String> KNOWN_PERMISSIONS = Arrays.stream(Permission.values())
      .map(Permission::name)
      .collect(Collectors.toUnmodifiableSet());

  private final DynamoDbTable<UserRecord> users;
  private final RequestMetrics metrics;

  @Override
  public Map<String, List<Permission>> getUserPermissions() {
    QueryConditional allUsers = QueryConditional.keyEqualTo(
        Key.builder().partitionValue(UserRecord.TYPE).build());
    List<UserRecord> records = metrics.time(MetricComponent.DYNAMODB,
                                            () -> users.query(allUsers).items()
                                                .stream()
                                                .toList());

    Map<String, List<Permission>> userPermissions = new HashMap<>();
    for (UserRecord record : records) {
      userPermissions.put(record.getUsername(), toPermissions(record));
    }
    return userPermissions;
  }

  private static List<Permission> toPermissions(UserRecord record) {
    Set<String> names = Optional.ofNullable(record.getPermissions()).orElse(Set.of());
    return names.stream()
        .filter(name -> {
          if (!KNOWN_PERMISSIONS.contains(name)) {
            log.warn("Unknown permission {} of user {} is skipped", name, record.getUsername());
            return false;
          }
          return true;
        })
        .map(Permission::valueOf)
        .sorted()
        .toList();
  }

  @Override
  public void updateUserPermissions(String tgUsername, Set<Permission> permissions) {
    if (permissions == null || permissions.isEmpty()) {
      Key key = Key.builder()
          .partitionValue(UserRecord.TYPE)
          .sortValue(tgUsername)
          .build();
      metrics.time(MetricComponent.DYNAMODB, () -> users.deleteItem(key));
      return;
    }

    UserRecord record = new UserRecord();
    record.setUsername(tgUsername);
    record.setPermissions(permissions.stream()
                              .map(Permission::name)
                              .collect(Collectors.toSet()));
    metrics.time(MetricComponent.DYNAMODB, () -> users.putItem(record));
  }

}
