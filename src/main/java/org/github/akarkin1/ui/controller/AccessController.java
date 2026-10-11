package org.github.akarkin1.ui.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.AccessRequest;
import org.github.akarkin1.auth.AccessRequestService;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.PermissionsService;
import org.github.akarkin1.auth.RoleSet;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.auth.UserRole;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.AccessScreens;
import org.github.akarkin1.ui.screen.Screen;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Access requests: a user without access asks, every admin gets a message with role checkboxes
 * and Grant/Decline, the first decision wins and the requester is notified.
 */
@Log4j2
@RequiredArgsConstructor
public class AccessController {

  private final AccessRequestService accessRequests;
  private final PermissionsService permissionsService;
  private final Authorizer authorizer;
  private final HomeController homeController;
  private final UiMessenger messenger;
  private final AccessScreens accessScreens;
  private final Clock clock;

  /** The requester's tap on Request access. */
  public void request(UiContext context, Integer messageId) {
    TgUser user = context.user();
    if (user.id() == null || UserAccess.hasAccess(authorizer, user)) {
      homeController.refreshHome(context, messageId);
      return;
    }
    if (accessRequests.find(user.id()).isPresent()) {
      messenger.edit(context, messageId, accessScreens.alreadyRequested());
      return;
    }

    AccessRequest request = new AccessRequest(user.id(), context.username(), context.firstName(),
                                              context.languageCode(), clock.millis());
    accessRequests.create(request);
    messenger.edit(context, messageId, accessScreens.requested());
    notifyAdmins(request);
  }

  /** An admin's tap on a role checkbox: redraws the message with the role flipped, writes nothing. */
  public void toggle(UiContext context, Integer messageId, long userId, int mask, UserRole role) {
    if (!UserAccess.canManageUsers(authorizer, context.user())) {
      messenger.edit(context, messageId, accessScreens.notAllowed());
      return;
    }
    Optional<AccessRequest> request = accessRequests.find(userId);
    if (request.isEmpty()) {
      messenger.edit(context, messageId, accessScreens.alreadyHandled());
      return;
    }

    Set<UserRole> selected = EnumSet.noneOf(UserRole.class);
    selected.addAll(RoleSet.decode(mask));
    if (!selected.remove(role)) {
      selected.add(role);
    }
    messenger.edit(context, messageId, accessScreens.adminRequest(request.get(), selected));
  }

  /**
   * An admin's Grant (with the selected roles) or Decline (empty): assigns the roles, deletes the
   * request and notifies the requester. An empty role set changes nothing.
   */
  public void decide(UiContext context, Integer messageId, long userId,
                     Optional<Set<UserRole>> roles) {
    if (roles.isPresent() && roles.get().isEmpty()) {
      return;
    }
    if (!UserAccess.canManageUsers(authorizer, context.user())) {
      messenger.edit(context, messageId, accessScreens.notAllowed());
      return;
    }
    Optional<AccessRequest> found = accessRequests.find(userId);
    if (found.isEmpty()) {
      messenger.edit(context, messageId, accessScreens.alreadyHandled());
      return;
    }

    AccessRequest request = found.get();
    roles.ifPresent(granted -> permissionsService.assignRolesToUser(request.user(), granted));
    accessRequests.delete(userId);
    log.info("Access request of user {} {} by user {}", userId,
             roles.map(granted -> "granted " + granted).orElse("declined"), context.userId());

    messenger.edit(context, messageId, roles
        .map(granted -> accessScreens.granted(request, granted))
        .orElseGet(() -> accessScreens.declined(request)));
    notifyRequester(request, roles
        .map(accessScreens::accessGranted)
        .orElseGet(accessScreens::accessDeclined));
  }

  /** Sends the request to every admin; a private chat's id equals the user id (D-4). */
  private void notifyAdmins(AccessRequest request) {
    List<TgUser> admins = permissionsService.getUsers().entrySet().stream()
        .filter(entry -> UserAccess.isUserAdmin(entry.getValue()))
        .map(Map.Entry::getKey)
        .filter(admin -> !admin.pending())
        .toList();
    if (admins.isEmpty()) {
      log.warn("No admin to notify of the access request of user {}", request.userId());
      return;
    }

    Screen screen = accessScreens.adminRequest(request, EnumSet.noneOf(UserRole.class));
    for (TgUser admin : admins) {
      // the user record has no language: the bot's default
      UiContext adminChat = new UiContext(admin.id(), admin.id(), admin.username(), null, null);
      try {
        messenger.send(adminChat, screen);
      } catch (RuntimeException e) {
        log.error("Failed to notify admin {} of the access request of user {}", admin.id(),
                  request.userId(), e);
      }
    }
  }

  private void notifyRequester(AccessRequest request, Screen screen) {
    UiContext requester = new UiContext(request.userId(), request.userId(), request.username(),
                                        request.firstName(), request.languageCode());
    try {
      messenger.send(requester, screen);
    } catch (RuntimeException e) {
      log.error("Failed to notify user {} of the access decision", request.userId(), e);
    }
  }

}
