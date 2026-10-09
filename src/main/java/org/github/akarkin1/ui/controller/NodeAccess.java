package org.github.akarkin1.ui.controller;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.ecs.TaskInfo;

@RequiredArgsConstructor
public class NodeAccess {

  private final Authorizer authorizer;

  /** Users who may run nodes or are root may view and stop nodes at all. */
  public boolean canManageNodes(String username) {
    return isRoot(username)
           || username != null && authorizer.hasPermission(username, Permission.RUN_NODES);
  }

  public boolean canStop(String username, TaskInfo node) {
    return isRoot(username)
           || isOwner(username, node) && authorizer.hasPermission(username, Permission.RUN_NODES);
  }

  /** Root stopping someone else's node has to confirm it first. */
  public boolean needsConfirmation(String username, TaskInfo node) {
    return isRoot(username) && !isOwner(username, node);
  }

  public boolean canView(String username, TaskInfo node) {
    return isOwner(username, node) || isRoot(username);
  }

  private boolean isRoot(String username) {
    return username != null && authorizer.hasPermission(username, Permission.ROOT_ACCESS);
  }

  private static boolean isOwner(String username, TaskInfo node) {
    return username != null && username.equals(node.getRunBy());
  }

}
