package org.github.akarkin1.ui.controller;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.ecs.EcsNode;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.HelpScreen;
import org.github.akarkin1.ui.screen.HomeModel;
import org.github.akarkin1.ui.screen.HomeScreen;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class HomeController {

  private final TailscaleNodeService nodeService;
  private final Authorizer authorizer;
  private final NodeAccess nodeAccess;
  private final UiMessenger messenger;
  private final HomeScreen homeScreen;
  private final HelpScreen helpScreen;

  public void showHome(UiContext context) {
    messenger.send(context, homeScreen.home(buildModel(context)));
  }

  public void refreshHome(UiContext context, Integer messageId) {
    messenger.edit(context, messageId, homeScreen.home(buildModel(context)));
  }

  public void showHelp(UiContext context, Integer messageId) {
    messenger.edit(context, messageId, helpScreen.help());
  }

  private HomeModel buildModel(UiContext context) {
    TgUser user = context.user();
    if (user.id() == null) {
      return new HomeModel(context.firstName(), context.username(), false, false, false, List.of(),
                           List.of(), List.of(), false, false);
    }

    boolean canListNodes = authorizer.hasPermission(user, Permission.LIST_NODES);
    boolean canRunNodes = authorizer.hasPermission(user, Permission.RUN_NODES);
    boolean allNodes = authorizer.hasPermission(user, Permission.ROOT_ACCESS);
    boolean canManageUsers = allNodes || authorizer.hasPermission(user, Permission.USER_MANAGEMENT);
    boolean hasAccess = canListNodes || canRunNodes || canManageUsers
                        || UserAccess.hasAccess(authorizer, user);

    List<EcsNode> nodes = canListNodes
        ? nodeService.listTasks(allNodes ? null : String.valueOf(user.id()))
        : List.of();
    List<String> regionIds = canRunNodes
        ? new ArrayList<>(nodeService.getSupportedRegionIds())
        : List.of();
    List<String> stoppableTaskIds = nodes.stream()
        .filter(node -> nodeAccess.canStop(user, node))
        .map(EcsNode::getId)
        .toList();

    return new HomeModel(context.firstName(), context.username(), canListNodes, canRunNodes,
                         allNodes, nodes, regionIds, stoppableTaskIds, canManageUsers, hasAccess);
  }

}
