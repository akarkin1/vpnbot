package org.github.akarkin1.ui.screen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
  @DisplayName("2a AC-4: waiting shows the region label and the waiting step, with a menu button")
  void waiting() {
    Screen screen = screens.waiting(REGION);

    assertEquals("🚀 ${ui.launch.starting} %s…\n✅ ${ui.launch.step.submitted}\n⏳ ${ui.launch.step.waiting}",
                 screen.template());
    assertEquals(List.of("🇩🇪 Frankfurt"), screen.params());
    assertEquals(List.of(List.of(MENU)), screen.keyboard());
  }

  @Test
  @DisplayName("2a AC-4: ready shows the node card with links and menu")
  void ready() {
    Screen screen = screens.ready("node-1", REGION, "1.2.3.4");

    assertEquals(READY_TEMPLATE, screen.template());
    assertEquals(List.of("🟢", "node-1", "🇩🇪 Frankfurt", "1.2.3.4"), screen.params());
    assertEquals(List.of(LINKS, List.of(MENU)), screen.keyboard());
  }

  @Test
  @DisplayName("2a AC-4: ready shows — for a missing host and IP")
  void readyWithMissingValues() {
    assertEquals(List.of("🟢", "—", "🇩🇪 Frankfurt", "—"), screens.ready(null, REGION, null).params());
    assertEquals(List.of("🟢", "—", "🇩🇪 Frankfurt", "—"), screens.ready(" ", REGION, "").params());
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
  @DisplayName("2a AC-4: stopped shows the host with start-again (RUN:<id>) and menu buttons")
  void stopped() {
    Screen screen = screens.stopped("node-1", REGION);

    assertEquals("🛑 <b>%s</b> ${ui.node.stopped-idle}", screen.template());
    assertEquals(List.of("node-1"), screen.params());
    assertEquals(List.of(List.of(new Button("🚀 ${ui.button.start-again}", "RUN:eu-central-1", null), MENU)),
                 screen.keyboard());
    assertEquals(List.of("—"), screens.stopped(null, REGION).params());
  }

  @Test
  @DisplayName("2a AC-4: stoppedCard shows the host and region label, no keyboard")
  void stoppedCard() {
    Screen screen = screens.stoppedCard("node-1", REGION);

    assertEquals("⚪ <b>%s</b> · %s\n🛑 ${ui.node.stopped}", screen.template());
    assertEquals(List.of("node-1", "🇩🇪 Frankfurt"), screen.params());
    assertEquals(List.of(), screen.keyboard());
    assertEquals(List.of("—", "🇩🇪 Frankfurt"), screens.stoppedCard("", REGION).params());
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
  @DisplayName("2a AC-4: launch templates' placeholders match params for every screen, no null params")
  void placeholdersMatchParams() {
    List.of(screens.starting(REGION),
            screens.starting("unknown-1"),
            screens.waiting(REGION),
            screens.ready("node-1", REGION, "1.2.3.4"),
            screens.ready(null, "unknown-1", null),
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

}
