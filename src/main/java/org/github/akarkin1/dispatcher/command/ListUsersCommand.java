package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.UserEntitlements;
import org.github.akarkin1.auth.UserEntitlementsProvider;
import org.github.akarkin1.dispatcher.response.TextCommandResponse;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class ListUsersCommand implements BotCommand<TextCommandResponse> {

  private final UserEntitlementsProvider userEntitlementsProvider;

  @Override
  public TextCommandResponse run(List<String> args) {

    Map<String, List<UserEntitlements.Entitlement>> userPermissions = userEntitlementsProvider.getUserEntitlements();

    if (userPermissions.isEmpty()) {
      return new TextCommandResponse("${command.list-users.no-users-registered.message}");
    }
    StringBuilder responseBuilder = new StringBuilder();

    responseBuilder.append("${command.list-users.list-of-registered-users.message}\n");
    userPermissions.forEach((userName, entitlements) -> responseBuilder
        .append("\t - %s (${command.list-users.user-permissions.message} %s)\n".formatted(userName, entitlements)));

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
