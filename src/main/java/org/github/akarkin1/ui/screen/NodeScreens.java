package org.github.akarkin1.ui.screen;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.ecs.TaskInfo;

@RequiredArgsConstructor
public class NodeScreens {

  private final RegionLabels regionLabels;

  public Screen stopping(TaskInfo node) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public Screen confirmStop(TaskInfo node) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public Screen alreadyStopped() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public Screen notAllowed() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public Screen stoppedByAdmin(TaskInfo node) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

}
