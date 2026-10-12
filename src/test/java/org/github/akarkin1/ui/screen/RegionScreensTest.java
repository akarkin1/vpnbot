package org.github.akarkin1.ui.screen;

import org.github.akarkin1.auth.AccessRequest;
import org.github.akarkin1.region.RegionRequest;
import org.github.akarkin1.translation.MainMessagesTranslator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.github.akarkin1.ui.screen.ScreenTestSupport.MENU;
import static org.github.akarkin1.ui.screen.ScreenTestSupport.buttons;
import static org.github.akarkin1.ui.screen.ScreenTestSupport.render;
import static org.github.akarkin1.ui.screen.ScreenTestSupport.renderedLabels;
import static org.github.akarkin1.ui.screen.ScreenTestSupport.withParams;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5 §4.2–§4.4, §4.7, AC-2, AC-8, D-8, D-10. Expected texts are written with
 * {@link ScreenTestSupport#withParams}: the template formatted with the HTML-escaped params and
 * its {@code ${…}} keys left in place, so the shapes the spec fixes are checked exactly without
 * depending on the English wording or on how a screen splits template and params. The keys are
 * checked against the real message files by rendering every screen in EN and RU
 * ({@link MainMessagesTranslator} fails on a missing key).
 */
class RegionScreensTest {

  private static final long USER_ID = 123456789L;
  private static final String FRANKFURT = "🇩🇪 Frankfurt";
  private static final RegionRequest REQUEST =
      new RegionRequest("eu-central-1", USER_ID, "alex", "Alex", "Smith", "ru", 1_760_097_600_123L);
  private static final Button DEPLOYED =
      new Button("✅ ${ui.button.deployed-notify}", "REGION_DONE:eu-central-1", null);
  private static final Button DECLINE =
      new Button("✖ ${ui.button.decline}", "REGION_DECLINE:eu-central-1", null);
  /** The requester line of {@link #REQUEST} (D-8: the same block as the access request's). */
  private static final String ALEX = "<b>Alex Smith</b> (@alex, 123456789)";
  private static final String FRANKFURT_ID = "<b>" + FRANKFURT + "</b>, <code>eu-central-1</code>";

  private final RegionScreens screens = new RegionScreens(ScreenTestSupport.regionLabels());
  private final MainMessagesTranslator messages = new MainMessagesTranslator();

  // --- Regions screen (§4.2) -----------------------------------------------------------------

  @Test
  @DisplayName("5 §4.2: the Regions screen reads \"🌍 <b>${ui.regions.title}</b>\\n${ui.regions.hint} ⏳ ${ui.regions.hint-pending}\" without params (the emoji stays in Java)")
  void regionsText() {
    Screen screen = screens.regions(List.of("eu-central-1"), Set.of());

    assertEquals("🌍 <b>${ui.regions.title}</b>\n${ui.regions.hint} ⏳ ${ui.regions.hint-pending}", screen.template());
    assertEquals(List.of(), screen.params());
  }

  @Test
  @DisplayName("5 AC-2: one button per region with its label, three per row in city order, callback REGION_REQ:<id>, Menu last")
  void regionsKeyboard() {
    // in city order (case-insensitive) already: whether the controller or the screen sorts is
    // left open, RegionControllerTest checks the order end to end
    List<String> available = List.of("eu-central-1", "eu-west-2", "ap-south-1", "us-east-1",
                                     "sa-east-1", "eu-north-1", "ap-northeast-1");

    Screen screen = screens.regions(available, Set.of());

    assertEquals(List.of(
                     List.of(region(FRANKFURT, "eu-central-1"),
                             region("🇬🇧 London", "eu-west-2"),
                             region("🇮🇳 mumbai", "ap-south-1")),
                     List.of(region("🇺🇸 N. Virginia", "us-east-1"),
                             region("🇧🇷 Sao Paulo", "sa-east-1"),
                             region("🇸🇪 Stockholm", "eu-north-1")),
                     List.of(region("🇯🇵 Tokyo", "ap-northeast-1")),
                     List.of(MENU)),
                 screen.keyboard());
  }

  @Test
  @DisplayName("5 AC-2: three regions fill exactly one row, followed by the Menu row")
  void regionsKeyboardOneFullRow() {
    Screen screen = screens.regions(List.of("eu-central-1", "eu-west-2", "eu-north-1"), Set.of());

    assertEquals(List.of(
                     List.of(region(FRANKFURT, "eu-central-1"),
                             region("🇬🇧 London", "eu-west-2"),
                             region("🇸🇪 Stockholm", "eu-north-1")),
                     List.of(MENU)),
                 screen.keyboard());
  }

  @Test
  @DisplayName("5 AC-2: pending regions get the ⏳ prefix and keep their place and callback; pending ids not offered add no button")
  void regionsPendingPrefix() {
    Screen screen = screens.regions(List.of("eu-central-1", "eu-west-2", "sa-east-1", "eu-north-1"),
                                    Set.of("eu-west-2", "eu-north-1", "us-east-1"));

    assertEquals(List.of(
                     List.of(region(FRANKFURT, "eu-central-1"),
                             region("⏳ 🇬🇧 London", "eu-west-2"),
                             region("🇧🇷 Sao Paulo", "sa-east-1")),
                     List.of(region("⏳ 🇸🇪 Stockholm", "eu-north-1")),
                     List.of(MENU)),
                 screen.keyboard());
  }

  @Test
  @DisplayName("5 §4.2: a region application.yml does not know gets its id as the label and the globe flag")
  void regionsUnknownLabel() {
    Screen screen = screens.regions(List.of("xx-unknown-1"), Set.of("xx-unknown-1"));

    assertEquals(List.of(List.of(region("⏳ 🌐 xx-unknown-1", "xx-unknown-1")), List.of(MENU)),
                 screen.keyboard());
  }

  @Test
  @DisplayName("5 §4.2: the Regions screen renders title, hint and the button labels in EN and RU")
  void regionsRendered() {
    Screen screen = screens.regions(List.of("eu-central-1", "eu-west-2"), Set.of("eu-west-2"));

    for (String language : List.of("en", "ru")) {
      String text = render(screen, language).text();
      assertEquals("🌍 <b>" + messages.value(language, "ui.regions.title") + "</b>\n"
                   + messages.value(language, "ui.regions.hint") + " ⏳ "
                   + messages.value(language, "ui.regions.hint-pending"),
                   text);
      assertEquals(List.of(FRANKFURT, "⏳ 🇬🇧 London", "🏠 " + messages.value(language, "ui.button.menu")),
                   renderedLabels(screen, language));
    }
  }

  // --- requester side (§4.2, §4.3, §4.4) ---------------------------------------------------------

  @Test
  @DisplayName("5 §4.2, §4.3: none, notAllowed and unknown show their key with the Menu row and no params")
  void simpleRequesterScreens() {
    assertAll(
        () -> assertSimple(screens.none(), "ui.regions.none", List.of(List.of(MENU))),
        () -> assertSimple(screens.notAllowed(), "ui.regions.not-allowed", List.of(List.of(MENU))),
        () -> assertSimple(screens.unknown(), "ui.regions.unknown", List.of(List.of(MENU))));
  }

  @Test
  @DisplayName("5 §4.3, §4.7: requested, alreadyRequested and alreadyAvailable put the bold region label in front of their key, with the Menu row")
  void requesterScreensWithLabel() {
    assertAll(
        () -> assertLabelled(screens.requested("eu-central-1"), "ui.regions.requested", FRANKFURT),
        () -> assertLabelled(screens.alreadyRequested("eu-central-1"), "ui.regions.already-requested",
                             FRANKFURT),
        () -> assertLabelled(screens.alreadyAvailable("eu-central-1"), "ui.regions.already-available",
                             FRANKFURT),
        () -> assertEquals(List.of(List.of(MENU)), screens.requested("eu-central-1").keyboard()),
        () -> assertEquals(List.of(List.of(MENU)), screens.alreadyRequested("eu-central-1").keyboard()),
        () -> assertEquals(List.of(List.of(MENU)), screens.alreadyAvailable("eu-central-1").keyboard()));
  }

  @Test
  @DisplayName("5 §4.4, §4.7: available starts with ✅ and puts the bold region label in front of ui.regions.available, with the Menu row")
  void available() {
    Screen screen = screens.available("eu-west-2");

    assertTrue(screen.template().startsWith("✅ "), screen.template());
    assertLabelled(screen, "ui.regions.available", "🇬🇧 London");
    assertEquals(List.of(List.of(MENU)), screen.keyboard());
  }

  @Test
  @DisplayName("5 §4.4, §4.7: declined starts with ✖ and puts the bold region label in front of ui.regions.declined, no buttons")
  void declined() {
    Screen screen = screens.declined("eu-west-2");

    assertTrue(screen.template().startsWith("✖ "), screen.template());
    assertLabelled(screen, "ui.regions.declined", "🇬🇧 London");
    assertEquals(List.of(), screen.keyboard());
  }

  @Test
  @DisplayName("5 §4.2: a region unknown to application.yml is named by its id with the globe flag")
  void requesterScreensUnknownLabel() {
    assertLabelled(screens.requested("xx-unknown-1"), "ui.regions.requested", "🌐 xx-unknown-1");
  }

  // --- admin side (§4.3, §4.4, D-8, D-10) --------------------------------------------------------

  @Test
  @DisplayName("5 §4.3, D-10: adminRequest reads \"🌍 <b>First Last</b> (@username, id) ${ui.regions.asks} <b>🇩🇪 Frankfurt</b>, <code>eu-central-1</code>\"")
  void adminRequestText() {
    Screen screen = screens.adminRequest(REQUEST);

    assertEquals("🌍 " + ALEX + " ${ui.regions.asks} " + FRANKFURT_ID, withParams(screen));
  }

  @Test
  @DisplayName("5 §4.3: adminRequest renders in English with ui.regions.asks between the requester and the region")
  void adminRequestRendered() {
    String text = render(screens.adminRequest(REQUEST), "en").text();

    assertEquals("🌍 " + ALEX + " " + messages.value("en", "ui.regions.asks") + " " + FRANKFURT_ID, text);
  }

  @Test
  @DisplayName("5 §4.3: adminRequest has the buttons Deployed, notify (REGION_DONE:<id>) and Decline (REGION_DECLINE:<id>)")
  void adminRequestButtons() {
    Screen screen = screens.adminRequest(REQUEST);

    assertEquals(List.of(DEPLOYED, DECLINE), buttons(screen));
  }

  @Test
  @DisplayName("5 §4.3, D-8: without a last name the requester is the first name alone")
  void adminRequestWithoutLastName() {
    Screen screen = screens.adminRequest(request("alex", "Alex", null));

    assertEquals("🌍 <b>Alex</b> (@alex, 123456789) ${ui.regions.asks} " + FRANKFURT_ID, withParams(screen));
  }

  @Test
  @DisplayName("5 §4.3, D-8: without a username the requester line is \"<b>First Last</b> (id)\", no @null")
  void adminRequestWithoutUsername() {
    Screen screen = screens.adminRequest(request(null, "Alex", "Smith"));

    assertEquals("🌍 <b>Alex Smith</b> (123456789) ${ui.regions.asks} " + FRANKFURT_ID, withParams(screen));
    assertFalse(render(screen, "en").text().contains("null"));
  }

  @Test
  @DisplayName("5 D-8: the requester line is the same block as in the access request message, for every name combination")
  void requesterLineSharedWithAccessScreens() {
    AccessScreens accessScreens = new AccessScreens();
    List<Executable> checks = new ArrayList<>();
    String[][] names = {{"alex", "Alex", "Smith"}, {"alex", "Alex", null}, {null, "Alex", "Smith"},
                        {null, "Alex", null}, {"alex", null, null}, {"", "Alex", " "}};
    for (String[] name : names) {
      String access = withParams(accessScreens.adminRequest(
          new AccessRequest(USER_ID, name[0], name[1], name[2], "en", 1L), Set.of()));
      String region = withParams(screens.adminRequest(request(name[0], name[1], name[2])));
      String description = name[0] + "/" + name[1] + "/" + name[2];
      checks.add(() -> assertEquals(between(access, "🙋 ", " ${ui.access.asks}"),
                                    between(region, "🌍 ", " ${ui.regions.asks}"), description));
    }
    assertAll(checks);
  }

  @Test
  @DisplayName("5 §4.4: notDeployedYet is the admin message plus \"⚠️ ${ui.regions.not-deployed-yet}\" with the same two buttons")
  void notDeployedYet() {
    Screen admin = screens.adminRequest(REQUEST);
    Screen screen = screens.notDeployedYet(REQUEST);

    String text = withParams(screen);
    String adminText = withParams(admin);
    String hint = "⚠️ ${ui.regions.not-deployed-yet}";
    assertTrue(text.startsWith(adminText), text);
    assertTrue(text.endsWith(hint), text);
    assertTrue(text.length() > adminText.length() + hint.length(),
               "a separator between the admin message and the hint: " + text);
    assertTrue(text.substring(adminText.length(), text.length() - hint.length()).isBlank(),
               "only whitespace between the admin message and the hint: " + text);
    assertEquals(admin.keyboard(), screen.keyboard());
    assertEquals(List.of(DEPLOYED, DECLINE), buttons(screen));
  }

  @Test
  @DisplayName("5 §4.4, D-10: deployedAdmin reads \"✅ <b>🇩🇪 Frankfurt</b>, <code>eu-central-1</code> ${ui.regions.requested-by} <b>First Last</b> (@username, id): ${ui.regions.deployed-admin}\", no buttons")
  void deployedAdmin() {
    Screen screen = screens.deployedAdmin(REQUEST);

    assertEquals("✅ " + FRANKFURT_ID + " ${ui.regions.requested-by} " + ALEX
                 + ": ${ui.regions.deployed-admin}",
                 withParams(screen));
    assertEquals(List.of(), screen.keyboard());
  }

  @Test
  @DisplayName("5 §4.4, D-10: declinedAdmin reads \"✖ <b>🇩🇪 Frankfurt</b>, <code>eu-central-1</code> ${ui.regions.requested-by} <b>First Last</b> (@username, id): ${ui.regions.declined-admin}\", no buttons")
  void declinedAdmin() {
    Screen screen = screens.declinedAdmin(REQUEST);

    assertEquals("✖ " + FRANKFURT_ID + " ${ui.regions.requested-by} " + ALEX
                 + ": ${ui.regions.declined-admin}",
                 withParams(screen));
    assertEquals(List.of(), screen.keyboard());
  }

  @Test
  @DisplayName("5 D-8, D-10: the closed variants without a username or last name show \"<b>First</b> (id)\"")
  void closedVariantsWithoutUsername() {
    RegionRequest request = request(null, "Alex", null);

    assertEquals("✅ " + FRANKFURT_ID + " ${ui.regions.requested-by} <b>Alex</b> (123456789): "
                 + "${ui.regions.deployed-admin}",
                 withParams(screens.deployedAdmin(request)));
    assertEquals("✖ " + FRANKFURT_ID + " ${ui.regions.requested-by} <b>Alex</b> (123456789): "
                 + "${ui.regions.declined-admin}",
                 withParams(screens.declinedAdmin(request)));
  }

  @Test
  @DisplayName("5 §4.4: alreadyHandled shows ui.regions.already-handled and no buttons")
  void alreadyHandled() {
    assertSimple(screens.alreadyHandled(), "ui.regions.already-handled", List.of());
  }

  @Test
  @DisplayName("5 §4.4: only adminRequest and notDeployedYet carry the decision buttons")
  void decisionButtonsOnlyOnOpenRequest() {
    List<Screen> others = List.of(screens.deployedAdmin(REQUEST), screens.declinedAdmin(REQUEST),
                                  screens.alreadyHandled(), screens.available("eu-central-1"),
                                  screens.declined("eu-central-1"), screens.none(), screens.notAllowed(),
                                  screens.unknown(), screens.requested("eu-central-1"),
                                  screens.alreadyRequested("eu-central-1"),
                                  screens.alreadyAvailable("eu-central-1"));
    for (Screen screen : others) {
      for (Button button : buttons(screen)) {
        String data = String.valueOf(button.callbackData());
        assertFalse(data.startsWith("REGION_DONE") || data.startsWith("REGION_DECLINE"),
                    screen.template() + " -> " + data);
      }
    }
  }

  // --- escaping (AC-8: every dynamic value is a param) -------------------------------------------

  @Test
  @DisplayName("5 AC-8: the requester's name is a param and HTML-escaped in every admin screen")
  void adminScreensEscapeName() {
    RegionRequest request = new RegionRequest("eu-central-1", USER_ID, "eve", "<i>Eve</i> & 100%", null,
                                              "en", 1L);

    for (Screen screen : List.of(screens.adminRequest(request), screens.notDeployedYet(request),
                                 screens.deployedAdmin(request), screens.declinedAdmin(request))) {
      assertFalse(screen.template().contains("Eve"), screen.template());
      String text = render(screen, "en").text();
      assertTrue(text.contains("<b>&lt;i&gt;Eve&lt;/i&gt; &amp; 100%</b>"), text);
      assertFalse(text.contains("<i>"), text);
    }
  }

  @Test
  @DisplayName("5 AC-8: the region label and id are params and HTML-escaped in every screen that names the region")
  void screensEscapeRegion() {
    RegionScreens escaping = new RegionScreens(new RegionLabels(Map.of("xx-test-1", "<Test & Co>"),
                                                                Map.of("xx-test-1", "DE")));
    RegionRequest request = new RegionRequest("xx-test-1", USER_ID, "alex", "Alex", null, "en", 1L);

    List<Screen> admin = List.of(escaping.adminRequest(request), escaping.notDeployedYet(request),
                                 escaping.deployedAdmin(request), escaping.declinedAdmin(request));
    List<Screen> requester = List.of(escaping.requested("xx-test-1"), escaping.alreadyRequested("xx-test-1"),
                                     escaping.alreadyAvailable("xx-test-1"), escaping.available("xx-test-1"),
                                     escaping.declined("xx-test-1"));
    List<Executable> checks = new ArrayList<>();
    for (Screen screen : admin) {
      checks.add(() -> assertFalse(screen.template().contains("xx-test-1"), screen.template()));
      checks.add(() -> assertTrue(render(screen, "en").text().contains("<code>xx-test-1</code>"),
                                  render(screen, "en").text()));
    }
    for (Screen screen : concat(admin, requester)) {
      String text = render(screen, "en").text();
      checks.add(() -> assertFalse(screen.template().contains("Test"), screen.template()));
      checks.add(() -> assertTrue(text.contains("<b>🇩🇪 &lt;Test &amp; Co&gt;</b>"), text));
      checks.add(() -> assertFalse(text.contains("<Test"), text));
    }
    assertAll(checks);
  }

  // --- placeholders and texts (AC-8) -------------------------------------------------------------

  @Test
  @DisplayName("5 AC-8: every region screen's %s placeholders match its params")
  void placeholdersMatchParams() {
    allScreens().forEach(ScreenTestSupport::assertPlaceholdersMatchParams);
  }

  @Test
  @DisplayName("5 AC-8, §4.7: every region screen renders in EN and RU from the real message files without leftover keys or specifiers")
  void allScreensRender() {
    List<Executable> checks = new ArrayList<>();
    for (String language : List.of("en", "ru")) {
      for (Screen screen : allScreens()) {
        checks.add(() -> {
          String text = render(screen, language).text();
          assertFalse(text.contains("${"), text);
          assertFalse(text.contains("%s"), text);
          renderedLabels(screen, language).forEach(label -> assertFalse(label.contains("${"), label));
        });
      }
    }
    assertAll(checks);
  }

  @Test
  @DisplayName("5 §4.7: the region screens use the ui.regions.* keys and the deployed-notify and decline buttons of §4.7")
  void screensUseSpecKeys() {
    String templates = String.join("\n", allScreens().stream().map(Screen::template).toList());
    String labels = String.join("\n", allScreens().stream()
        .flatMap(screen -> buttons(screen).stream())
        .map(Button::label)
        .toList());
    List<String> keys = List.of("ui.regions.title", "ui.regions.hint", "ui.regions.hint-pending", "ui.regions.none",
                                "ui.regions.not-allowed", "ui.regions.requested",
                                "ui.regions.already-requested", "ui.regions.already-available",
                                "ui.regions.unknown", "ui.regions.asks", "ui.regions.requested-by",
                                "ui.regions.not-deployed-yet", "ui.regions.deployed-admin",
                                "ui.regions.declined-admin", "ui.regions.already-handled",
                                "ui.regions.available", "ui.regions.declined");
    assertAll(keys.stream()
                  .map(key -> (Executable) () -> assertTrue(templates.contains("${" + key + "}"), key)));
    assertTrue(labels.contains("${ui.button.deployed-notify}"), labels);
    assertTrue(labels.contains("${ui.button.decline}"), labels);
  }

  // --- helpers -------------------------------------------------------------------------------------

  private List<Screen> allScreens() {
    RegionRequest bare = request(null, "Alex", null);
    return List.of(
        screens.regions(List.of("eu-central-1", "eu-west-2", "eu-north-1", "xx-unknown-1"),
                        Set.of("eu-west-2")),
        screens.none(), screens.notAllowed(), screens.unknown(),
        screens.requested("eu-central-1"), screens.alreadyRequested("eu-central-1"),
        screens.alreadyAvailable("eu-central-1"), screens.available("eu-central-1"),
        screens.declined("eu-central-1"),
        screens.adminRequest(REQUEST), screens.adminRequest(bare),
        screens.notDeployedYet(REQUEST), screens.notDeployedYet(bare),
        screens.deployedAdmin(REQUEST), screens.deployedAdmin(bare),
        screens.declinedAdmin(REQUEST), screens.declinedAdmin(bare),
        screens.alreadyHandled());
  }

  private static RegionRequest request(String username, String firstName, String lastName) {
    return new RegionRequest("eu-central-1", USER_ID, username, firstName, lastName, "en", 1L);
  }

  /** The key in the template, no params, the given keyboard. */
  private static void assertSimple(Screen screen, String key, List<List<Button>> keyboard) {
    assertTrue(screen.template().contains("${" + key + "}"), screen.template());
    assertEquals(List.of(), screen.params());
    assertEquals(keyboard, screen.keyboard());
  }

  /** §4.7: the region label is a param, shown in bold in front of the key's sentence tail. */
  private static void assertLabelled(Screen screen, String key, String label) {
    assertTrue(screen.template().contains("${" + key + "}"), screen.template());
    assertTrue(screen.params().contains(label), screen.params().toString());
    String text = withParams(screen);
    int labelAt = text.indexOf("<b>" + label + "</b>");
    assertTrue(labelAt >= 0, text);
    assertTrue(labelAt < text.indexOf("${" + key + "}"), text);
  }

  private static String between(String text, String prefix, String suffix) {
    assertTrue(text.startsWith(prefix), text);
    int end = text.indexOf(suffix);
    assertTrue(end > 0, text);
    return text.substring(prefix.length(), end);
  }

  private static List<Screen> concat(List<Screen> first, List<Screen> second) {
    List<Screen> all = new ArrayList<>(first);
    all.addAll(second);
    return all;
  }

  private static Button region(String label, String regionId) {
    return new Button(label, "REGION_REQ:" + regionId, null);
  }

}
