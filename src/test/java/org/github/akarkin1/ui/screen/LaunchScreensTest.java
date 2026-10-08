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
  @DisplayName("AC-8: waiting shows the region label and the waiting step, no keyboard")
  void waiting() {
    Screen screen = screens.waiting(REGION);

    assertEquals("🚀 ${ui.launch.starting} %s…\n✅ ${ui.launch.step.submitted}\n⏳ ${ui.launch.step.waiting}",
                 screen.template());
    assertEquals(List.of("🇩🇪 Frankfurt"), screen.params());
    assertEquals(List.of(), screen.keyboard());
  }

  @Test
  @DisplayName("AC-8: ready shows the node card with links and menu")
  void ready() {
    TaskInfo node = TaskInfo.builder()
        .state("HEALTHY")
        .hostName("node-1")
        .region(Region.EU_CENTRAL_1)
        .publicIp("1.2.3.4")
        .build();

    Screen screen = screens.ready(node);

    assertEquals(READY_TEMPLATE, screen.template());
    assertEquals(List.of("🟢", "node-1", "🇩🇪 Frankfurt", "1.2.3.4"), screen.params());
    assertEquals(List.of(LINKS, List.of(MENU)), screen.keyboard());
  }

  @Test
  @DisplayName("AC-8: ready shows — for missing node values")
  void readyWithMissingValues() {
    Screen screen = screens.ready(TaskInfo.builder().build());

    assertEquals(READY_TEMPLATE, screen.template());
    assertEquals(List.of("🟡", "—", "—", "—"), screen.params());
  }

  @Test
  @DisplayName("AC-8: stillStarting shows the region and a menu button")
  void stillStarting() {
    Screen screen = screens.stillStarting(REGION);

    assertEquals("🟡 ${ui.launch.still-starting}\n📍 %s", screen.template());
    assertEquals(List.of("🇩🇪 Frankfurt"), screen.params());
    assertEquals(List.of(List.of(MENU)), screen.keyboard());
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
  @DisplayName("AC-7: launch templates' placeholders match params for every screen")
  void placeholdersMatchParams() {
    List.of(screens.starting(REGION),
            screens.starting("unknown-1"),
            screens.waiting(REGION),
            screens.ready(TaskInfo.builder().state("HEALTHY").hostName("node-1")
                              .region(Region.EU_CENTRAL_1).publicIp("1.2.3.4").build()),
            screens.ready(TaskInfo.builder().build()),
            screens.stillStarting(REGION),
            screens.failed(REGION),
            screens.regionUnavailable(),
            screens.notAllowed())
        .forEach(ScreenTestSupport::assertPlaceholdersMatchParams);
  }

}
