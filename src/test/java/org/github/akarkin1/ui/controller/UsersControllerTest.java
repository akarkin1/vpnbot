package org.github.akarkin1.ui.controller;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.FakePermissionsService;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.auth.UserRole;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.Button;
import org.github.akarkin1.ui.screen.Screen;
import org.github.akarkin1.ui.screen.UserRow;
import org.github.akarkin1.ui.screen.UsersModel;
import org.github.akarkin1.ui.screen.UsersScreen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * §4.4. Users: root (ROOT_ACCESS), admin (USER_MANAGEMENT), deleter (USER_MANAGEMENT +
 * DELETE_USERS), reader (read-only), alice (node admin) and carol (pending, read-only).
 * Permissions live in a {@link FakePermissionsService}; the mocked {@link Authorizer} answers
 * from the same table (root implies every permission, as {@code WhiteListAuthorizer} does).
 */
@ExtendWith(MockitoExtension.class)
class UsersControllerTest {

  private static final Integer MESSAGE_ID = 42;

  private static final UiContext ROOT = new UiContext(1L, 1L, "root", "Root", "en");
  private static final UiContext ADMIN = new UiContext(10L, 10L, "admin", "Admin", "en");
  private static final UiContext DELETER = new UiContext(11L, 11L, "deleter", "Deleter", "en");
  private static final UiContext READER = new UiContext(20L, 20L, "reader", "Reader", "en");
  private static final TgUser ALICE = new TgUser(30L, "alice");
  private static final TgUser CAROL = new TgUser(null, "carol");

  private static final Set<Permission> NODE_ADMIN =
      Set.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES, Permission.RUN_NODES);
  private static final Set<Permission> READ_ONLY = Set.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES);

  private static final Screen LIST = screen("list");
  private static final Screen CARD = screen("card");
  private static final Screen CONFIRM = screen("confirmDelete");
  private static final Screen NOT_ALLOWED = screen("notAllowed");

  @Mock
  private Authorizer authorizer;
  @Mock
  private UiMessenger messenger;
  @Mock
  private UsersScreen usersScreen;

  private FakePermissionsService permissions;
  private UsersController controller;

  @BeforeEach
  void setUp() {
    permissions = new FakePermissionsService()
        .with(ROOT.user(), Permission.ROOT_ACCESS)
        .with(ADMIN.user(), Permission.USER_MANAGEMENT)
        .with(DELETER.user(), Permission.USER_MANAGEMENT, Permission.DELETE_USERS)
        .with(READER.user(), Permission.SUPPORTED_REGIONS, Permission.LIST_NODES)
        .with(ALICE, Permission.SUPPORTED_REGIONS, Permission.LIST_NODES, Permission.RUN_NODES)
        .with(CAROL, Permission.SUPPORTED_REGIONS, Permission.LIST_NODES);
    lenient().when(authorizer.hasPermission(any(), any())).thenAnswer(invocation -> {
      Set<Permission> granted = permissions.permissionsOf(invocation.getArgument(0));
      return granted.contains(Permission.ROOT_ACCESS) || granted.contains(invocation.getArgument(1));
    });
    lenient().when(usersScreen.list(any())).thenReturn(LIST);
    lenient().when(usersScreen.user(any(), anyBoolean())).thenReturn(CARD);
    lenient().when(usersScreen.confirmDelete(any())).thenReturn(CONFIRM);
    lenient().when(usersScreen.notAllowed()).thenReturn(NOT_ALLOWED);
    controller = new UsersController(permissions, authorizer, messenger, usersScreen);
  }

  // --- list ------------------------------------------------------------------------------------

  @Test
  @DisplayName("4 AC-6: an admin sees every user (pending ones too); the admin's own row and root's are not editable")
  void showByAdmin() {
    controller.show(ADMIN, MESSAGE_ID);

    verify(messenger).edit(ADMIN, MESSAGE_ID, LIST);
    Map<String, UserRow> rows = listedRows();
    assertEquals(Set.of("1", "10", "11", "20", "30", "@carol"), rows.keySet());
    assertFalse(rows.get("1").editable(), "root");
    assertFalse(rows.get("10").editable(), "the caller");
    assertTrue(rows.get("11").editable());
    assertTrue(rows.get("20").editable());
    assertTrue(rows.get("30").editable());
    assertTrue(rows.get("@carol").editable());
    assertTrue(rows.get("@carol").user().pending());
    assertEquals(NODE_ADMIN, Set.copyOf(rows.get("30").permissions()));
    assertEquals(READ_ONLY, Set.copyOf(rows.get("@carol").permissions()));
  }

  @Test
  @DisplayName("4 AC-6: root sees the list; its own row is not editable")
  void showByRoot() {
    controller.show(ROOT, MESSAGE_ID);

    Map<String, UserRow> rows = listedRows();
    assertFalse(rows.get("1").editable());
    assertTrue(rows.get("10").editable());
  }

  @Test
  @DisplayName("4 AC-6: a user without USER_MANAGEMENT/root gets notAllowed, no list")
  void showNotAllowed() {
    controller.show(READER, MESSAGE_ID);

    verify(messenger).edit(READER, MESSAGE_ID, NOT_ALLOWED);
    verify(usersScreen, never()).list(any());
  }

  @Test
  @DisplayName("4 AC-6: the list has one button per user, sorted by username then key, then the menu (real screen)")
  void listSorted() {
    permissions.with(new TgUser(null, "alice"), Permission.USER_MANAGEMENT);
    UsersController withRealScreen = new UsersController(permissions, authorizer, messenger,
                                                         new UsersScreen());

    withRealScreen.show(ADMIN, MESSAGE_ID);

    ArgumentCaptor<Screen> screen = ArgumentCaptor.forClass(Screen.class);
    verify(messenger).edit(eq(ADMIN), eq(MESSAGE_ID), screen.capture());
    List<String> data = screen.getValue().keyboard().stream()
        .flatMap(List::stream)
        .map(Button::callbackData)
        .toList();
    assertEquals(List.of("USER:10", "USER:30", "USER:@alice", "USER:@carol", "USER:11", "USER:20",
                         "USER:1", "HOME"),
                 data);
  }

  // --- user card -------------------------------------------------------------------------------

  @Test
  @DisplayName("4 AC-6: an admin without DELETE_USERS gets an editable card without Remove")
  void showUserByAdmin() {
    controller.showUser(ADMIN, MESSAGE_ID, "30");

    verify(messenger).edit(ADMIN, MESSAGE_ID, CARD);
    UserRow row = shownRow(false);
    assertEquals(ALICE, row.user());
    assertEquals(NODE_ADMIN, Set.copyOf(row.permissions()));
    assertTrue(row.editable());
  }

  @Test
  @DisplayName("4 AC-6, D-11: an admin with DELETE_USERS and root get the card with Remove")
  void showUserWithRemove() {
    controller.showUser(DELETER, MESSAGE_ID, "30");
    controller.showUser(ROOT, MESSAGE_ID, "@carol");

    verify(usersScreen).user(argRow(ALICE, true), eq(true));
    verify(usersScreen).user(argRow(CAROL, true), eq(true));
  }

  @Test
  @DisplayName("4 AC-6, D-10: the caller's own card and root's card are not editable")
  void showUserNotEditable() {
    controller.showUser(ADMIN, MESSAGE_ID, "10");
    controller.showUser(DELETER, MESSAGE_ID, "1");
    controller.showUser(ROOT, MESSAGE_ID, "1");

    verify(usersScreen).user(argRow(ADMIN.user(), false), anyBoolean());
    verify(usersScreen, times(2)).user(argRow(ROOT.user(), false), anyBoolean());
    verify(usersScreen, never()).user(argThat(UserRow::editable), anyBoolean());
  }

  @Test
  @DisplayName("4 AC-6: a pending user's card is found by its @username key")
  void showPendingUser() {
    controller.showUser(ADMIN, MESSAGE_ID, "@carol");

    UserRow row = shownRow(false);
    assertTrue(row.user().pending());
    assertEquals("carol", row.user().username());
    assertEquals(READ_ONLY, Set.copyOf(row.permissions()));
  }

  @Test
  @DisplayName("4 AC-6: a user without USER_MANAGEMENT/root gets notAllowed, no card")
  void showUserNotAllowed() {
    controller.showUser(READER, MESSAGE_ID, "30");

    verify(messenger).edit(READER, MESSAGE_ID, NOT_ALLOWED);
    verify(usersScreen, never()).user(any(), anyBoolean());
  }

  @Test
  @DisplayName("4 AC-6: an unknown key (user removed meanwhile) shows no card and writes nothing")
  void showUnknownUser() {
    assertDoesNotThrow(() -> controller.showUser(ADMIN, MESSAGE_ID, "999"));

    verify(usersScreen, never()).user(any(), anyBoolean());
    assertEquals(0, permissions.writes());
  }

  // --- toggle role -----------------------------------------------------------------------------

  @Test
  @DisplayName("4 AC-6, D-7: ticking a role replaces the permissions with the union of the ticked roles and shows the card again")
  void toggleRoleOn() {
    controller.toggleRole(ADMIN, MESSAGE_ID, "30", UserRole.USER_ADMIN);

    Set<Permission> expected = Set.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES,
                                      Permission.RUN_NODES, Permission.USER_MANAGEMENT);
    assertEquals(expected, permissions.permissionsOf(ALICE));
    assertEquals("alice", permissions.storedKey(ALICE).username());
    verify(messenger).edit(ADMIN, MESSAGE_ID, CARD);
    assertEquals(expected, Set.copyOf(shownRow(false).permissions()));
  }

  @Test
  @DisplayName("4 AC-6: unticking node admin keeps read-only (still ticked: its permissions are a subset)")
  void toggleRoleOff() {
    controller.toggleRole(ADMIN, MESSAGE_ID, "30", UserRole.NODE_ADMIN);

    assertEquals(READ_ONLY, permissions.permissionsOf(ALICE));
    verify(messenger).edit(ADMIN, MESSAGE_ID, CARD);
  }

  @Test
  @DisplayName("4 AC-6: the roles of a pending user are edited under its username")
  void toggleRolePendingUser() {
    controller.toggleRole(DELETER, MESSAGE_ID, "@carol", UserRole.NODE_ADMIN);

    assertEquals(NODE_ADMIN, permissions.permissionsOf(CAROL));
    assertNotNull(permissions.storedKey(CAROL));
    assertTrue(permissions.storedKey(CAROL).pending());
  }

  @Test
  @DisplayName("4 AC-6: unticking the last role asks for confirmation and writes nothing")
  void toggleLastRoleAsksToConfirm() {
    controller.toggleRole(ADMIN, MESSAGE_ID, "@carol", UserRole.READ_ONLY);

    verify(usersScreen).confirmDelete(argRow(CAROL, true));
    verify(messenger).edit(ADMIN, MESSAGE_ID, CONFIRM);
    assertEquals(0, permissions.writes());
    assertEquals(READ_ONLY, permissions.permissionsOf(CAROL));
  }

  @Test
  @DisplayName("4 AC-6: a toggle on the caller's own card is refused even on a forged callback")
  void toggleSelfRefused() {
    controller.toggleRole(ADMIN, MESSAGE_ID, "10", UserRole.NODE_ADMIN);

    assertEquals(0, permissions.writes());
    assertEquals(Set.of(Permission.USER_MANAGEMENT), permissions.permissionsOf(ADMIN.user()));
    verify(usersScreen, never()).confirmDelete(any());
  }

  @Test
  @DisplayName("4 AC-6, D-10: a toggle on root is refused even on a forged callback")
  void toggleRootRefused() {
    controller.toggleRole(DELETER, MESSAGE_ID, "1", UserRole.NODE_ADMIN);

    assertEquals(0, permissions.writes());
    assertEquals(Set.of(Permission.ROOT_ACCESS), permissions.permissionsOf(ROOT.user()));
    verify(usersScreen, never()).confirmDelete(any());
  }

  @Test
  @DisplayName("4 AC-6: a toggle by a user without USER_MANAGEMENT/root shows notAllowed and writes nothing")
  void toggleNotAllowed() {
    controller.toggleRole(READER, MESSAGE_ID, "30", UserRole.USER_ADMIN);

    verify(messenger).edit(READER, MESSAGE_ID, NOT_ALLOWED);
    assertEquals(0, permissions.writes());
  }

  // --- delete ----------------------------------------------------------------------------------

  @Test
  @DisplayName("4 AC-6: 🗑 shows the confirmation for the user")
  void confirmDelete() {
    controller.confirmDelete(DELETER, MESSAGE_ID, "30");

    verify(usersScreen).confirmDelete(argRow(ALICE, true));
    verify(messenger).edit(DELETER, MESSAGE_ID, CONFIRM);
    assertEquals(0, permissions.writes());
  }

  @Test
  @DisplayName("4 AC-6: 🗑 by a user without USER_MANAGEMENT/root shows notAllowed")
  void confirmDeleteNotAllowed() {
    controller.confirmDelete(READER, MESSAGE_ID, "30");

    verify(messenger).edit(READER, MESSAGE_ID, NOT_ALLOWED);
    verify(usersScreen, never()).confirmDelete(any());
  }

  @Test
  @DisplayName("4 AC-6: confirm by an admin with DELETE_USERS removes the user and shows the list without it")
  void deleteByDeleter() {
    controller.delete(DELETER, MESSAGE_ID, "30");

    assertNull(permissions.storedKey(ALICE));
    verify(messenger).edit(DELETER, MESSAGE_ID, LIST);
    assertFalse(listedRows().containsKey("30"));
  }

  @Test
  @DisplayName("4 AC-6: root removes a pending user by its @username key")
  void deletePendingByRoot() {
    controller.delete(ROOT, MESSAGE_ID, "@carol");

    assertNull(permissions.storedKey(CAROL));
    verify(messenger).edit(ROOT, MESSAGE_ID, LIST);
  }

  @Test
  @DisplayName("4 AC-6, D-11: confirm by an admin without DELETE_USERS is refused")
  void deleteWithoutDeleteUsersRefused() {
    controller.delete(ADMIN, MESSAGE_ID, "30");

    assertEquals(NODE_ADMIN, permissions.permissionsOf(ALICE));
    assertEquals(0, permissions.writes());
    verify(messenger).edit(ADMIN, MESSAGE_ID, NOT_ALLOWED);
  }

  @Test
  @DisplayName("4 AC-6: removing oneself or root is refused even on a forged callback")
  void deleteSelfOrRootRefused() {
    controller.delete(ROOT, MESSAGE_ID, "1");
    controller.delete(DELETER, MESSAGE_ID, "1");
    controller.delete(DELETER, MESSAGE_ID, "11");

    assertEquals(0, permissions.writes());
    assertEquals(Set.of(Permission.ROOT_ACCESS), permissions.permissionsOf(ROOT.user()));
    assertEquals(Set.of(Permission.USER_MANAGEMENT, Permission.DELETE_USERS),
                 permissions.permissionsOf(DELETER.user()));
  }

  @Test
  @DisplayName("4 AC-6: confirm by a user without USER_MANAGEMENT/root is refused")
  void deleteNotAllowed() {
    controller.delete(READER, MESSAGE_ID, "30");

    assertEquals(0, permissions.writes());
    verify(messenger).edit(READER, MESSAGE_ID, NOT_ALLOWED);
  }

  // --- helpers ---------------------------------------------------------------------------------

  /** The rows of the (last) listed model by user key. */
  private Map<String, UserRow> listedRows() {
    ArgumentCaptor<UsersModel> captor = ArgumentCaptor.forClass(UsersModel.class);
    verify(usersScreen).list(captor.capture());
    return captor.getValue().users().stream()
        .collect(Collectors.toMap(row -> row.user().key(), row -> row));
  }

  /** The row of the (only) card shown, checking the Remove flag. */
  private UserRow shownRow(boolean canDelete) {
    ArgumentCaptor<UserRow> captor = ArgumentCaptor.forClass(UserRow.class);
    verify(usersScreen).user(captor.capture(), eq(canDelete));
    return captor.getValue();
  }

  private static UserRow argRow(TgUser user, boolean editable) {
    return argThat(
        row -> row != null && row.user().equals(user) && row.editable() == editable);
  }

  private static Screen screen(String name) {
    return new Screen(name, List.of(), List.of());
  }

}
