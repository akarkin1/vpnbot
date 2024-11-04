package org.github.akarkin1.dispatcher.command.ecs;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.UserPermissionsProvider;
import org.github.akarkin1.dispatcher.command.TextCommandResponse;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class ListUsersCommand implements BotCommandV2<TextCommandResponse> {

  private final UserPermissionsProvider userPermissionsProvider;

  @Override
  public TextCommandResponse run(List<String> args) {

    Map<String, List<Permission>> userPermissions = userPermissionsProvider.getUserPermissions();

    if (userPermissions.isEmpty()) {
      return new TextCommandResponse("No users registered.");
    }
    StringBuilder responseBuilder = new StringBuilder();

    responseBuilder.append("List of registered users:\n");
    userPermissions.forEach((userName, permissions) -> responseBuilder
        .append("\t - %s (user permissions: %s)\n".formatted(userName, permissions)));

    return new TextCommandResponse(responseBuilder.toString());
  }

  @Override
  public String getDescription() {
    return "returns list of registered users with their permissions";
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return List.of(Permission.USER_MANAGEMENT);
  }

}
