package org.github.akarkin1.ui.screen;

import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.TgUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.github.akarkin1.ui.screen.ScreenTestSupport.MENU;
import static org.github.akarkin1.ui.screen.ScreenTestSupport.buttons;
import static org.github.akarkin1.ui.screen.ScreenTestSupport.render;
import static org.github.akarkin1.ui.screen.ScreenTestSupport.renderedLabels;
import static org.github.akarkin1.ui.screen.ScreenTestSupport.withParams;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** §4.4. Role descriptions are whatever {@link RoleFormat#describe} returns (see RoleFormatTest). */
class UsersScreenTest {

  private static final List<Permission> NODE_ADMIN_PERMISSIONS =
      List.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES, Permission.RUN_NODES);
  private static final List<Permission> READ_ONLY_PERMISSIONS =
      List.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES);

  private static final UserRow ALICE =
      new UserRow(new TgUser(1L, "alice"), NODE_ADMIN_PERMISSIONS, true);
  private static final UserRow BOB_PENDING =
      new UserRow(new TgUser(null, "bob"), READ_ONLY_PERMISSIONS, true);
  private static final UserRow ZED_ROOT =
      new UserRow(new TgUser(3L, "zed"), List.of(Permission.ROOT_ACCESS), false);
  private static final UserRow ID_ONLY =
      new UserRow(new TgUser(42L, null), List.of(Permission.USER_MANAGEMENT), true);

  private static final Button BACK = new Button("◀ ${ui.button.users}", "USERS", null);

  private final UsersScreen screen = new UsersScreen();

  // --- list --------------------------------------------------------------------------------------

  @Test
  @DisplayName("4 AC-6, AC-7: list shows the title, one button per user (USER:<key>) sorted by username, then the menu")
  void list() {
    Screen list = screen.list(new UsersModel(List.of(ALICE, BOB_PENDING, ZED_ROOT)));

    assertTrue(list.template().contains("${ui.users.title}"), list.template());
    List<List<Button>> keyboard = list.keyboard();
    assertEquals(List.of(MENU), keyboard.getLast());
    List<Button> userButtons = keyboard.subList(0, keyboard.size() - 1).stream()
        .flatMap(List::stream)
        .toList();
    assertEquals(List.of(
                     new Button("@alice · " + RoleFormat.describe(NODE_ADMIN_PERMISSIONS), "USER:1", null),
                     new Button("@bob · " + RoleFormat.describe(READ_ONLY_PERMISSIONS)
                                + " · ${ui.users.pending}", "USER:@bob", null),
                     new Button("@zed · " + RoleFormat.describe(List.of(Permission.ROOT_ACCESS)),
                                "USER:3", null)),
                 userButtons);
  }

  @Test
  @DisplayName("4 AC-7: a user without a username is labelled with the id")
  void listUserWithoutUsername() {
    Screen list = screen.list(new UsersModel(List.of(ID_ONLY)));

    assertEquals(new Button("42 · " + RoleFormat.describe(List.of(Permission.USER_MANAGEMENT)),
                            "USER:42", null),
                 buttons(list).getFirst());
  }

  @Test
  @DisplayName("4 AC-7: an empty list shows only the menu")
  void emptyList() {
    Screen list = screen.list(new UsersModel(List.of()));

    assertEquals(List.of(List.of(MENU)), list.keyboard());
  }

  @Test
  @DisplayName("4 AC-7: list labels render translated, and a % in a username survives the label formatting")
  void listLabelsRendered() {
    UserRow percent = new UserRow(new TgUser(null, "50%off"), READ_ONLY_PERMISSIONS, true);

    List<String> labels = renderedLabels(screen.list(new UsersModel(List.of(ALICE, percent))), "en");

    String alice = labels.stream().filter(l -> l.startsWith("@alice · ")).findFirst().orElseThrow();
    String pending = labels.stream().filter(l -> l.startsWith("@50%off · ")).findFirst().orElseThrow();
    assertTrue(alice.contains("Node admin"), alice);
    assertTrue(pending.endsWith(" · pending"), pending);
    labels.forEach(label -> assertFalse(label.contains("${"), label));
  }

  // --- user card -----------------------------------------------------------------------------

  @Test
  @DisplayName("4 AC-7: the card reads \"👤 <b>@username</b> (id)\" and the roles on the next line")
  void cardText() {
    Screen card = screen.user(ALICE, false);

    assertEquals("👤 <b>@alice</b> (1)\n" + RoleFormat.describe(NODE_ADMIN_PERMISSIONS), withParams(card));
  }

  @Test
  @DisplayName("4 AC-7: the card of a pending user shows ui.users.pending instead of the id")
  void cardTextPending() {
    Screen card = screen.user(BOB_PENDING, false);

    assertEquals("👤 <b>@bob</b> (${ui.users.pending})\n" + RoleFormat.describe(READ_ONLY_PERMISSIONS),
                 withParams(card));
  }

  @Test
  @DisplayName("4 AC-7: the card renders translated roles in RU without leftover keys")
  void cardRendered() {
    String text = render(screen.user(ALICE, true), "ru").text();

    assertTrue(text.contains("<b>@alice</b>"), text);
    assertTrue(text.contains("Администратор VPN-серверов"), text);
    assertFalse(text.contains("${"), text);
  }

  @Test
  @DisplayName("4 AC-7: the username is HTML-escaped on the card (a param, not part of the template)")
  void cardEscapesUsername() {
    UserRow row = new UserRow(new TgUser(5L, "<b>x&y</b>"), READ_ONLY_PERMISSIONS, true);

    Screen card = screen.user(row, true);

    assertFalse(card.template().contains("x&y"), card.template());
    String text = render(card, "en").text();
    assertTrue(text.contains("&lt;b&gt;x&amp;y&lt;/b&gt;"), text);
  }

  @Test
  @DisplayName("4 AC-7: a user without a username shows no @null on the card")
  void cardWithoutUsername() {
    String text = render(screen.user(ID_ONLY, true), "en").text();

    assertTrue(text.contains("42"), text);
    assertFalse(text.contains("null"), text);
  }

  @Test
  @DisplayName("4 AC-6, AC-7: an editable card: role toggles (ticked = role's permissions ⊆ user's), Remove, back to Users")
  void editableCardWithRemove() {
    Screen card = screen.user(ALICE, true);

    List<List<Button>> keyboard = card.keyboard();
    assertEquals(List.of(new Button("✅ ${ui.role.node-admin}", "USER_ROLE:1:NODE_ADMIN", null),
                         new Button("✅ ${ui.role.read-only}", "USER_ROLE:1:READ_ONLY", null)),
                 keyboard.get(0));
    assertEquals(List.of(new Button("☐ ${ui.role.user-admin}", "USER_ROLE:1:USER_ADMIN", null)),
                 keyboard.get(1));
    assertEquals(List.of(new Button("🗑 ${ui.button.remove}", "USER_DEL:1", null), BACK),
                 keyboard.subList(2, keyboard.size()).stream().flatMap(List::stream).toList());
  }

  @Test
  @DisplayName("4 AC-6: without DELETE_USERS/root the editable card has the toggles and the back button but no Remove")
  void editableCardWithoutRemove() {
    Screen card = screen.user(ALICE, false);

    List<List<Button>> keyboard = card.keyboard();
    assertEquals(3, buttons(card).stream().filter(b -> b.callbackData().startsWith("USER_ROLE:")).count());
    assertEquals(List.of(BACK),
                 keyboard.subList(2, keyboard.size()).stream().flatMap(List::stream).toList());
  }

  @Test
  @DisplayName("4 AC-6: the toggles of a pending user carry the @username key; read-only and user admin ticked")
  void pendingUserToggles() {
    UserRow row = new UserRow(new TgUser(null, "bob"),
                              List.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES,
                                      Permission.USER_MANAGEMENT), true);

    Screen card = screen.user(row, true);

    assertEquals(List.of(
                     List.of(new Button("☐ ${ui.role.node-admin}", "USER_ROLE:@bob:NODE_ADMIN", null),
                             new Button("✅ ${ui.role.read-only}", "USER_ROLE:@bob:READ_ONLY", null)),
                     List.of(new Button("✅ ${ui.role.user-admin}", "USER_ROLE:@bob:USER_ADMIN", null))),
                 card.keyboard().subList(0, 2));
    assertTrue(buttons(card).contains(new Button("🗑 ${ui.button.remove}", "USER_DEL:@bob", null)));
  }

  @Test
  @DisplayName("4 AC-6, D-10: the card of a non-editable user (root, the caller) has only the back button")
  void nonEditableCard() {
    UserRow self = new UserRow(new TgUser(1L, "alice"), NODE_ADMIN_PERMISSIONS, false);

    assertEquals(List.of(BACK), buttons(screen.user(ZED_ROOT, true)));
    assertEquals(List.of(BACK), buttons(screen.user(self, true)));
    assertEquals(List.of(BACK), buttons(screen.user(self, false)));
  }

  // --- confirm delete --------------------------------------------------------------------------

  @Test
  @DisplayName("4 AC-6, AC-7, D-19: confirmDelete asks \"Remove the user <b>@username</b>?\" with Yes, remove (USER_DEL_CONFIRM) and Cancel (USER)")
  void confirmDelete() {
    Screen confirm = screen.confirmDelete(ALICE);

    assertTrue(confirm.template().contains("${ui.users.confirm-delete}"), confirm.template());
    assertEquals(List.of(new Button("✅ ${ui.button.confirm-delete}", "USER_DEL_CONFIRM:1", null),
                         new Button("✖ ${ui.button.cancel}", "USER:1", null)),
                 buttons(confirm));
    String text = render(confirm, "en").text();
    assertTrue(text.contains("Remove the user <b>@alice</b>?"), text);
    assertFalse(text.contains("%s"), text);
  }

  @Test
  @DisplayName("4 AC-6: confirmDelete of a pending user uses the @username key")
  void confirmDeletePending() {
    Screen confirm = screen.confirmDelete(BOB_PENDING);

    assertEquals(List.of("USER_DEL_CONFIRM:@bob", "USER:@bob"),
                 buttons(confirm).stream().map(Button::callbackData).toList());
    String text = render(confirm, "ru").text();
    assertTrue(text.contains("Удалить "), text);
    assertTrue(text.contains("@bob"), text);
  }

  @Test
  @DisplayName("4 AC-7: confirmDelete escapes the username (a pending username is typed by an admin)")
  void confirmDeleteEscapesUsername() {
    UserRow row = new UserRow(new TgUser(5L, "<x>"), READ_ONLY_PERMISSIONS, true);

    String text = render(screen.confirmDelete(row), "en").text();

    assertTrue(text.contains("&lt;x&gt;"), text);
    assertFalse(text.contains("<x>"), text);
  }

  @Test
  @DisplayName("4 AC-6: notAllowed shows ui.users.not-allowed with a menu button")
  void notAllowed() {
    Screen notAllowed = screen.notAllowed();

    assertTrue(notAllowed.template().contains("${ui.users.not-allowed}"), notAllowed.template());
    assertTrue(buttons(notAllowed).contains(MENU), notAllowed.keyboard().toString());
  }

  // --- callback size -------------------------------------------------------------------------

  @Test
  @DisplayName("4 AC-7: every callback of the users screens fits in 64 bytes for a 10-digit id and a 32-char username")
  void callbacksFit() {
    String username = "u".repeat(32);
    UserRow withId = new UserRow(new TgUser(9_999_999_999L, username), NODE_ADMIN_PERMISSIONS, true);
    UserRow pending = new UserRow(new TgUser(null, username), NODE_ADMIN_PERMISSIONS, true);

    List<Screen> screens = List.of(screen.list(new UsersModel(List.of(withId, pending))),
                                   screen.user(withId, true), screen.user(pending, true),
                                   screen.confirmDelete(withId), screen.confirmDelete(pending));
    for (Screen s : screens) {
      for (Button button : buttons(s)) {
        assertTrue(button.callbackData().getBytes(StandardCharsets.UTF_8).length < 64,
                   button.callbackData());
      }
    }
  }

  @Test
  @DisplayName("4 AC-7: every users screen renders in EN and RU without leftover keys")
  void allScreensRender() {
    List<Screen> all = List.of(screen.list(new UsersModel(List.of(ALICE, BOB_PENDING, ZED_ROOT, ID_ONLY))),
                               screen.user(ALICE, true), screen.user(BOB_PENDING, true),
                               screen.user(ZED_ROOT, true), screen.confirmDelete(ALICE),
                               screen.notAllowed());
    for (String language : List.of("en", "ru")) {
      for (Screen s : all) {
        String text = render(s, language).text();
        assertFalse(text.contains("${"), text);
        assertFalse(text.contains("%s"), text);
        renderedLabels(s, language).forEach(label -> assertFalse(label.contains("${"), label));
      }
    }
  }

}
