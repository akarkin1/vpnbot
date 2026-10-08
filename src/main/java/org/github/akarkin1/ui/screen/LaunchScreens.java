package org.github.akarkin1.ui.screen;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.ecs.TaskInfo;
import org.github.akarkin1.ui.UiAction;

import java.util.List;

@RequiredArgsConstructor
public class LaunchScreens {

  private static final Button MENU = Button.action("🏠 ${ui.button.menu}", UiAction.home());
  private static final List<Button> LINKS = List.of(
      Button.link("📖 ${ui.button.exit-node-guide}", Links.EXIT_NODE_GUIDE),
      Button.link("⬇️ ${ui.button.get-tailscale}", Links.DOWNLOAD));

  private final RegionLabels regionLabels;

  public Screen starting(String regionId) {
    return new Screen("🚀 ${ui.launch.starting} %s…\n⏳ ${ui.launch.step.submitting}",
                      List.of(regionLabels.label(regionId)),
                      List.of());
  }

  public Screen waiting(String regionId) {
    return new Screen("🚀 ${ui.launch.starting} %s…\n✅ ${ui.launch.step.submitted}\n⏳ ${ui.launch.step.waiting}",
                      List.of(regionLabels.label(regionId)),
                      List.of());
  }

  public Screen ready(TaskInfo node) {
    String regionLabel = node.getRegion() == null ? null : regionLabels.label(node.getRegion().id());
    return new Screen("%s <b>%s</b> · %s\n🌐 <code>%s</code>\n⏱ ${ui.node.auto-stop}\n\n${ui.node.connect-hint}",
                      List.of(NodeFormat.statusEmoji(node.getState()),
                              NodeFormat.orDash(node.getHostName()),
                              NodeFormat.orDash(regionLabel),
                              NodeFormat.orDash(node.getPublicIp())),
                      List.of(LINKS, List.of(MENU)));
  }

  public Screen stillStarting(String regionId) {
    return new Screen("🟡 ${ui.launch.still-starting}\n📍 %s",
                      List.of(regionLabels.label(regionId)),
                      List.of(List.of(MENU)));
  }

  public Screen failed(String regionId) {
    Button tryAgain = Button.action("🔁 ${ui.button.try-again}", UiAction.run(regionId));
    return new Screen("🔴 ${ui.launch.failed}\n📍 %s",
                      List.of(regionLabels.label(regionId)),
                      List.of(List.of(tryAgain, MENU)));
  }

  public Screen regionUnavailable() {
    return new Screen("⚠️ ${ui.launch.region-unavailable}", List.of(), List.of(List.of(MENU)));
  }

  public Screen notAllowed() {
    return new Screen("⛔ ${ui.launch.not-allowed}", List.of(), List.of(List.of(MENU)));
  }

}
