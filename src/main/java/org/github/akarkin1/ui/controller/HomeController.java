package org.github.akarkin1.ui.controller;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.ecs.TaskInfo;
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
    String username = context.username();
    if (username == null) {
      return new HomeModel(context.firstName(), null, false, false, false, List.of(), List.of(),
                           List.of());
    }

    boolean canListNodes = authorizer.hasPermission(username, Permission.LIST_NODES);
    boolean canRunNodes = authorizer.hasPermission(username, Permission.RUN_NODES);
    boolean allNodes = authorizer.hasPermission(username, Permission.ROOT_ACCESS);

    List<TaskInfo> nodes = canListNodes
        ? nodeService.listTasks(allNodes ? null : username)
        : List.of();
    List<String> regionIds = canRunNodes
        ? new ArrayList<>(nodeService.getSupportedRegionIds())
        : List.of();

    return new HomeModel(context.firstName(), username, canListNodes, canRunNodes, allNodes,
                         nodes, regionIds, List.of());
  }

}
