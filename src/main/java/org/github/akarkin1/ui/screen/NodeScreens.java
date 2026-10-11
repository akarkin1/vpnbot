package org.github.akarkin1.ui.screen;

import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.ecs.EcsNode;
import org.github.akarkin1.ui.NodeRef;
import org.github.akarkin1.ui.UiAction;

import java.util.List;

import static org.github.akarkin1.ui.screen.CommonButtons.MENU;
import static org.github.akarkin1.ui.screen.NodeFormat.orDash;

public class NodeScreens {

  public Screen stopping(EcsNode node) {
    return new Screen("🛑 <b>%s</b> ${ui.node.stopping}",
                      List.of(orDash(node.getHostName())),
                      List.of(List.of(MENU)));
  }

  public Screen confirmStop(EcsNode node) {
    NodeRef nodeRef = new NodeRef(node.getRegion().id(), node.getId());
    Button yes = Button.action("🛑 ${ui.button.yes-stop}", UiAction.confirmStop(nodeRef));
    Button cancel = Button.action("↩️ ${ui.button.cancel}", UiAction.home());
    String owner = owner(node);
    return new Screen("❓ ${ui.stop.confirm} <b>%s</b> (%s)?",
                      List.of(orDash(node.getHostName()), orDash(owner)),
                      List.of(List.of(yes, cancel)));
  }

  /**
   * {@code @RunByName}; else the {@code RunBy} tag: an id as is, or {@code @username} for nodes
   * started before Phase 4.
   */
  private static String owner(EcsNode node) {
    if (StringUtils.isNotBlank(node.getRunByName())) {
      return "@" + node.getRunByName();
    }
    String runBy = node.getRunBy();
    if (StringUtils.isBlank(runBy)) {
      return null;
    }
    return StringUtils.isNumeric(runBy) ? runBy : "@" + runBy;
  }

  public Screen alreadyStopped() {
    return new Screen("⚪ ${ui.node.already-stopped}", List.of(), List.of(List.of(MENU)));
  }

  public Screen notAllowed() {
    return new Screen("⛔ ${ui.stop.not-allowed}", List.of(), List.of(List.of(MENU)));
  }

  public Screen stoppedByAdmin(EcsNode node) {
    Button startAgain = Button.action("🚀 ${ui.button.start-again}", UiAction.run(node.getRegion().id()));
    return new Screen("🛑 <b>%s</b> ${ui.node.stopped-by-admin}",
                      List.of(orDash(node.getHostName())),
                      List.of(List.of(startAgain, MENU)));
  }

}
