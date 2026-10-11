package org.github.akarkin1.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.dynamodb.PendingUserRecord;
import org.github.akarkin1.dynamodb.UserRecord;
import org.github.akarkin1.metrics.MetricComponent;
import org.github.akarkin1.metrics.RequestMetrics;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps user permissions in the {@code USER} records (keyed by the Telegram user id) and the
 * {@code PENDING_USER} records (keyed by the username of a user pre-registered by an admin);
 * a user without permissions has no record.
 */
@Log4j2
// Pre-registration by username ended with 2026.10. Remove once PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
@RequiredArgsConstructor(onConstructor_ = @SuppressWarnings("removal"))
public class DynamoDbPermissionsService implements PermissionsService {

  private static final Set<String> KNOWN_PERMISSIONS = Arrays.stream(Permission.values())
      .map(Permission::name)
      .collect(Collectors.toUnmodifiableSet());

  private final DynamoDbTable<UserRecord> users;
  // Pre-registration by username ended with 2026.10. Remove once PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
  @SuppressWarnings("removal")
  private final DynamoDbTable<PendingUserRecord> pendingUsers;
  private final RequestMetrics metrics;

  @Override
  public Map<TgUser, List<Permission>> getUsers() {
    List<UserRecord> userRecords = metrics.time(
        MetricComponent.DYNAMODB,
        () -> users.query(allOf(UserRecord.TYPE)).items().stream().toList());
    Map<TgUser, List<Permission>> pending = getPendingUsers();

    Map<TgUser, List<Permission>> result = new LinkedHashMap<>();
    for (UserRecord record : userRecords) {
      Long id = parseUserId(record.getUserId());
      if (id == null) {
        log.warn("USER record with a non-numeric key {} is skipped", record.getUserId());
        continue;
      }
      result.put(new TgUser(id, record.getUsername()),
                 toPermissions(record.getPermissions(), record.getUserId()));
    }
    result.putAll(pending);
    return result;
  }

  /**
   * The users of the {@code PENDING_USER} records, keyed by username without an id.
   *
   * @deprecated Pre-registration by username ended with 2026.10. Remove once
   *     {@code PENDING_USER} records no longer exist (query {@code pk = PENDING_USER}); see
   *     docs/specs/cleanup-2026-10.md §4.3.
   */
  @Deprecated(forRemoval = true)
  // Pre-registration by username ended with 2026.10. Remove once PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
  @SuppressWarnings("removal")
  private Map<TgUser, List<Permission>> getPendingUsers() {
    List<PendingUserRecord> records = metrics.time(
        MetricComponent.DYNAMODB,
        () -> pendingUsers.query(allOf(PendingUserRecord.TYPE)).items().stream().toList());
    Map<TgUser, List<Permission>> result = new LinkedHashMap<>();
    for (PendingUserRecord record : records) {
      result.put(new TgUser(null, record.getUsername()),
                 toPermissions(record.getPermissions(), record.getUsername()));
    }
    return result;
  }

  private static QueryConditional allOf(String type) {
    return QueryConditional.keyEqualTo(Key.builder().partitionValue(type).build());
  }

  private static Long parseUserId(String userId) {
    if (userId == null || userId.isEmpty() || !userId.chars().allMatch(Character::isDigit)) {
      return null;
    }
    try {
      return Long.valueOf(userId);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static List<Permission> toPermissions(Set<String> permissionNames, String userKey) {
    Set<String> names = Optional.ofNullable(permissionNames).orElse(Set.of());
    return names.stream()
        .filter(name -> {
          if (!KNOWN_PERMISSIONS.contains(name)) {
            log.warn("Unknown permission {} of user {} is skipped", name, userKey);
            return false;
          }
          return true;
        })
        .map(Permission::valueOf)
        .sorted()
        .toList();
  }

  // Pre-registration by username ended with 2026.10. Remove once PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
  @SuppressWarnings("removal")
  @Override
  public void updateUserPermissions(TgUser user, Set<Permission> permissions) {
    if (user.pending()) {
      if (user.username() == null || user.username().isBlank()) {
        throw new IllegalArgumentException("A user needs an id or a username: " + user);
      }
      updatePendingUser(user.username(), permissions);
    } else {
      updateUser(user, permissions);
    }
  }

  private void updateUser(TgUser user, Set<Permission> permissions) {
    Key key = Key.builder()
        .partitionValue(UserRecord.TYPE)
        .sortValue(String.valueOf(user.id()))
        .build();
    if (permissions == null || permissions.isEmpty()) {
      metrics.time(MetricComponent.DYNAMODB, () -> users.deleteItem(key));
      return;
    }

    String username = user.username();
    if (username == null) {
      UserRecord stored = metrics.time(MetricComponent.DYNAMODB, () -> users.getItem(key));
      username = stored == null ? null : stored.getUsername();
    }

    UserRecord record = new UserRecord();
    record.setUserId(String.valueOf(user.id()));
    record.setUsername(username);
    record.setPermissions(toNames(permissions));
    metrics.time(MetricComponent.DYNAMODB, () -> users.putItem(record));
  }

  /**
   * Writes or deletes the {@code PENDING_USER} record of {@code username}.
   *
   * @deprecated Pre-registration by username ended with 2026.10. Remove once
   *     {@code PENDING_USER} records no longer exist (query {@code pk = PENDING_USER}); see
   *     docs/specs/cleanup-2026-10.md §4.3.
   */
  @Deprecated(forRemoval = true)
  // Pre-registration by username ended with 2026.10. Remove once PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
  @SuppressWarnings("removal")
  private void updatePendingUser(String username, Set<Permission> permissions) {
    if (permissions == null || permissions.isEmpty()) {
      Key key = Key.builder()
          .partitionValue(PendingUserRecord.TYPE)
          .sortValue(username)
          .build();
      metrics.time(MetricComponent.DYNAMODB, () -> pendingUsers.deleteItem(key));
      return;
    }

    PendingUserRecord record = new PendingUserRecord();
    record.setUsername(username);
    record.setPermissions(toNames(permissions));
    metrics.time(MetricComponent.DYNAMODB, () -> pendingUsers.putItem(record));
  }

  private static Set<String> toNames(Set<Permission> permissions) {
    return permissions.stream()
        .map(Permission::name)
        .collect(Collectors.toSet());
  }

}
