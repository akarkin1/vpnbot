package org.github.akarkin1.auth;

import org.github.akarkin1.util.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CachingPermissionsServiceTest {

  private static final Duration TTL = Duration.ofSeconds(300);
  private static final Map<String, List<Permission>> PERMISSIONS =
      Map.of("alice", List.of(Permission.RUN_NODES));
  private static final Map<String, List<Permission>> UPDATED =
      Map.of("alice", List.of(Permission.RUN_NODES), "bob", List.of(Permission.LIST_NODES));

  @Mock
  private PermissionsService delegate;

  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-10T10:00:00Z"));

  @Test
  @DisplayName("3 AC-A12: a second read within the TTL does not reach the delegate")
  void cachedWithinTtl() {
    when(delegate.getUserPermissions()).thenReturn(PERMISSIONS);
    CachingPermissionsService service = new CachingPermissionsService(delegate, TTL, clock);

    assertEquals(PERMISSIONS, service.getUserPermissions());
    clock.advance(TTL.minusMillis(1));
    assertEquals(PERMISSIONS, service.getUserPermissions());

    verify(delegate, times(1)).getUserPermissions();
  }

  @Test
  @DisplayName("3 AC-A12: a read after the TTL reaches the delegate again")
  void reloadedAfterTtl() {
    when(delegate.getUserPermissions()).thenReturn(PERMISSIONS, UPDATED);
    CachingPermissionsService service = new CachingPermissionsService(delegate, TTL, clock);

    assertEquals(PERMISSIONS, service.getUserPermissions());
    clock.advance(TTL.plusMillis(1));
    assertEquals(UPDATED, service.getUserPermissions());
    assertEquals(UPDATED, service.getUserPermissions());

    verify(delegate, times(2)).getUserPermissions();
  }

  @Test
  @DisplayName("3 AC-A12: TTL 0 delegates every read")
  void zeroTtlDelegatesEveryRead() {
    when(delegate.getUserPermissions()).thenReturn(PERMISSIONS);
    CachingPermissionsService service = new CachingPermissionsService(delegate, Duration.ZERO, clock);

    for (int i = 0; i < 3; i++) {
      assertEquals(PERMISSIONS, service.getUserPermissions());
    }

    verify(delegate, times(3)).getUserPermissions();
  }

  @Test
  @DisplayName("3 AC-A12: an update within the TTL is delegated and invalidates the cache")
  void updateInvalidates() {
    when(delegate.getUserPermissions()).thenReturn(PERMISSIONS, UPDATED);
    CachingPermissionsService service = new CachingPermissionsService(delegate, TTL, clock);

    assertEquals(PERMISSIONS, service.getUserPermissions());
    service.updateUserPermissions("bob", Set.of(Permission.LIST_NODES));
    assertEquals(UPDATED, service.getUserPermissions());

    InOrder order = inOrder(delegate);
    order.verify(delegate).getUserPermissions();
    order.verify(delegate).updateUserPermissions("bob", Set.of(Permission.LIST_NODES));
    order.verify(delegate).getUserPermissions();
  }

  @Test
  @DisplayName("3 AC-A12: the cache is invalidated before delegating, so a failed update is not hidden")
  void invalidatedBeforeDelegating() {
    when(delegate.getUserPermissions()).thenReturn(PERMISSIONS, UPDATED);
    doThrow(new IllegalStateException("write failed"))
        .when(delegate).updateUserPermissions("bob", Set.of(Permission.LIST_NODES));
    CachingPermissionsService service = new CachingPermissionsService(delegate, TTL, clock);

    assertEquals(PERMISSIONS, service.getUserPermissions());
    assertThrows(IllegalStateException.class,
                 () -> service.updateUserPermissions("bob", Set.of(Permission.LIST_NODES)));
    assertEquals(UPDATED, service.getUserPermissions());

    verify(delegate, times(2)).getUserPermissions();
  }

  @Test
  @DisplayName("3 AC-A12: deleteUser (interface default) goes through the invalidating update")
  void deleteUserInvalidates() {
    when(delegate.getUserPermissions()).thenReturn(UPDATED, PERMISSIONS);
    CachingPermissionsService service = new CachingPermissionsService(delegate, TTL, clock);

    assertEquals(UPDATED, service.getUserPermissions());
    service.deleteUser("bob");
    assertEquals(PERMISSIONS, service.getUserPermissions());

    verify(delegate).updateUserPermissions("bob", null);
    verify(delegate, times(2)).getUserPermissions();
  }

}
