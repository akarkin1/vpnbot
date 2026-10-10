package org.github.akarkin1.ui.screen;

import org.github.akarkin1.ecs.TaskInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;

import java.util.List;

import static org.github.akarkin1.ui.screen.ScreenTestSupport.LINKS;
import static org.github.akarkin1.ui.screen.ScreenTestSupport.MENU;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LaunchScreensTest {

  private static final String REGION = "eu-central-1";
  private static final String TASK_ID = "0123456789abcdef0123456789abcdef";
  private static final String READY_TEMPLATE =
      "%s <b>%s</b> · %s\n🌐 <code>%s</code>\n⏱ ${ui.node.auto-stop}\n\n${ui.node.connect-hint}";

  private final LaunchScreens screens = new LaunchScreens(ScreenTestSupport.regionLabels());

  @Test
  @DisplayName("AC-8: starting shows the region label and the submitting step, no keyboard")
  void starting() {
    Screen screen = screens.starting(REGION);

    assertEquals("🚀 ${ui.launch.starting} %s…\n⏳ ${ui.launch.step.submitting}", screen.template());
    assertEquals(List.of("🇩🇪 Frankfurt"), screen.params());
    assertEquals(List.of(), screen.keyboard());
  }

  @Test
  @DisplayName("2a AC-4: waiting shows the region label and the waiting step, with a menu button")
  void waiting() {
    Screen screen = screens.waiting(REGION);

    assertEquals("🚀 ${ui.launch.starting} %s…\n✅ ${ui.launch.step.submitted}\n⏳ ${ui.launch.step.waiting}",
                 screen.template());
    assertEquals(List.of("🇩🇪 Frankfurt"), screen.params());
    assertEquals(List.of(List.of(MENU)), screen.keyboard());
  }

  @Test
  @DisplayName("2a AC-4, 2b AC-7: ready shows the node card with links, then stop (STOP:<region>:<task>) and menu in the last row")
  void ready() {
    Screen screen = screens.ready("node-1", REGION, "1.2.3.4", TASK_ID);

    assertEquals(READY_TEMPLATE, screen.template());
    assertEquals(List.of("🟢", "node-1", "🇩🇪 Frankfurt", "1.2.3.4"), screen.params());
    assertEquals(List.of(LINKS, List.of(stop("STOP:eu-central-1:" + TASK_ID), MENU)),
                 screen.keyboard());
  }

  @Test
  @DisplayName("2b AC-7: ready with the {{TASK_ID}} placeholder puts it into the stop callback")
  void readyWithTaskIdPlaceholder() {
    Screen screen = screens.ready("{{HOSTNAME}}", REGION, "{{PUBLIC_IP}}", "{{TASK_ID}}");

    assertEquals(List.of(stop("STOP:eu-central-1:{{TASK_ID}}"), MENU), screen.keyboard().get(1));
  }

  @Test
  @DisplayName("2a AC-4: ready shows — for a missing host and IP")
  void readyWithMissingValues() {
    assertEquals(List.of("🟢", "—", "🇩🇪 Frankfurt", "—"),
                 screens.ready(null, REGION, null, TASK_ID).params());
    assertEquals(List.of("🟢", "—", "🇩🇪 Frankfurt", "—"),
                 screens.ready(" ", REGION, "", TASK_ID).params());
  }

  @Test
  @DisplayName("2b AC-7: existingNodes shows the region, one use button per node (USE:<region>:<task>), then start-another (RUN_NEW:<region>), then menu")
  void existingNodes() {
    List<TaskInfo> nodes = List.of(node("task-a", "alex-frankfurt-1"), node("task-b", "alex-frankfurt-2"));

    Screen screen = screens.existingNodes(REGION, nodes);

    assertEquals("ℹ️ ${ui.reuse.existing}\n📍 %s", screen.template());
    assertEquals(List.of("🇩🇪 Frankfurt"), screen.params());
    assertEquals(List.of(
                     List.of(new Button("📋 ${ui.button.use} alex-frankfurt-1", "USE:eu-central-1:task-a", null)),
                     List.of(new Button("📋 ${ui.button.use} alex-frankfurt-2", "USE:eu-central-1:task-b", null)),
                     List.of(new Button("🚀 ${ui.button.start-another}", "RUN_NEW:eu-central-1", null)),
                     List.of(MENU)),
                 screen.keyboard());
  }

  @Test
  @DisplayName("2b AC-7: existingNodes escapes % in host names as %% (the translator formats labels)")
  void existingNodesEscapesPercent() {
    Screen screen = screens.existingNodes(REGION, List.of(node("task-a", "100%-node")));

    String label = screen.keyboard().getFirst().getFirst().label();
    assertEquals("📋 ${ui.button.use} 100%%-node", label);
    assertEquals("📋 ${ui.button.use} 100%-node", label.formatted());
  }

  @Test
  @DisplayName("2a AC-4: idleWarning shows the host, no keyboard")
  void idleWarning() {
    Screen screen = screens.idleWarning("node-1");

    assertEquals("⚠️ <b>%s</b> ${ui.node.idle-warning}", screen.template());
    assertEquals(List.of("node-1"), screen.params());
    assertEquals(List.of(), screen.keyboard());
    assertEquals(List.of("—"), screens.idleWarning(null).params());
  }

  @Test
  @DisplayName("stop-in-place AC-1: stopped is a card with host and region label, the idle-stop reason, start-again (RUN:<region>) and menu in one row")
  void stopped() {
    Screen screen = screens.stopped("node-1", REGION);

    assertEquals("⚪ <b>%s</b> · %s\n🛑 ${ui.node.stopped-idle}", screen.template());
    assertEquals(List.of("node-1", "🇩🇪 Frankfurt"), screen.params());
    assertEquals(List.of(List.of(startAgain("RUN:eu-central-1"), MENU)), screen.keyboard());
  }

  @Test
  @DisplayName("stop-in-place AC-1: stopped shows — for a missing host")
  void stoppedWithMissingValues() {
    assertEquals(List.of("—", "🇩🇪 Frankfurt"), screens.stopped(null, REGION).params());
    assertEquals(List.of("—", "🇩🇪 Frankfurt"), screens.stopped(" ", REGION).params());
  }

  @Test
  @DisplayName("stop-in-place AC-1: stopped with the {{HOSTNAME}} placeholder keeps it as the host param")
  void stoppedWithHostnamePlaceholder() {
    Screen screen = screens.stopped("{{HOSTNAME}}", REGION);

    assertEquals(List.of("{{HOSTNAME}}", "🇩🇪 Frankfurt"), screen.params());
    assertEquals(List.of(List.of(startAgain("RUN:eu-central-1"), MENU)), screen.keyboard());
  }

  @Test
  @DisplayName("stop-in-place AC-2: stoppedCard keeps its template and params, now with start-again (RUN:<region>) and menu in one row")
  void stoppedCard() {
    Screen screen = screens.stoppedCard("node-1", REGION);

    assertEquals("⚪ <b>%s</b> · %s\n🛑 ${ui.node.stopped}", screen.template());
    assertEquals(List.of("node-1", "🇩🇪 Frankfurt"), screen.params());
    assertEquals(List.of(List.of(startAgain("RUN:eu-central-1"), MENU)), screen.keyboard());
    assertEquals(List.of("—", "🇩🇪 Frankfurt"), screens.stoppedCard("", REGION).params());
  }

  @Test
  @DisplayName("stop-in-place AC-1, AC-2: stopped and stoppedCard have the same keyboard, also for another region")
  void stoppedScreensShareKeyboard() {
    Screen stopped = screens.stopped("node-1", "eu-west-2");
    Screen stoppedCard = screens.stoppedCard("node-1", "eu-west-2");

    assertEquals(List.of(List.of(startAgain("RUN:eu-west-2"), MENU)), stopped.keyboard());
    assertEquals(stopped.keyboard(), stoppedCard.keyboard());
  }

  @Test
  @DisplayName("AC-8: failed shows the region, try-again (RUN:<id>) and menu buttons")
  void failed() {
    Screen screen = screens.failed(REGION);

    assertEquals("🔴 ${ui.launch.failed}\n📍 %s", screen.template());
    assertEquals(List.of("🇩🇪 Frankfurt"), screen.params());
    assertEquals(List.of(List.of(new Button("🔁 ${ui.button.try-again}", "RUN:eu-central-1", null), MENU)),
                 screen.keyboard());
  }

  @Test
  @DisplayName("AC-8: regionUnavailable has no params and a menu button")
  void regionUnavailable() {
    Screen screen = screens.regionUnavailable();

    assertEquals("⚠️ ${ui.launch.region-unavailable}", screen.template());
    assertEquals(List.of(), screen.params());
    assertEquals(List.of(List.of(MENU)), screen.keyboard());
  }

  @Test
  @DisplayName("AC-8: notAllowed has no params and a menu button")
  void notAllowed() {
    Screen screen = screens.notAllowed();

    assertEquals("⛔ ${ui.launch.not-allowed}", screen.template());
    assertEquals(List.of(), screen.params());
    assertEquals(List.of(List.of(MENU)), screen.keyboard());
  }

  @Test
  @DisplayName("AC-8: link constants point to the Tailscale docs")
  void links() {
    assertEquals("https://tailscale.com/docs/features/exit-nodes", Links.EXIT_NODE_GUIDE);
    assertEquals("https://tailscale.com/download", Links.DOWNLOAD);
  }

  @Test
  @DisplayName("2a AC-4, 2b AC-7, stop-in-place AC-1: launch templates' placeholders match params for every screen, no null params")
  void placeholdersMatchParams() {
    List.of(screens.starting(REGION),
            screens.starting("unknown-1"),
            screens.waiting(REGION),
            screens.ready("node-1", REGION, "1.2.3.4", TASK_ID),
            screens.ready(null, "unknown-1", null, TASK_ID),
            screens.existingNodes(REGION, List.of(node("task-a", "alex-frankfurt-1"))),
            screens.existingNodes("unknown-1", List.of(node("task-a", "alex-frankfurt-1"))),
            screens.idleWarning("node-1"),
            screens.idleWarning(null),
            screens.stopped("node-1", REGION),
            screens.stopped(null, "unknown-1"),
            screens.stoppedCard("node-1", REGION),
            screens.stoppedCard(null, "unknown-1"),
            screens.failed(REGION),
            screens.regionUnavailable(),
            screens.notAllowed())
        .forEach(ScreenTestSupport::assertPlaceholdersMatchParams);
  }

  private static Button startAgain(String callbackData) {
    return new Button("🚀 ${ui.button.start-again}", callbackData, null);
  }

  private static Button stop(String callbackData) {
    return new Button("🛑 ${ui.button.stop}", callbackData, null);
  }

  private static TaskInfo node(String id, String hostName) {
    return TaskInfo.builder()
        .id(id)
        .hostName(hostName)
        .region(Region.EU_CENTRAL_1)
        .runBy("alex")
        .build();
  }

}
