package org.github.akarkin1.ui.screen;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.ecs.TaskInfo;
import org.github.akarkin1.ui.NodeRef;
import org.github.akarkin1.ui.UiAction;

import java.util.ArrayList;
import java.util.List;

import static org.github.akarkin1.ui.screen.CommonButtons.LINKS;
import static org.github.akarkin1.ui.screen.CommonButtons.MENU;
import static org.github.akarkin1.ui.screen.NodeFormat.escapePercent;
import static org.github.akarkin1.ui.screen.NodeFormat.orDash;

@RequiredArgsConstructor
public class LaunchScreens {

  private final RegionLabels regionLabels;

  public Screen starting(String regionId) {
    return new Screen("🚀 ${ui.launch.starting} %s…\n⏳ ${ui.launch.step.submitting}",
                      List.of(regionLabels.label(regionId)),
                      List.of());
  }

  public Screen waiting(String regionId) {
    return new Screen("🚀 ${ui.launch.starting} %s…\n✅ ${ui.launch.step.submitted}\n⏳ ${ui.launch.step.waiting}",
                      List.of(regionLabels.label(regionId)),
                      List.of(List.of(MENU)));
  }

  public Screen ready(String hostName, String regionId, String publicIp, String taskId) {
    Button stop = Button.action("🛑 ${ui.button.stop}", UiAction.stop(new NodeRef(regionId, taskId)));
    return new Screen("%s <b>%s</b> · %s\n🌐 <code>%s</code>\n⏱ ${ui.node.auto-stop}\n\n${ui.node.connect-hint}",
                      List.of("🟢", orDash(hostName), label(regionId), orDash(publicIp)),
                      List.of(LINKS, List.of(stop, MENU)));
  }

  public Screen idleWarning(String hostName) {
    return new Screen("⚠️ <b>%s</b> ${ui.node.idle-warning}",
                      List.of(orDash(hostName)),
                      List.of());
  }

  public Screen stopped(String hostName, String regionId) {
    return new Screen("⚪ <b>%s</b> · %s\n🛑 ${ui.node.stopped-idle}",
                      List.of(orDash(hostName), label(regionId)),
                      stoppedKeyboard(regionId));
  }

  public Screen stoppedCard(String hostName, String regionId) {
    return new Screen("⚪ <b>%s</b> · %s\n🛑 ${ui.node.stopped}",
                      List.of(orDash(hostName), label(regionId)),
                      stoppedKeyboard(regionId));
  }

  private static List<List<Button>> stoppedKeyboard(String regionId) {
    Button startAgain = Button.action("🚀 ${ui.button.start-again}", UiAction.run(regionId));
    return List.of(List.of(startAgain, MENU));
  }

  public Screen failed(String regionId) {
    Button tryAgain = Button.action("🔁 ${ui.button.try-again}", UiAction.run(regionId));
    return new Screen("🔴 ${ui.launch.failed}\n📍 %s",
                      List.of(regionLabels.label(regionId)),
                      List.of(List.of(tryAgain, MENU)));
  }

  public Screen existingNodes(String regionId, List<TaskInfo> nodes) {
    List<List<Button>> keyboard = new ArrayList<>();
    for (TaskInfo node : nodes) {
      String label = "📋 ${ui.button.use} " + escapePercent(orDash(node.getHostName()));
      keyboard.add(List.of(Button.action(label, UiAction.use(new NodeRef(regionId, node.getId())))));
    }
    keyboard.add(List.of(Button.action("🚀 ${ui.button.start-another}", UiAction.runNew(regionId))));
    keyboard.add(List.of(MENU));
    return new Screen("ℹ️ ${ui.reuse.existing}\n📍 %s",
                      List.of(regionLabels.label(regionId)),
                      keyboard);
  }

  public Screen regionUnavailable() {
    return new Screen("⚠️ ${ui.launch.region-unavailable}", List.of(), List.of(List.of(MENU)));
  }

  public Screen notAllowed() {
    return new Screen("⛔ ${ui.launch.not-allowed}", List.of(), List.of(List.of(MENU)));
  }

  private String label(String regionId) {
    return orDash(regionId == null ? null : regionLabels.label(regionId));
  }

}
