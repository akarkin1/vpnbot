package org.github.akarkin1.auth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory {@link PermissionsService} for controller tests. Only {@link #updateUserPermissions}
 * is implemented here; {@code assignRolesToUser} and {@code deleteUser} are the interface defaults,
 * so a test sees the resulting permissions whichever of the three methods the code under test
 * calls. Like the DynamoDB service, a user with an id keeps the stored username when the given
 * one is null.
 */
public class FakePermissionsService implements PermissionsService {

  private final Map<TgUser, List<Permission>> users = new LinkedHashMap<>();
  private int writes;

  public FakePermissionsService with(TgUser user, Permission... permissions) {
    users.put(user, List.of(permissions));
    return this;
  }

  @Override
  public Map<TgUser, List<Permission>> getUsers() {
    return new LinkedHashMap<>(users);
  }

  @Override
  public void updateUserPermissions(TgUser user, Set<Permission> permissions) {
    writes++;
    TgUser stored = storedKey(user);
    users.remove(user);
    if (permissions == null || permissions.isEmpty()) {
      return;
    }
    String username = user.username() != null || stored == null ? user.username() : stored.username();
    users.put(new TgUser(user.id(), username), new ArrayList<>(permissions));
  }

  /** The stored user equal to {@code user} (with its stored username), or null. */
  public TgUser storedKey(TgUser user) {
    return users.keySet().stream().filter(user::equals).findFirst().orElse(null);
  }

  /** The stored permissions of {@code user} as a set, empty when the user is unknown. */
  public Set<Permission> permissionsOf(TgUser user) {
    List<Permission> permissions = users.get(user);
    return permissions == null ? Set.of() : Set.copyOf(permissions);
  }

  /** Number of {@code updateUserPermissions} calls (every write, including deletes). */
  public int writes() {
    return writes;
  }

}
