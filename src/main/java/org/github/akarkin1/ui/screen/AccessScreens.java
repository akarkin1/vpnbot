package org.github.akarkin1.ui.screen;

import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.auth.AccessRequest;
import org.github.akarkin1.auth.UserRole;
import org.github.akarkin1.ui.UiAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.github.akarkin1.ui.screen.CommonButtons.MENU;
import static org.github.akarkin1.ui.screen.NodeFormat.orDash;

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

    Requester requester = requester(request);
    return new Screen("🙋 " + requester.template() + " ${ui.access.asks}", requester.params(),
                      keyboard);
  }

  /** The admin's message after granting: who and which roles, no buttons. */
  public Screen granted(AccessRequest request, Set<UserRole> roles) {
    Requester requester = requester(request);
    return new Screen("✅ " + requester.template() + " ${ui.access.granted-admin} "
                      + RoleFormat.names(roles),
                      requester.params(), List.of());
  }

  /** The admin's message after declining, no buttons. */
  public Screen declined(AccessRequest request) {
    Requester requester = requester(request);
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

  /**
   * {@code <b>First Last</b> (@username, id)}; without a last name just {@code <b>First</b>},
   * without a username {@code <b>First Last</b> (id)}.
   */
  private static Requester requester(AccessRequest request) {
    if (StringUtils.isBlank(request.username())) {
      return new Requester("<b>%s</b> (%s)", List.of(fullName(request), request.userId()));
    }
    return new Requester("<b>%s</b> (@%s, %s)",
                         List.of(fullName(request), request.username(), request.userId()));
  }

  private static String fullName(AccessRequest request) {
    String name = StringUtils.isBlank(request.lastName())
        ? request.firstName()
        : request.firstName() + " " + request.lastName();
    return orDash(name);
  }

  private record Requester(String template, List<Object> params) {
  }

}
