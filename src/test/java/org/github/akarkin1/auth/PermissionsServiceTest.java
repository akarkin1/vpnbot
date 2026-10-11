package org.github.akarkin1.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

/** The user writes of the former sign-up interface are folded into {@link PermissionsService} (cleanup §4.5). */
@ExtendWith(MockitoExtension.class)
class PermissionsServiceTest {

  private static final TgUser ALICE = new TgUser(42L, "alice");

  @Mock(answer = Answers.CALLS_REAL_METHODS)
  private PermissionsService service;

  @Test
  @DisplayName("cleanup §4.5: PermissionsService extends no other interface and declares updateUserPermissions, deleteUser and assignRolesToUser itself")
  void declaresTheUserWrites() throws NoSuchMethodException {
    Method update = PermissionsService.class.getDeclaredMethod("updateUserPermissions",
                                                               TgUser.class, Set.class);
    Method delete = PermissionsService.class.getDeclaredMethod("deleteUser", TgUser.class);
    Method assign = PermissionsService.class.getDeclaredMethod("assignRolesToUser",
                                                               TgUser.class, Set.class);

    assertAll(
        () -> assertEquals(0, PermissionsService.class.getInterfaces().length),
        () -> assertFalse(update.isDefault()),
        () -> assertTrue(delete.isDefault()),
        () -> assertTrue(assign.isDefault()));
  }

  @Test
  @DisplayName("cleanup §4.5: deleteUser writes null permissions (unchanged default)")
  void deleteUserWritesNull() {
    service.deleteUser(ALICE);

    verify(service).updateUserPermissions(ALICE, null);
  }

  @Test
  @DisplayName("cleanup §4.5: assignRolesToUser writes the union of the roles' permissions (unchanged default)")
  void assignRolesWritesUnion() {
    service.assignRolesToUser(ALICE, Set.of(UserRole.READ_ONLY, UserRole.USER_ADMIN));

    verify(service).updateUserPermissions(ALICE, EnumSet.of(Permission.SUPPORTED_REGIONS,
                                                            Permission.LIST_NODES,
                                                            Permission.USER_MANAGEMENT));
  }

}
