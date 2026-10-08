package org.github.akarkin1.ui.screen;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.ecs.TaskInfo;

@RequiredArgsConstructor
public class LaunchScreens {

  private final RegionLabels regionLabels;

  public Screen starting(String regionId) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public Screen waiting(String regionId) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public Screen ready(TaskInfo node) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public Screen stillStarting(String regionId) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public Screen failed(String regionId) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public Screen regionUnavailable() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public Screen notAllowed() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

}
