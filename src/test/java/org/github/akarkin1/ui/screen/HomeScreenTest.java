package org.github.akarkin1.ui.screen;

import org.github.akarkin1.ecs.EcsNode;
import org.github.akarkin1.translation.MainMessagesTranslator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;

import java.util.List;

import static org.github.akarkin1.ui.screen.ScreenTestSupport.REFRESH_HELP;
import static org.github.akarkin1.ui.screen.ScreenTestSupport.assertPlaceholdersMatchParams;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeScreenTest {

  private static final String GREETING = "👋 ${ui.home.greeting}, %s!";
  private static final String NODE_LINE = "\n%s <b>%s</b> · %s · <code>%s</code>";
  private static final Button REQUEST_ACCESS =
      new Button("🙋 ${ui.button.request-access}", "REQUEST_ACCESS", null);
  private static final Button USERS = new Button("👥 ${ui.button.users}", "USERS", null);
  private static final List<Button> OTHER_REGION =
      List.of(new Button("🌍 ${ui.button.other-region}", "REGIONS", null));

  private final HomeScreen homeScreen = new HomeScreen(ScreenTestSupport.regionLabels());

  @Test
  @DisplayName("AC-3: greeting uses the first name")
  void greetingUsesFirstName() {
    Screen screen = homeScreen.home(noAccess("Alex", "alex"));

    assertTrue(screen.template().startsWith(GREETING), screen.template());
    assertEquals(List.of("Alex"), screen.params());
  }

  @Test
  @DisplayName("AC-3: greeting falls back to @username when the first name is blank")
  void greetingFallsBackToUsername() {
    Screen screen = homeScreen.home(noAccess(" ", "alex"));

    assertTrue(screen.template().startsWith(GREETING), screen.template());
    assertEquals(List.of("@alex"), screen.params());
  }

  @Test
  @DisplayName("AC-3: greeting has no name when first name and username are missing")
  void greetingWithoutName() {
    Screen screen = homeScreen.home(noAccess(null, ""));

    assertTrue(screen.template().startsWith("👋 ${ui.home.greeting}!"), screen.template());
    assertEquals(List.of(), screen.params());
  }

  @Test
  @DisplayName("AC-4, 4 §4.2: a user without access sees the no-access text")
  void noAccessText() {
    Screen screen = homeScreen.home(noAccess("Alex", "alex"));

    assertEquals(GREETING + "\n\n${ui.home.no-access}", screen.template());
  }

  @Test
  @DisplayName("4 AC-7, §4.2, D-21: a user without access gets the Request access row (REQUEST_ACCESS), then Refresh/Help")
  void noAccessKeyboard() {
    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", false, false, false, List.of(),
                                                  List.of("eu-central-1"), List.of(), false, false, false));

    assertEquals(List.of(List.of(REQUEST_ACCESS), REFRESH_HELP), screen.keyboard());
  }

  @Test
  @DisplayName("4 §4.2: users with access never get the Request access button")
  void noRequestAccessWithAccess() {
    List<HomeModel> models = List.of(
        new HomeModel("Alex", "alex", true, false, false, List.of(), List.of(), List.of(), false, true, false),
        new HomeModel("Alex", "alex", false, true, false, List.of(), List.of("eu-central-1"), List.of(),
                      false, true, true),
        new HomeModel("Alex", "alex", false, false, false, List.of(), List.of(), List.of(), true, true, false));

    for (HomeModel model : models) {
      List<String> data = callbackData(homeScreen.home(model));
      assertFalse(data.contains("REQUEST_ACCESS"), model + " -> " + data);
    }
  }

  @Test
  @DisplayName("4 AC-7, §4.4: a user admin gets the Users row (USERS) between the stop rows and Refresh/Help")
  void usersRow() {
    EcsNode node = stoppable("task-1", "alex-frankfurt-1", Region.EU_CENTRAL_1);

    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", true, true, false, List.of(node),
                                                  List.of("eu-central-1"), List.of("task-1"), true, true, true));

    assertEquals(List.of(List.of(region("🇩🇪 Frankfurt", "eu-central-1")),
                         OTHER_REGION,
                         List.of(new Button("🛑 alex-frankfurt-1", "STOP:eu-central-1:task-1", null)),
                         List.of(USERS),
                         REFRESH_HELP),
                 screen.keyboard());
  }

  @Test
  @DisplayName("4 §4.4: a user admin without node permissions gets Users and Refresh/Help, and no no-access text")
  void usersRowOnly() {
    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", false, false, false, List.of(),
                                                  List.of(), List.of(), true, true, false));

    assertEquals(List.of(List.of(USERS), REFRESH_HELP), screen.keyboard());
    assertFalse(screen.template().contains("${ui.home.no-access}"), screen.template());
  }

  @Test
  @DisplayName("4 §4.4: no Users row without canManageUsers")
  void noUsersRow() {
    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", true, true, true, List.of(),
                                                  List.of(), List.of(), false, true, true));

    assertFalse(callbackData(screen).contains("USERS"), screen.keyboard().toString());
  }

  @Test
  @DisplayName("AC-5: nodes are listed with status emoji, host, region label and IP, — for nulls")
  void listsNodes() {
    List<EcsNode> nodes = List.of(
        node("HEALTHY", "node-1", Region.EU_CENTRAL_1, "1.2.3.4"),
        node("UNHEALTHY", "node-2", Region.US_EAST_1, "5.6.7.8"),
        node(null, null, null, null));

    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", true, false, false, nodes, List.of(),
                                                  List.of(), false, true, false));

    assertEquals(GREETING + "\n\n<b>${ui.home.your-nodes}</b>" + NODE_LINE + NODE_LINE + NODE_LINE,
                 screen.template());
    assertEquals(List.of("Alex",
                         "🟢", "node-1", "🇩🇪 Frankfurt", "1.2.3.4",
                         "🔴", "node-2", "🇺🇸 N. Virginia", "5.6.7.8",
                         "🟡", "—", "—", "—"),
                 screen.params());
  }

  @Test
  @DisplayName("AC-5: blank host and IP are shown as —")
  void blankNodeValues() {
    List<EcsNode> nodes = List.of(node("PROVISIONING", " ", Region.EU_CENTRAL_1, ""));

    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", true, false, false, nodes, List.of(),
                                                  List.of(), false, true, false));

    assertEquals(List.of("Alex", "🟡", "—", "🇩🇪 Frankfurt", "—"), screen.params());
  }

  @Test
  @DisplayName("AC-5: empty node list shows ui.home.no-nodes")
  void noNodes() {
    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", true, false, false, List.of(),
                                                  List.of(), List.of(), false, true, false));

    assertEquals(GREETING + "\n\n<b>${ui.home.your-nodes}</b>\n${ui.home.no-nodes}",
                 screen.template());
    assertEquals(List.of(REFRESH_HELP), screen.keyboard());
  }

  @Test
  @DisplayName("AC-5: allNodes uses ui.home.all-nodes")
  void allNodesTitle() {
    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", true, false, true, List.of(),
                                                  List.of(), List.of(), false, true, false));

    assertEquals(GREETING + "\n\n<b>${ui.home.all-nodes}</b>\n${ui.home.no-nodes}",
                 screen.template());
  }

  @Test
  @DisplayName("AC-6, 5 AC-1: region buttons are sorted by city (case-insensitive), 3 per row, callback RUN:<id>, then the Other region row")
  void regionButtons() {
    List<String> regionIds = List.of("us-east-1", "eu-central-1", "ap-northeast-1", "eu-west-2",
                                     "ap-south-1", "eu-north-1", "sa-east-1");

    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", false, true, false, List.of(),
                                                  regionIds, List.of(), false, true, true));

    assertEquals(List.of(
                     List.of(region("🇩🇪 Frankfurt", "eu-central-1"),
                             region("🇬🇧 London", "eu-west-2"),
                             region("🇮🇳 mumbai", "ap-south-1")),
                     List.of(region("🇺🇸 N. Virginia", "us-east-1"),
                             region("🇧🇷 Sao Paulo", "sa-east-1"),
                             region("🇸🇪 Stockholm", "eu-north-1")),
                     List.of(region("🇯🇵 Tokyo", "ap-northeast-1")),
                     OTHER_REGION,
                     REFRESH_HELP),
                 screen.keyboard());
  }

  @Test
  @DisplayName("AC-6: user with RUN_NODES sees the start-node section")
  void startNodeSection() {
    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", false, true, false, List.of(),
                                                  List.of("eu-central-1"), List.of(), false, true, true));

    assertEquals(GREETING + "\n\n<b>${ui.home.start-node}</b>", screen.template());
  }

  @Test
  @DisplayName("AC-6: no region buttons and no start-node section without RUN_NODES")
  void noRegionButtonsWithoutRunNodes() {
    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", true, false, false, List.of(),
                                                  List.of("eu-central-1", "eu-west-2"), List.of(),
                                                  false, true, false));

    assertEquals(List.of(REFRESH_HELP), screen.keyboard());
    assertFalse(screen.template().contains("${ui.home.start-node}"), screen.template());
  }

  @Test
  @DisplayName("AC-6, 5 AC-1: empty region list shows ui.home.no-regions, and the Other region row is still offered")
  void noRegions() {
    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", false, true, false, List.of(),
                                                  List.of(), List.of(), false, true, true));

    assertEquals(GREETING + "\n\n<b>${ui.home.start-node}</b>\n${ui.home.no-regions}",
                 screen.template());
    assertEquals(List.of(OTHER_REGION, REFRESH_HELP), screen.keyboard());
  }

  @Test
  @DisplayName("AC-5, AC-6: nodes section comes before the start-node section")
  void sectionOrder() {
    List<EcsNode> nodes = List.of(node("HEALTHY", "node-1", Region.EU_CENTRAL_1, "1.2.3.4"));

    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", true, true, false, nodes,
                                                  List.of("eu-central-1"), List.of(), false, true, true));

    assertEquals(GREETING + "\n\n<b>${ui.home.your-nodes}</b>" + NODE_LINE
                 + "\n\n<b>${ui.home.start-node}</b>",
                 screen.template());
    assertEquals(List.of(List.of(region("🇩🇪 Frankfurt", "eu-central-1")), OTHER_REGION, REFRESH_HELP),
                 screen.keyboard());
  }

  @Test
  @DisplayName("2b AC-8, 5 AC-1: one stop row per stoppable node, in order, between the region rows (and the Other region row) and Refresh/Help")
  void stopButtons() {
    EcsNode first = stoppable("task-1", "alex-frankfurt-1", Region.EU_CENTRAL_1);
    EcsNode notStoppable = stoppable("task-2", "bob-london-1", Region.EU_WEST_2);
    EcsNode third = stoppable("task-3", "alex-tokyo-1", Region.AP_NORTHEAST_1);

    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", true, true, false,
                                                  List.of(first, notStoppable, third),
                                                  List.of("eu-central-1"),
                                                  List.of("task-1", "task-3"), false, true, true));

    assertEquals(List.of(List.of(region("🇩🇪 Frankfurt", "eu-central-1")),
                         OTHER_REGION,
                         List.of(new Button("🛑 alex-frankfurt-1", "STOP:eu-central-1:task-1", null)),
                         List.of(new Button("🛑 alex-tokyo-1", "STOP:ap-northeast-1:task-3", null)),
                         REFRESH_HELP),
                 screen.keyboard());
  }

  @Test
  @DisplayName("2b AC-8: no stop rows when no node is stoppable")
  void noStopButtons() {
    EcsNode node = stoppable("task-1", "bob-frankfurt-1", Region.EU_CENTRAL_1);

    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", true, true, true, List.of(node),
                                                  List.of("eu-central-1"), List.of(), false, true, true));

    assertEquals(List.of(List.of(region("🇩🇪 Frankfurt", "eu-central-1")), OTHER_REGION, REFRESH_HELP),
                 screen.keyboard());
  }

  @Test
  @DisplayName("2b AC-8: stop rows do not change the home text")
  void stopButtonsKeepText() {
    EcsNode node = node("HEALTHY", "node-1", Region.EU_CENTRAL_1, "1.2.3.4");
    EcsNode stoppableNode = stoppable("task-1", "node-1", Region.EU_CENTRAL_1);

    Screen without = homeScreen.home(new HomeModel("Alex", "alex", true, true, false, List.of(node),
                                                   List.of("eu-central-1"), List.of(), false, true, true));
    Screen with = homeScreen.home(new HomeModel("Alex", "alex", true, true, false,
                                                List.of(stoppableNode), List.of("eu-central-1"),
                                                List.of("task-1"), false, true, true));

    assertEquals(without.template(), with.template());
  }

  @Test
  @DisplayName("5 AC-1, §4.1: the Other region row (REGIONS) follows the last region row – also a partial one – and precedes the stop rows")
  void otherRegionRowPlacement() {
    EcsNode node = stoppable("task-1", "alex-frankfurt-1", Region.EU_CENTRAL_1);

    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", true, true, false, List.of(node),
                                                  List.of("eu-central-1", "eu-west-2", "eu-north-1",
                                                          "us-east-1"),
                                                  List.of("task-1"), false, true, true));

    assertEquals(List.of(
                     List.of(region("🇩🇪 Frankfurt", "eu-central-1"),
                             region("🇬🇧 London", "eu-west-2"),
                             region("🇺🇸 N. Virginia", "us-east-1")),
                     List.of(region("🇸🇪 Stockholm", "eu-north-1")),
                     OTHER_REGION,
                     List.of(new Button("🛑 alex-frankfurt-1", "STOP:eu-central-1:task-1", null)),
                     REFRESH_HELP),
                 screen.keyboard());
  }

  @Test
  @DisplayName("5 AC-1: no Other region row when canRequestRegions is false, even with the region buttons")
  void noOtherRegionRowWithoutCanRequestRegions() {
    List<HomeModel> models = List.of(
        new HomeModel("Alex", "alex", true, true, false, List.of(), List.of("eu-central-1"), List.of(),
                      false, true, false),
        new HomeModel("Alex", "alex", true, false, false, List.of(), List.of(), List.of(), true, true, false),
        noAccess("Alex", "alex"));

    for (HomeModel model : models) {
      List<String> data = callbackData(homeScreen.home(model));
      assertFalse(data.contains("REGIONS"), model + " -> " + data);
    }
  }

  @Test
  @DisplayName("5 §4.1: the Other region row does not change the home text")
  void otherRegionRowKeepsText() {
    HomeModel with = new HomeModel("Alex", "alex", true, true, false, List.of(), List.of(), List.of(),
                                   false, true, true);
    HomeModel without = new HomeModel("Alex", "alex", true, true, false, List.of(), List.of(), List.of(),
                                      false, true, false);

    assertEquals(homeScreen.home(without).template(), homeScreen.home(with).template());
    assertEquals(homeScreen.home(without).params(), homeScreen.home(with).params());
  }

  @Test
  @DisplayName("5 AC-1, §4.7: the Other region button renders in EN and RU from the real message files")
  void otherRegionButtonRendered() {
    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", false, true, false, List.of(), List.of(),
                                                  List.of(), false, true, true));
    MainMessagesTranslator messages = new MainMessagesTranslator();

    for (String language : List.of("en", "ru")) {
      assertTrue(ScreenTestSupport.renderedLabels(screen, language)
                     .contains("🌍 " + messages.value(language, "ui.button.other-region")),
                 language);
    }
  }

  @Test
  @DisplayName("2b AC-8: % in a host name is escaped as %% in the stop button label")
  void stopButtonEscapesPercent() {
    EcsNode node = stoppable("task-1", "100%-node", Region.EU_CENTRAL_1);

    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", true, false, false, List.of(node),
                                                  List.of(), List.of("task-1"), false, true, false));

    String label = screen.keyboard().getFirst().getFirst().label();
    assertEquals("🛑 100%%-node", label);
    assertEquals("🛑 100%-node", label.formatted());
  }

  @Test
  @DisplayName("AC-7: home template placeholders match params for every kind of model")
  void placeholdersMatchParams() {
    List<EcsNode> nodes = List.of(node("HEALTHY", "node-1", Region.EU_CENTRAL_1, "1.2.3.4"),
                                   node(null, null, null, null));
    List<HomeModel> models = List.of(
        noAccess("Alex", "alex"),
        noAccess(null, "alex"),
        noAccess(null, null),
        new HomeModel(null, null, true, true, true, nodes, List.of("eu-central-1", "ap-south-1"),
                      List.of(), false, true, true),
        new HomeModel("Alex", "alex", true, true, false, nodes, List.of(), List.of(), false, true, true),
        new HomeModel("Alex", "alex", true, false, false, List.of(), List.of(), List.of(), false, true, false),
        new HomeModel("Alex", "alex", false, true, false, List.of(), List.of("unknown-1"), List.of(),
                      false, true, true),
        new HomeModel("Alex", "alex", false, false, false, List.of(), List.of(), List.of(), true, true, false));

    models.forEach(model -> assertPlaceholdersMatchParams(homeScreen.home(model)));
  }

  private static HomeModel noAccess(String firstName, String username) {
    return new HomeModel(firstName, username, false, false, false, List.of(), List.of(), List.of(),
                         false, false, false);
  }

  private static List<String> callbackData(Screen screen) {
    return screen.keyboard().stream()
        .flatMap(List::stream)
        .map(Button::callbackData)
        .filter(data -> data != null)
        .toList();
  }

  private static EcsNode node(String state, String hostName, Region region, String publicIp) {
    return EcsNode.builder()
        .id("task-0")
        .state(state)
        .hostName(hostName)
        .region(region)
        .publicIp(publicIp)
        .build();
  }

  private static EcsNode stoppable(String id, String hostName, Region region) {
    return EcsNode.builder()
        .id(id)
        .state("HEALTHY")
        .hostName(hostName)
        .region(region)
        .publicIp("1.2.3.4")
        .build();
  }

  private static Button region(String label, String regionId) {
    return new Button(label, "RUN:" + regionId, null);
  }

}
