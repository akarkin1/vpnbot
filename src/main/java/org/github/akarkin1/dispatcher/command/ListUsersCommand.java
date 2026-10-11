package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.PermissionsService;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.dispatcher.response.TextCommandResponse;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class ListUsersCommand implements BotCommand<TextCommandResponse> {

  private final PermissionsService permissionsService;

  @Override
  public TextCommandResponse run(List<String> args) {

    Map<TgUser, List<Permission>> users = permissionsService.getUsers();

    if (users.isEmpty()) {
      return new TextCommandResponse("${command.list-users.no-users-registered.message}");
    }
    StringBuilder responseBuilder = new StringBuilder();

    responseBuilder.append("${command.list-users.list-of-registered-users.message}\n");
    users.forEach((user, permissions) -> responseBuilder
        .append("\t - %s: %s\n".formatted(UserArguments.describe(user), permissions)));

    return new TextCommandResponse(responseBuilder.toString());
  }

  @Override
  public String getDescription() {
    return "${command.list-users.description.message}";
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return List.of(Permission.USER_MANAGEMENT);
  }

}
