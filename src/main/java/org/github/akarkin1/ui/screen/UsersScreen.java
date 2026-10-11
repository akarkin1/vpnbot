package org.github.akarkin1.ui.screen;

import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.auth.RoleSet;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.ui.UiAction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.github.akarkin1.ui.screen.CommonButtons.MENU;
import static org.github.akarkin1.ui.screen.NodeFormat.escapePercent;

/** The Users screen: the list of known users, a user's card and the removal confirmation. */
public class UsersScreen {

  private static final Comparator<UserRow> BY_USERNAME_THEN_KEY = Comparator
      .comparing((UserRow row) -> row.user().username(),
                 Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
      .thenComparing(row -> row.user().key());
  private static final Button BACK = Button.action("◀ ${ui.button.users}", UiAction.users());

  /** One button per user, sorted by username then key: {@code @username · roles [· pending]}. */
  public Screen list(UsersModel model) {
    List<List<Button>> keyboard = new ArrayList<>();
    model.users().stream()
        .sorted(BY_USERNAME_THEN_KEY)
        .map(row -> List.of(Button.action(label(row), UiAction.user(row.user().key()))))
        .forEach(keyboard::add);
    keyboard.add(List.of(MENU));
    return new Screen("👥 <b>${ui.users.title}</b>", List.of(), keyboard);
  }

  /** The card without the Remove button. */
  public Screen user(UserRow row) {
    return user(row, false);
  }

  /**
   * The card: name, id (or pending) and roles; for an editable user the role toggles and, when
   * {@code canRemove}, the Remove button; always the back button.
   */
  // Pre-registration by username ended with 2026.10. Remove once PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
  @SuppressWarnings("removal")
  public Screen user(UserRow row, boolean canRemove) {
    TgUser user = row.user();
    String roles = RoleFormat.describe(row.permissions());
    String template;
    List<Object> params;
    if (StringUtils.isBlank(user.username())) {
      template = "👤 <b>%s</b>\n" + roles;
      params = List.of(String.valueOf(user.id()));
    } else if (user.pending()) {
      template = "👤 <b>@%s</b> (${ui.users.pending})\n" + roles;
      params = List.of(user.username());
    } else {
      template = "👤 <b>@%s</b> (%s)\n" + roles;
      params = List.of(user.username(), String.valueOf(user.id()));
    }

    List<List<Button>> keyboard = new ArrayList<>();
    if (row.editable()) {
      keyboard.addAll(RoleFormat.toggleRows(RoleSet.coveredBy(row.permissions()),
                                            role -> UiAction.toggleRole(user.key(), role)));
      if (canRemove) {
        keyboard.add(List.of(Button.action("🗑 ${ui.button.remove}",
                                           UiAction.deleteUser(user.key()))));
      }
    }
    keyboard.add(List.of(BACK));
    return new Screen(template, params, keyboard);
  }

  public Screen confirmDelete(UserRow row) {
    String key = row.user().key();
    Button yes = Button.action("✅ ${ui.button.confirm-delete}", UiAction.confirmDeleteUser(key));
    Button cancel = Button.action("✖ ${ui.button.cancel}", UiAction.user(key));
    return new Screen("❓ ${ui.users.confirm-delete} <b>%s</b>?", List.of(displayName(row.user())),
                      List.of(List.of(yes, cancel)));
  }

  public Screen notAllowed() {
    return new Screen("⛔ ${ui.users.not-allowed}", List.of(), List.of(List.of(MENU)));
  }

  // Pre-registration by username ended with 2026.10. Remove once PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
  @SuppressWarnings("removal")
  private static String label(UserRow row) {
    TgUser user = row.user();
    String label = escapePercent(displayName(user)) + " · " + RoleFormat.describe(row.permissions());
    return user.pending() ? label + " · ${ui.users.pending}" : label;
  }

  /** {@code @username}, or the id without a username. */
  private static String displayName(TgUser user) {
    return StringUtils.isBlank(user.username()) ? String.valueOf(user.id()) : "@" + user.username();
  }

}
