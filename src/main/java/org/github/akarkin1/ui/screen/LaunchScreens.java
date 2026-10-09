package org.github.akarkin1.ui.screen;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.ui.UiAction;

import java.util.List;

import static org.github.akarkin1.ui.screen.CommonButtons.MENU;

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
                      List.of());
  }

  public Screen ready(String hostName, String regionId, String publicIp) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public Screen idleWarning(String hostName) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public Screen stopped(String hostName, String regionId) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public Screen stoppedCard(String hostName, String regionId) {
    throw new UnsupportedOperationException("Not implemented yet");
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
