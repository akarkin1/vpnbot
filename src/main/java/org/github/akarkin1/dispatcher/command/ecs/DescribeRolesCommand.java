package org.github.akarkin1.dispatcher.command.ecs;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.UserSignupService;
import org.github.akarkin1.dispatcher.command.TextCommandResponse;

import java.util.List;

@RequiredArgsConstructor
public class DescribeRolesCommand implements BotCommandV2<TextCommandResponse> {

  private final UserSignupService signupService;

  @Override
  public TextCommandResponse run(List<String> args) {
    StringBuilder responseBuilder = new StringBuilder();

    responseBuilder.append("${command.describe-roles.available-roles.message}:\n");
    signupService.describeRoles().forEach((role, permissions) -> {
      responseBuilder.append("\t - %s: %s\n".formatted(role, permissions));
    });

    return new TextCommandResponse(responseBuilder.toString());
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
