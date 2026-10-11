package org.github.akarkin1.ui.controller;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.ecs.EcsNode;

@RequiredArgsConstructor
public class NodeAccess {

  private final Authorizer authorizer;

  /** Users who may run nodes or are root may view and stop nodes at all. */
  public boolean canManageNodes(TgUser user) {
    return isRoot(user)
           || user != null && authorizer.hasPermission(user, Permission.RUN_NODES);
  }

  public boolean canStop(TgUser user, EcsNode node) {
    return isRoot(user)
           || isOwner(user, node) && authorizer.hasPermission(user, Permission.RUN_NODES);
  }

  /** Root stopping someone else's node has to confirm it first. */
  public boolean needsConfirmation(TgUser user, EcsNode node) {
    return isRoot(user) && !isOwner(user, node);
  }

  public boolean canView(TgUser user, EcsNode node) {
    return isOwner(user, node) || isRoot(user);
  }

  private boolean isRoot(TgUser user) {
    return user != null && authorizer.hasPermission(user, Permission.ROOT_ACCESS);
  }

  /**
   * The {@code RunBy} tag holds the owner's user id; nodes started before Phase 4 hold the
   * username there, so it matches too during the transition (D-3).
   */
  private static boolean isOwner(TgUser user, EcsNode node) {
    if (user == null || node.getRunBy() == null) {
      return false;
    }
    return user.id() != null && String.valueOf(user.id()).equals(node.getRunBy())
           || user.username() != null && user.username().equals(node.getRunBy());
  }

}
