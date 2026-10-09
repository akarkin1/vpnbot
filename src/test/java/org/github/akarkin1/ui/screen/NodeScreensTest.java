package org.github.akarkin1.ui.screen;

import org.github.akarkin1.ecs.TaskInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;

import java.util.List;

import static org.github.akarkin1.ui.screen.ScreenTestSupport.MENU;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeScreensTest {

  private static final String TASK_ID = "0123456789abcdef0123456789abcdef";
  private static final TaskInfo NODE = TaskInfo.builder()
      .id(TASK_ID)
      .hostName("alex-frankfurt-1")
      .region(Region.EU_CENTRAL_1)
      .publicIp("1.2.3.4")
      .runBy("alex")
      .chatId("100")
      .languageCode("ru")
      .build();

  private final NodeScreens screens = new NodeScreens();

  @Test
  @DisplayName("2b AC-7: stopping shows the host with a menu button")
  void stopping() {
    Screen screen = screens.stopping(NODE);

    assertEquals("🛑 <b>%s</b> ${ui.node.stopping}", screen.template());
    assertEquals(List.of("alex-frankfurt-1"), screen.params());
    assertEquals(List.of(List.of(MENU)), screen.keyboard());
  }

  @Test
  @DisplayName("2b AC-7 (D-1): confirmStop asks about host and @owner, one row: yes-stop (STOP_CONFIRM:<region>:<task>) and cancel (HOME)")
  void confirmStop() {
    Screen screen = screens.confirmStop(NODE);

    assertEquals("❓ ${ui.stop.confirm} <b>%s</b> (%s)?", screen.template());
    assertEquals(List.of("alex-frankfurt-1", "@alex"), screen.params());
    assertEquals(List.of(List.of(
                     new Button("🛑 ${ui.button.yes-stop}", "STOP_CONFIRM:eu-central-1:" + TASK_ID, null),
                     new Button("↩️ ${ui.button.cancel}", "HOME", null))),
                 screen.keyboard());
  }

  @Test
  @DisplayName("2b D-11: confirmStop shows — instead of @null for a task without RunBy")
  void confirmStopWithoutRunBy() {
    TaskInfo untagged = TaskInfo.builder()
        .id(TASK_ID)
        .hostName("legacy-frankfurt-1")
        .region(Region.EU_CENTRAL_1)
        .build();

    Screen screen = screens.confirmStop(untagged);

    assertEquals(List.of("legacy-frankfurt-1", "—"), screen.params());
  }

  @Test
  @DisplayName("2b AC-7: alreadyStopped has no params and a menu button")
  void alreadyStopped() {
    Screen screen = screens.alreadyStopped();

    assertEquals("⚪ ${ui.node.already-stopped}", screen.template());
    assertEquals(List.of(), screen.params());
    assertEquals(List.of(List.of(MENU)), screen.keyboard());
  }

  @Test
  @DisplayName("2b AC-7: notAllowed has no params and a menu button")
  void notAllowed() {
    Screen screen = screens.notAllowed();

    assertEquals("⛔ ${ui.stop.not-allowed}", screen.template());
    assertEquals(List.of(), screen.params());
    assertEquals(List.of(List.of(MENU)), screen.keyboard());
  }

  @Test
  @DisplayName("2b AC-7 (D-1): stoppedByAdmin shows the host, one row: start-again (RUN:<region>) and menu")
  void stoppedByAdmin() {
    Screen screen = screens.stoppedByAdmin(NODE);

    assertEquals("🛑 <b>%s</b> ${ui.node.stopped-by-admin}", screen.template());
    assertEquals(List.of("alex-frankfurt-1"), screen.params());
    assertEquals(List.of(List.of(new Button("🚀 ${ui.button.start-again}", "RUN:eu-central-1", null),
                                 MENU)),
                 screen.keyboard());
  }

  @Test
  @DisplayName("2b AC-7: node screens' placeholders match params, no null params (also without a host name)")
  void placeholdersMatchParams() {
    TaskInfo withoutHost = TaskInfo.builder()
        .id(TASK_ID)
        .region(Region.US_EAST_1)
        .runBy("bob")
        .build();

    List.of(screens.stopping(NODE),
            screens.stopping(withoutHost),
            screens.confirmStop(NODE),
            screens.confirmStop(withoutHost),
            screens.alreadyStopped(),
            screens.notAllowed(),
            screens.stoppedByAdmin(NODE),
            screens.stoppedByAdmin(withoutHost))
        .forEach(ScreenTestSupport::assertPlaceholdersMatchParams);
  }

}
