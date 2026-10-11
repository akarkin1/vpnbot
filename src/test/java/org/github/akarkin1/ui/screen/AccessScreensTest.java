package org.github.akarkin1.ui.screen;

import org.github.akarkin1.auth.AccessRequest;
import org.github.akarkin1.auth.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.github.akarkin1.ui.screen.ScreenTestSupport.MENU;
import static org.github.akarkin1.ui.screen.ScreenTestSupport.buttons;
import static org.github.akarkin1.ui.screen.ScreenTestSupport.render;
import static org.github.akarkin1.ui.screen.ScreenTestSupport.renderedLabels;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §4.2, §4.3. Texts whose role names fill a {@code %s} of a message value (granted, accessGranted)
 * are checked as rendered by the real renderer over the real message files, so the test does not
 * depend on how the screen splits template and params.
 */
class AccessScreensTest {

  private static final long USER_ID = 123456789L;
  private static final AccessRequest REQUEST =
      new AccessRequest(USER_ID, "alex", "Alex", "ru", 1_760_097_600_123L);

  private final AccessScreens screens = new AccessScreens();

  // --- requester side ------------------------------------------------------------------------

  @Test
  @DisplayName("4 AC-7: requested shows ui.access.requested with a menu button")
  void requested() {
    Screen screen = screens.requested();

    assertTrue(screen.template().contains("${ui.access.requested}"), screen.template());
    assertEquals(List.of(), screen.params());
    assertEquals(List.of(List.of(MENU)), screen.keyboard());
  }

  @Test
  @DisplayName("4 AC-7: alreadyRequested shows ui.access.already-requested with a menu button")
  void alreadyRequested() {
    Screen screen = screens.alreadyRequested();

    assertTrue(screen.template().contains("${ui.access.already-requested}"), screen.template());
    assertEquals(List.of(), screen.params());
    assertEquals(List.of(List.of(MENU)), screen.keyboard());
  }

  @Test
  @DisplayName("4 AC-5, AC-7: accessGranted names the roles in the requester's language (RU), with a menu button")
  void accessGrantedRussian() {
    Screen screen = screens.accessGranted(Set.of(UserRole.NODE_ADMIN, UserRole.READ_ONLY));

    assertTrue(screen.template().contains("${ui.access.granted}"), screen.template());
    assertEquals(List.of(List.of(MENU)), screen.keyboard());
    String text = render(screen, "ru").text();
    assertTrue(text.contains("Доступ выдан: "), text);
    assertTrue(text.contains("Администратор VPN-серверов, Только просмотр")
               || text.contains("Только просмотр, Администратор VPN-серверов"), text);
    assertTrue(text.contains(". Добро пожаловать!"), text);
    assertNoRawKeysOrSpecifiers(text);
  }

  @Test
  @DisplayName("4 AC-5, AC-7: accessGranted with one role reads \"Access granted: <role>. Welcome!\" in English")
  void accessGrantedEnglish() {
    String text = render(screens.accessGranted(Set.of(UserRole.USER_ADMIN)), "en").text();

    assertTrue(text.contains("Access granted: User admin. Welcome!"), text);
    assertNoRawKeysOrSpecifiers(text);
  }

  @Test
  @DisplayName("4 AC-7: accessDeclined shows ui.access.declined without buttons")
  void accessDeclined() {
    Screen screen = screens.accessDeclined();

    assertTrue(screen.template().contains("${ui.access.declined}"), screen.template());
    assertEquals(List.of(), screen.params());
    assertEquals(List.of(), screen.keyboard());
  }

  // --- admin side ----------------------------------------------------------------------------

  @Test
  @DisplayName("4 AC-7: adminRequest reads \"🙋 <b>First</b> (@username, id) asks for access to the bot.\"")
  void adminRequestText() {
    Screen screen = screens.adminRequest(REQUEST, Set.of());

    assertEquals("🙋 <b>Alex</b> (@alex, 123456789) asks for access to the bot.",
                 render(screen, "en").text());
    assertTrue(screen.template().contains("${ui.access.asks}"), screen.template());
  }

  @Test
  @DisplayName("4 AC-7: adminRequest without a username shows no @null")
  void adminRequestWithoutUsername() {
    Screen screen = screens.adminRequest(new AccessRequest(USER_ID, null, "Alex", "en", 1L), Set.of());

    String text = render(screen, "en").text();
    assertTrue(text.contains("123456789"), text);
    assertTrue(text.contains("<b>Alex</b>"), text);
    assertFalse(text.contains("null"), text);
    assertFalse(text.contains("@,"), text);
  }

  @Test
  @DisplayName("4 AC-7: adminRequest with nothing selected: unticked role checkboxes with mask 0, Grant (mask 0) and Decline")
  void adminRequestKeyboardNothingSelected() {
    Screen screen = screens.adminRequest(REQUEST, Set.of());

    assertEquals(List.of(
                     List.of(new Button("☐ ${ui.role.node-admin}", "ACCESS_TOGGLE:123456789:0:NODE_ADMIN", null),
                             new Button("☐ ${ui.role.read-only}", "ACCESS_TOGGLE:123456789:0:READ_ONLY", null)),
                     List.of(new Button("☐ ${ui.role.user-admin}", "ACCESS_TOGGLE:123456789:0:USER_ADMIN", null)),
                     List.of(new Button("✅ ${ui.button.grant}", "ACCESS_GRANT:123456789:0", null),
                             new Button("✖ ${ui.button.decline}", "ACCESS_DECLINE:123456789", null))),
                 screen.keyboard());
  }

  @Test
  @DisplayName("4 AC-5, AC-7: adminRequest ticks the selected roles and carries their mask on every button")
  void adminRequestKeyboardWithSelection() {
    Screen screen = screens.adminRequest(REQUEST, Set.of(UserRole.NODE_ADMIN, UserRole.USER_ADMIN));

    assertEquals(List.of(
                     List.of(new Button("✅ ${ui.role.node-admin}", "ACCESS_TOGGLE:123456789:5:NODE_ADMIN", null),
                             new Button("☐ ${ui.role.read-only}", "ACCESS_TOGGLE:123456789:5:READ_ONLY", null)),
                     List.of(new Button("✅ ${ui.role.user-admin}", "ACCESS_TOGGLE:123456789:5:USER_ADMIN", null)),
                     List.of(new Button("✅ ${ui.button.grant}", "ACCESS_GRANT:123456789:5", null),
                             new Button("✖ ${ui.button.decline}", "ACCESS_DECLINE:123456789", null))),
                 screen.keyboard());
  }

  @Test
  @DisplayName("4 AC-7: the text of adminRequest does not depend on the selection")
  void adminRequestTextIndependentOfSelection() {
    Screen none = screens.adminRequest(REQUEST, Set.of());
    Screen all = screens.adminRequest(REQUEST, EnumSet.allOf(UserRole.class));

    assertEquals(none.template(), all.template());
    assertEquals(none.params(), all.params());
  }

  @Test
  @DisplayName("4 AC-7: the first name is HTML-escaped (a param, not part of the template)")
  void adminRequestEscapesName() {
    AccessRequest request = new AccessRequest(USER_ID, "eve", "<i>Eve</i> & 100%", "en", 1L);

    Screen screen = screens.adminRequest(request, Set.of());

    assertFalse(screen.template().contains("Eve"), screen.template());
    String text = render(screen, "en").text();
    assertTrue(text.contains("&lt;i&gt;Eve&lt;/i&gt; &amp; 100%"), text);
    assertFalse(text.contains("<i>"), text);
  }

  @Test
  @DisplayName("4 AC-7: every adminRequest callback for a 10-digit id fits in 64 bytes")
  void adminRequestCallbacksFit() {
    AccessRequest request = new AccessRequest(9_999_999_999L, "u".repeat(32), "Alex", "en", 1L);

    Screen screen = screens.adminRequest(request, EnumSet.allOf(UserRole.class));

    for (Button button : buttons(screen)) {
      assertTrue(button.callbackData().getBytes(StandardCharsets.UTF_8).length < 64,
                 button.callbackData());
      assertTrue(button.callbackData().contains("9999999999"), button.callbackData());
    }
  }

  @Test
  @DisplayName("4 AC-7: adminRequest buttons render in the admin's language")
  void adminRequestLabelsRendered() {
    List<String> labels = renderedLabels(screens.adminRequest(REQUEST, Set.of(UserRole.READ_ONLY)), "en");

    assertEquals(List.of("☐ Node admin", "✅ Read-only", "☐ User admin", "✅ Grant", "✖ Decline"), labels);
  }

  @Test
  @DisplayName("4 AC-5, AC-7: granted names the requester and the granted roles, without buttons")
  void granted() {
    Screen screen = screens.granted(REQUEST, Set.of(UserRole.NODE_ADMIN, UserRole.READ_ONLY));

    assertTrue(screen.template().contains("${ui.access.granted-admin}"), screen.template());
    assertEquals(List.of(), screen.keyboard());
    String text = render(screen, "en").text();
    assertTrue(text.contains("Alex"), text);
    assertTrue(text.contains("granted: "), text);
    assertTrue(text.contains("Node admin, Read-only") || text.contains("Read-only, Node admin"), text);
    assertNoRawKeysOrSpecifiers(text);
  }

  @Test
  @DisplayName("4 AC-7: granted escapes the requester's name")
  void grantedEscapesName() {
    AccessRequest request = new AccessRequest(USER_ID, "eve", "<Eve>", "en", 1L);

    String text = render(screens.granted(request, Set.of(UserRole.USER_ADMIN)), "en").text();

    assertTrue(text.contains("&lt;Eve&gt;"), text);
    assertTrue(text.contains("User admin"), text);
  }

  @Test
  @DisplayName("4 AC-7: declined names the requester, without buttons")
  void declined() {
    Screen screen = screens.declined(REQUEST);

    assertTrue(screen.template().contains("${ui.access.declined-admin}"), screen.template());
    assertEquals(List.of(), screen.keyboard());
    String text = render(screen, "en").text();
    assertTrue(text.contains("Alex"), text);
    assertTrue(text.contains("declined."), text);
    assertNoRawKeysOrSpecifiers(text);
  }

  @Test
  @DisplayName("4 AC-7: alreadyHandled shows ui.access.already-handled and no access buttons")
  void alreadyHandled() {
    Screen screen = screens.alreadyHandled();

    assertTrue(screen.template().contains("${ui.access.already-handled}"), screen.template());
    assertNoAccessButtons(screen);
  }

  @Test
  @DisplayName("4 AC-5: notAllowed shows ui.users.not-allowed and no access buttons")
  void notAllowed() {
    Screen screen = screens.notAllowed();

    assertTrue(screen.template().contains("${ui.users.not-allowed}"), screen.template());
    assertNoAccessButtons(screen);
  }

  @Test
  @DisplayName("4 AC-7: placeholders match params for the screens whose texts have no param in a message value")
  void placeholdersMatchParams() {
    List.of(screens.requested(),
            screens.alreadyRequested(),
            screens.accessDeclined(),
            screens.adminRequest(REQUEST, Set.of()),
            screens.adminRequest(new AccessRequest(USER_ID, null, "Alex", null, 1L), Set.of()),
            screens.declined(REQUEST),
            screens.alreadyHandled(),
            screens.notAllowed())
        .forEach(ScreenTestSupport::assertPlaceholdersMatchParams);
  }

  @Test
  @DisplayName("4 AC-7, AC-11: every access screen renders in EN and RU without leftover keys or specifiers")
  void allScreensRender() {
    List<Screen> all = List.of(
        screens.requested(),
        screens.alreadyRequested(),
        screens.accessGranted(EnumSet.allOf(UserRole.class)),
        screens.accessDeclined(),
        screens.adminRequest(REQUEST, EnumSet.allOf(UserRole.class)),
        screens.granted(REQUEST, EnumSet.allOf(UserRole.class)),
        screens.declined(REQUEST),
        screens.alreadyHandled(),
        screens.notAllowed());
    for (String language : List.of("en", "ru")) {
      for (Screen screen : all) {
        assertNoRawKeysOrSpecifiers(render(screen, language).text());
        renderedLabels(screen, language).forEach(label -> assertFalse(label.contains("${"), label));
      }
    }
  }

  private static void assertNoRawKeysOrSpecifiers(String text) {
    assertFalse(text.contains("${"), text);
    assertFalse(text.contains("%s"), text);
  }

  private static void assertNoAccessButtons(Screen screen) {
    for (Button button : buttons(screen)) {
      String data = String.valueOf(button.callbackData());
      assertFalse(data.startsWith("ACCESS_"), data);
    }
  }

}
