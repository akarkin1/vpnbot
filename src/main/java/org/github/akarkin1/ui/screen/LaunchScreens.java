package org.github.akarkin1.ui.screen;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.ecs.TaskInfo;
import org.github.akarkin1.ui.UiAction;

import java.util.List;

import static org.github.akarkin1.ui.screen.CommonButtons.LINKS;
import static org.github.akarkin1.ui.screen.CommonButtons.MENU;
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
    return new Screen("%s <b>%s</b> · %s\n🌐 <code>%s</code>\n⏱ ${ui.node.auto-stop}\n\n${ui.node.connect-hint}",
                      List.of("🟢", orDash(hostName), label(regionId), orDash(publicIp)),
                      List.of(LINKS, List.of(MENU)));
  }

  public Screen idleWarning(String hostName) {
    return new Screen("⚠️ <b>%s</b> ${ui.node.idle-warning}",
                      List.of(orDash(hostName)),
                      List.of());
  }

  public Screen stopped(String hostName, String regionId) {
    Button startAgain = Button.action("🚀 ${ui.button.start-again}", UiAction.run(regionId));
    return new Screen("🛑 <b>%s</b> ${ui.node.stopped-idle}",
                      List.of(orDash(hostName)),
                      List.of(List.of(startAgain, MENU)));
  }

  public Screen stoppedCard(String hostName, String regionId) {
    return new Screen("⚪ <b>%s</b> · %s\n🛑 ${ui.node.stopped}",
                      List.of(orDash(hostName), label(regionId)),
                      List.of());
  }

  public Screen failed(String regionId) {
    Button tryAgain = Button.action("🔁 ${ui.button.try-again}", UiAction.run(regionId));
    return new Screen("🔴 ${ui.launch.failed}\n📍 %s",
                      List.of(regionLabels.label(regionId)),
                      List.of(List.of(tryAgain, MENU)));
  }

  public Screen existingNodes(String regionId, List<TaskInfo> nodes) {
    throw new UnsupportedOperationException("Not implemented yet");
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
