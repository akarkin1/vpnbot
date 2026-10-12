package org.github.akarkin1.ui.screen;

import org.github.akarkin1.auth.AccessRequest;
import org.github.akarkin1.auth.UserRole;
import org.github.akarkin1.ui.UiAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.github.akarkin1.ui.screen.CommonButtons.MENU;

/** The screens of the access request: the requester's side and the admins' message. */
public class AccessScreens {

  public Screen requested() {
    return new Screen("📨 ${ui.access.requested}", List.of(), List.of(List.of(MENU)));
  }

  public Screen alreadyRequested() {
    return new Screen("⏳ ${ui.access.already-requested}", List.of(), List.of(List.of(MENU)));
  }

  /**
   * The admin's message: who asks, the role checkboxes and Grant/Decline. Every button carries the
   * current selection.
   */
  public Screen adminRequest(AccessRequest request, Set<UserRole> selected) {
    long userId = request.userId();
    List<List<Button>> keyboard = new ArrayList<>(RoleFormat.toggleRows(
        selected, role -> UiAction.accessToggle(userId, selected, role)));
    keyboard.add(List.of(Button.action("✅ ${ui.button.grant}", UiAction.accessGrant(userId, selected)),
                         Button.action("✖ ${ui.button.decline}", UiAction.accessDecline(userId))));

    UserLine requester = requester(request);
    return new Screen("🙋 " + requester.template() + " ${ui.access.asks}", requester.params(),
                      keyboard);
  }

  /** The admin's message after granting: who and which roles, no buttons. */
  public Screen granted(AccessRequest request, Set<UserRole> roles) {
    UserLine requester = requester(request);
    return new Screen("✅ " + requester.template() + " ${ui.access.granted-admin} "
                      + RoleFormat.names(roles),
                      requester.params(), List.of());
  }

  /** The admin's message after declining, no buttons. */
  public Screen declined(AccessRequest request) {
    UserLine requester = requester(request);
    return new Screen("✖ " + requester.template() + " ${ui.access.declined-admin}",
                      requester.params(), List.of());
  }

  public Screen alreadyHandled() {
    return new Screen("ℹ️ ${ui.access.already-handled}", List.of(), List.of());
  }

  /** The requester's notification of a grant, naming the roles. */
  public Screen accessGranted(Set<UserRole> roles) {
    return new Screen("✅ ${ui.access.granted} " + RoleFormat.names(roles)
                      + ". ${ui.access.welcome}",
                      List.of(), List.of(List.of(MENU)));
  }

  /** The requester's notification of a decline, no buttons. */
  public Screen accessDeclined() {
    return new Screen("✖ ${ui.access.declined}", List.of(), List.of());
  }

  public Screen notAllowed() {
    return new Screen("⛔ ${ui.users.not-allowed}", List.of(), List.of(List.of(MENU)));
  }

  private static UserLine requester(AccessRequest request) {
    return UserLine.of(request.userId(), request.username(), request.firstName(),
                       request.lastName());
  }

}
