package org.github.akarkin1.dispatcher.command;

import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.RoleSet;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.Screen;

import java.util.ArrayList;
import java.util.List;

public class DescribeRolesCommand implements SystemCommand {

  @Override
  public Screen run(UiContext context, List<String> args) {
    StringBuilder responseBuilder = new StringBuilder();
    List<Object> params = new ArrayList<>();

    responseBuilder.append("${command.describe-roles.available-roles.message}:\n");
    RoleSet.describe().forEach((role, permissions) -> {
      responseBuilder.append("\t - %s: %s\n");
      params.add(role);
      params.add(permissions);
    });

    return SystemCommand.reply(responseBuilder.toString(), params.toArray());
  }

  @Override
  public String getDescription() {
    return "${command.describe-roles.description.message}";
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return List.of(Permission.USER_MANAGEMENT);
  }

}
