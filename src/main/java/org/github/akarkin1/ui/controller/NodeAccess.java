package org.github.akarkin1.ui.controller;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.ecs.TaskInfo;

@RequiredArgsConstructor
public class NodeAccess {

  private final Authorizer authorizer;

  public boolean canStop(String username, TaskInfo node) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public boolean needsConfirmation(String username, TaskInfo node) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public boolean canView(String username, TaskInfo node) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

}
