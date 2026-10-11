package org.github.akarkin1.ui.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.PermissionsService;
import org.github.akarkin1.auth.RoleSet;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.auth.UserRole;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.UserRow;
import org.github.akarkin1.ui.screen.UsersModel;
import org.github.akarkin1.ui.screen.UsersScreen;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Users screen for admins ({@code USER_MANAGEMENT} or root): the list, a user's card with role
 * toggles and removal ({@code DELETE_USERS} or root). The caller and root users are never edited,
 * even on a forged callback. Users are referenced by {@link TgUser#key()}.
 */
@Log4j2
@RequiredArgsConstructor
public class UsersController {

  private final PermissionsService permissionsService;
  private final Authorizer authorizer;
  private final UiMessenger messenger;
  private final UsersScreen usersScreen;

  public void show(UiContext context, Integer messageId) {
    if (!checkCanManage(context, messageId)) {
      return;
    }
    showList(context, messageId);
  }

  public void showUser(UiContext context, Integer messageId, String key) {
    if (!checkCanManage(context, messageId)) {
      return;
    }
    findRow(context, key).ifPresentOrElse(
        row -> showCard(context, messageId, row),
        () -> showList(context, messageId));
  }

  /**
   * Flips the role; the user gets the union of the ticked roles' permissions. Unticking the last
   * role asks to remove the user instead (only callers who may remove users; others keep the card).
   */
  public void toggleRole(UiContext context, Integer messageId, String key, UserRole role) {
    if (!checkCanManage(context, messageId)) {
      return;
    }
    Optional<UserRow> found = findRow(context, key);
    if (found.isEmpty()) {
      showList(context, messageId);
      return;
    }
    UserRow row = found.get();
    if (!row.editable()) {
      showCard(context, messageId, row);
      return;
    }

    Set<UserRole> ticked = EnumSet.noneOf(UserRole.class);
    ticked.addAll(RoleSet.coveredBy(row.permissions()));
    if (!ticked.remove(role)) {
      ticked.add(role);
    }
    if (ticked.isEmpty()) {
      if (UserAccess.canDeleteUsers(authorizer, context.user())) {
        messenger.edit(context, messageId, usersScreen.confirmDelete(row));
      } else {
        showCard(context, messageId, row);
      }
      return;
    }

    permissionsService.assignRolesToUser(row.user(), ticked);
    log.info("User {} set the roles of user {} to {}", context.userId(), key, ticked);
    findRow(context, key).ifPresentOrElse(
        updated -> showCard(context, messageId, updated),
        () -> showList(context, messageId));
  }

  /** The 🗑 tap: asks for confirmation. */
  public void confirmDelete(UiContext context, Integer messageId, String key) {
    Optional<UserRow> row = findRemovable(context, messageId, key);
    row.ifPresent(target -> messenger.edit(context, messageId, usersScreen.confirmDelete(target)));
  }

  /** The confirmed removal: deletes the user's record and shows the list. */
  public void delete(UiContext context, Integer messageId, String key) {
    Optional<UserRow> row = findRemovable(context, messageId, key);
    if (row.isEmpty()) {
      return;
    }
    permissionsService.deleteUser(row.get().user());
    log.info("User {} removed user {}", context.userId(), key);
    showList(context, messageId);
  }

  /**
   * The user to remove; edits the message and returns empty when the caller may not remove users,
   * the user is gone, or it is the caller or a root user.
   */
  private Optional<UserRow> findRemovable(UiContext context, Integer messageId, String key) {
    if (!checkCanManage(context, messageId)) {
      return Optional.empty();
    }
    if (!UserAccess.canDeleteUsers(authorizer, context.user())) {
      messenger.edit(context, messageId, usersScreen.notAllowed());
      return Optional.empty();
    }
    Optional<UserRow> row = findRow(context, key);
    if (row.isEmpty()) {
      showList(context, messageId);
      return Optional.empty();
    }
    if (!row.get().editable()) {
      showCard(context, messageId, row.get());
      return Optional.empty();
    }
    return row;
  }

  private boolean checkCanManage(UiContext context, Integer messageId) {
    if (UserAccess.canManageUsers(authorizer, context.user())) {
      return true;
    }
    messenger.edit(context, messageId, usersScreen.notAllowed());
    return false;
  }

  private void showList(UiContext context, Integer messageId) {
    List<UserRow> rows = permissionsService.getUsers().entrySet().stream()
        .map(entry -> row(context, entry))
        .toList();
    messenger.edit(context, messageId, usersScreen.list(new UsersModel(rows)));
  }

  private void showCard(UiContext context, Integer messageId, UserRow row) {
    boolean canRemove = UserAccess.canDeleteUsers(authorizer, context.user());
    messenger.edit(context, messageId, usersScreen.user(row, canRemove));
  }

  private Optional<UserRow> findRow(UiContext context, String key) {
    return permissionsService.getUsers().entrySet().stream()
        .filter(entry -> entry.getKey().key().equals(key))
        .findFirst()
        .map(entry -> row(context, entry));
  }

  /** Editable = neither root nor the caller. */
  private static UserRow row(UiContext context, Map.Entry<TgUser, List<Permission>> entry) {
    boolean root = entry.getValue().contains(Permission.ROOT_ACCESS);
    boolean self = entry.getKey().equals(context.user());
    return new UserRow(entry.getKey(), entry.getValue(), !root && !self);
  }

}
