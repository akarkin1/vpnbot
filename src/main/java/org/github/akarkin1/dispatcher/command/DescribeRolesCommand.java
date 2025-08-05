package org.github.akarkin1.dispatcher.command;

import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.UserSignupService;
import org.github.akarkin1.dispatcher.response.TextCommandResponse;

import java.util.List;

public class DescribeRolesCommand implements BotCommand<TextCommandResponse> {

  private final UserSignupService signupService;

  public DescribeRolesCommand(UserSignupService signupService) {
    this.signupService = signupService;
  }

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
