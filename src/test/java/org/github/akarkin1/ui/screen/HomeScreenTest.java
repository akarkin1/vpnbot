package org.github.akarkin1.ui.screen;

import org.github.akarkin1.ecs.TaskInfo;
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
  @DisplayName("AC-4: user without LIST_NODES and RUN_NODES sees no-access text")
  void noAccessText() {
    Screen screen = homeScreen.home(noAccess("Alex", "alex"));

    assertEquals(GREETING + "\n\n${ui.home.no-access}", screen.template());
  }

  @Test
  @DisplayName("AC-4: user without LIST_NODES and RUN_NODES gets only the Refresh/Help row")
  void noAccessKeyboard() {
    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", false, false, false, List.of(),
                                                  List.of("eu-central-1")));

    assertEquals(List.of(REFRESH_HELP), screen.keyboard());
  }

  @Test
  @DisplayName("AC-5: nodes are listed with status emoji, host, region label and IP, — for nulls")
  void listsNodes() {
    List<TaskInfo> nodes = List.of(
        node("HEALTHY", "node-1", Region.EU_CENTRAL_1, "1.2.3.4"),
        node("UNHEALTHY", "node-2", Region.US_EAST_1, "5.6.7.8"),
        node(null, null, null, null));

    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", true, false, false, nodes, List.of()));

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
    List<TaskInfo> nodes = List.of(node("PROVISIONING", " ", Region.EU_CENTRAL_1, ""));

    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", true, false, false, nodes, List.of()));

    assertEquals(List.of("Alex", "🟡", "—", "🇩🇪 Frankfurt", "—"), screen.params());
  }

  @Test
  @DisplayName("AC-5: empty node list shows ui.home.no-nodes")
  void noNodes() {
    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", true, false, false, List.of(),
                                                  List.of()));

    assertEquals(GREETING + "\n\n<b>${ui.home.your-nodes}</b>\n${ui.home.no-nodes}",
                 screen.template());
    assertEquals(List.of(REFRESH_HELP), screen.keyboard());
  }

  @Test
  @DisplayName("AC-5: allNodes uses ui.home.all-nodes")
  void allNodesTitle() {
    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", true, false, true, List.of(),
                                                  List.of()));

    assertEquals(GREETING + "\n\n<b>${ui.home.all-nodes}</b>\n${ui.home.no-nodes}",
                 screen.template());
  }

  @Test
  @DisplayName("AC-6: region buttons are sorted by city (case-insensitive), 3 per row, callback RUN:<id>")
  void regionButtons() {
    List<String> regionIds = List.of("us-east-1", "eu-central-1", "ap-northeast-1", "eu-west-2",
                                     "ap-south-1", "eu-north-1", "sa-east-1");

    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", false, true, false, List.of(),
                                                  regionIds));

    assertEquals(List.of(
                     List.of(region("🇩🇪 Frankfurt", "eu-central-1"),
                             region("🇬🇧 London", "eu-west-2"),
                             region("🇮🇳 mumbai", "ap-south-1")),
                     List.of(region("🇺🇸 N. Virginia", "us-east-1"),
                             region("🇧🇷 Sao Paulo", "sa-east-1"),
                             region("🇸🇪 Stockholm", "eu-north-1")),
                     List.of(region("🇯🇵 Tokyo", "ap-northeast-1")),
                     REFRESH_HELP),
                 screen.keyboard());
  }

  @Test
  @DisplayName("AC-6: user with RUN_NODES sees the start-node section")
  void startNodeSection() {
    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", false, true, false, List.of(),
                                                  List.of("eu-central-1")));

    assertEquals(GREETING + "\n\n<b>${ui.home.start-node}</b>", screen.template());
  }

  @Test
  @DisplayName("AC-6: no region buttons and no start-node section without RUN_NODES")
  void noRegionButtonsWithoutRunNodes() {
    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", true, false, false, List.of(),
                                                  List.of("eu-central-1", "eu-west-2")));

    assertEquals(List.of(REFRESH_HELP), screen.keyboard());
    assertFalse(screen.template().contains("${ui.home.start-node}"), screen.template());
  }

  @Test
  @DisplayName("AC-6: empty region list shows ui.home.no-regions")
  void noRegions() {
    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", false, true, false, List.of(),
                                                  List.of()));

    assertEquals(GREETING + "\n\n<b>${ui.home.start-node}</b>\n${ui.home.no-regions}",
                 screen.template());
    assertEquals(List.of(REFRESH_HELP), screen.keyboard());
  }

  @Test
  @DisplayName("AC-5, AC-6: nodes section comes before the start-node section")
  void sectionOrder() {
    List<TaskInfo> nodes = List.of(node("HEALTHY", "node-1", Region.EU_CENTRAL_1, "1.2.3.4"));

    Screen screen = homeScreen.home(new HomeModel("Alex", "alex", true, true, false, nodes,
                                                  List.of("eu-central-1")));

    assertEquals(GREETING + "\n\n<b>${ui.home.your-nodes}</b>" + NODE_LINE
                 + "\n\n<b>${ui.home.start-node}</b>",
                 screen.template());
    assertEquals(List.of(List.of(region("🇩🇪 Frankfurt", "eu-central-1")), REFRESH_HELP),
                 screen.keyboard());
  }

  @Test
  @DisplayName("AC-7: home template placeholders match params for every kind of model")
  void placeholdersMatchParams() {
    List<TaskInfo> nodes = List.of(node("HEALTHY", "node-1", Region.EU_CENTRAL_1, "1.2.3.4"),
                                   node(null, null, null, null));
    List<HomeModel> models = List.of(
        noAccess("Alex", "alex"),
        noAccess(null, "alex"),
        noAccess(null, null),
        new HomeModel(null, null, true, true, true, nodes, List.of("eu-central-1", "ap-south-1")),
        new HomeModel("Alex", "alex", true, true, false, nodes, List.of()),
        new HomeModel("Alex", "alex", true, false, false, List.of(), List.of()),
        new HomeModel("Alex", "alex", false, true, false, List.of(), List.of("unknown-1")));

    models.forEach(model -> assertPlaceholdersMatchParams(homeScreen.home(model)));
  }

  private static HomeModel noAccess(String firstName, String username) {
    return new HomeModel(firstName, username, false, false, false, List.of(), List.of());
  }

  private static TaskInfo node(String state, String hostName, Region region, String publicIp) {
    return TaskInfo.builder()
        .state(state)
        .hostName(hostName)
        .region(region)
        .publicIp(publicIp)
        .build();
  }

  private static Button region(String label, String regionId) {
    return new Button(label, "RUN:" + regionId, null);
  }

}
