package org.github.akarkin1.ui.screen;

import org.github.akarkin1.ecs.TaskInfo;
import org.github.akarkin1.ui.NodeRef;
import org.github.akarkin1.ui.UiAction;

import java.util.List;

import static org.github.akarkin1.ui.screen.CommonButtons.MENU;
import static org.github.akarkin1.ui.screen.NodeFormat.orDash;

public class NodeScreens {

  public Screen stopping(TaskInfo node) {
    return new Screen("🛑 <b>%s</b> ${ui.node.stopping}",
                      List.of(orDash(node.getHostName())),
                      List.of(List.of(MENU)));
  }

  public Screen confirmStop(TaskInfo node) {
    NodeRef nodeRef = new NodeRef(node.getRegion().id(), node.getId());
    Button yes = Button.action("🛑 ${ui.button.yes-stop}", UiAction.confirmStop(nodeRef));
    Button cancel = Button.action("↩️ ${ui.button.cancel}", UiAction.home());
    return new Screen("❓ ${ui.stop.confirm} <b>%s</b> (%s)?",
                      List.of(orDash(node.getHostName()), "@" + node.getRunBy()),
                      List.of(List.of(yes, cancel)));
  }

  public Screen alreadyStopped() {
    return new Screen("⚪ ${ui.node.already-stopped}", List.of(), List.of(List.of(MENU)));
  }

  public Screen notAllowed() {
    return new Screen("⛔ ${ui.stop.not-allowed}", List.of(), List.of(List.of(MENU)));
  }

  public Screen stoppedByAdmin(TaskInfo node) {
    Button startAgain = Button.action("🚀 ${ui.button.start-again}", UiAction.run(node.getRegion().id()));
    return new Screen("🛑 <b>%s</b> ${ui.node.stopped-by-admin}",
                      List.of(orDash(node.getHostName())),
                      List.of(List.of(startAgain, MENU)));
  }

}
