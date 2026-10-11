package org.github.akarkin1.auth;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * In-memory {@link PermissionsService} with the semantics of §4.1: {@code USER} records keyed by
 * the user id (with a username attribute), {@code PENDING_USER} records keyed by the username.
 * {@code deleteUser}/{@code assignRolesToUser} are the interface defaults, so every write goes
 * through {@link #updateUserPermissions} and is logged in {@link #writes()} in call order.
 */
public class FakePermissionsService implements PermissionsService {

  /** One write: {@code permissions == null} means the record was deleted. */
  public record Write(String type, String key, String username, Set<Permission> permissions) {

    public static Write putUser(long id, String username, Permission... permissions) {
      return new Write("USER", String.valueOf(id), username, Set.of(permissions));
    }

    public static Write deleteUser(long id) {
      return new Write("USER", String.valueOf(id), null, null);
    }

    public static Write putPending(String username, Permission... permissions) {
      return new Write("PENDING_USER", username, null, Set.of(permissions));
    }

    public static Write deletePending(String username) {
      return new Write("PENDING_USER", username, null, null);
    }

  }

  private record StoredUser(String username, Set<Permission> permissions) {}

  private final Map<Long, StoredUser> users = new LinkedHashMap<>();
  private final Map<String, Set<Permission>> pending = new LinkedHashMap<>();
  private final List<Write> writes = new ArrayList<>();

  public FakePermissionsService withUser(long id, String username, Permission... permissions) {
    users.put(id, new StoredUser(username, permissionSet(permissions)));
    return this;
  }

  public FakePermissionsService withPending(String username, Permission... permissions) {
    pending.put(username, permissionSet(permissions));
    return this;
  }

  public List<Write> writes() {
    return writes;
  }

  /** The USER record of {@code id}: its username and permissions, if any. */
  public Optional<TgUser> storedUser(long id) {
    StoredUser stored = users.get(id);
    return stored == null ? Optional.empty() : Optional.of(new TgUser(id, stored.username()));
  }

  public Set<Permission> permissionsOf(long id) {
    StoredUser stored = users.get(id);
    return stored == null ? Set.of() : stored.permissions();
  }

  public Set<Permission> pendingPermissionsOf(String username) {
    return pending.getOrDefault(username, Set.of());
  }

  public boolean hasPending(String username) {
    return pending.containsKey(username);
  }

  @Override
  public Map<TgUser, List<Permission>> getUsers() {
    Map<TgUser, List<Permission>> result = new HashMap<>();
    users.forEach((id, stored) -> result.put(new TgUser(id, stored.username()),
                                             sorted(stored.permissions())));
    pending.forEach((username, permissions) -> result.put(new TgUser(null, username),
                                                          sorted(permissions)));
    return result;
  }

  @Override
  public Optional<TgUser> findByUsername(String username) {
    if (username == null) {
      return Optional.empty();
    }
    String wanted = (username.startsWith("@") ? username.substring(1) : username)
        .toLowerCase(Locale.ROOT);
    Optional<TgUser> user = users.entrySet().stream()
        .filter(entry -> entry.getValue().username() != null
                         && entry.getValue().username().toLowerCase(Locale.ROOT).equals(wanted))
        .map(entry -> new TgUser(entry.getKey(), entry.getValue().username()))
        .findFirst();
    if (user.isPresent()) {
      return user;
    }
    return pending.keySet().stream()
        .filter(name -> name.toLowerCase(Locale.ROOT).equals(wanted))
        .map(name -> new TgUser(null, name))
        .findFirst();
  }

  @Override
  public void updateUserPermissions(TgUser user, Set<Permission> permissions) {
    boolean delete = permissions == null || permissions.isEmpty();
    if (user.id() != null) {
      if (delete) {
        users.remove(user.id());
        writes.add(Write.deleteUser(user.id()));
        return;
      }
      StoredUser stored = users.get(user.id());
      String username = user.username() != null || stored == null
          ? user.username()
          : stored.username();
      users.put(user.id(), new StoredUser(username, Set.copyOf(permissions)));
      writes.add(new Write("USER", String.valueOf(user.id()), username, Set.copyOf(permissions)));
      return;
    }

    if (delete) {
      pending.remove(user.username());
      writes.add(Write.deletePending(user.username()));
      return;
    }
    pending.put(user.username(), Set.copyOf(permissions));
    writes.add(new Write("PENDING_USER", user.username(), null, Set.copyOf(permissions)));
  }

  private static Set<Permission> permissionSet(Permission... permissions) {
    return permissions.length == 0 ? Set.of() : Set.copyOf(EnumSet.of(permissions[0], permissions));
  }

  private static List<Permission> sorted(Set<Permission> permissions) {
    return permissions.stream().sorted().toList();
  }

}
