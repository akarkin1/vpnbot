package org.github.akarkin1.dispatcher.command.ecs;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.UserRole;
import org.github.akarkin1.auth.UserSignupService;
import org.github.akarkin1.dispatcher.command.TextCommandResponse;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class AssignRoleCommand implements BotCommandV2<TextCommandResponse> {

  public static final String USAGE_NOTE = "Usage: /assignRole <TelegramUsername> <UserRoleList>.";

  private final UserSignupService signupService;

  @Override
  public TextCommandResponse run(List<String> args) {
    if (args.isEmpty()) {
      return new TextCommandResponse(
          "Invalid command syntax – username need to be specified. " + USAGE_NOTE);
    }

    if (args.size() == 1) {
      return new TextCommandResponse(
          "Invalid command syntax – at least one of the roles needs to be specified. " + USAGE_NOTE);
    }

    String username = args.getFirst();
    List<String> rolesValues = args.subList(1, args.size());

    Set<String> knownRoleValues = Stream.of(UserRole.values())
        .map(UserRole::name)
        .collect(Collectors.toSet());
    List<String> unknownRoleValues = rolesValues.stream()
        .filter(Predicate.not(knownRoleValues::contains))
        .toList();
    if (!unknownRoleValues.isEmpty()) {
      return new TextCommandResponse("There are no such roles: %s. Valid values for the roles are – %s"
                                         .formatted(unknownRoleValues, knownRoleValues));
    }

    Set<UserRole> rolesToAssign = rolesValues.stream()
        .map(UserRole::valueOf)
        .collect(Collectors.toSet());
    signupService.assignRolesToUser(username, rolesToAssign);
    return new TextCommandResponse("The following roles are assigned successfully to the user %s: %s"
                                       .formatted(username, rolesToAssign));
  }

  @Override
  public String getDescription() {
    return "assigns list of <UserRole> to the user, each <UserRole> can accept one of the following values: %s. %s"
        .formatted(Stream.of(UserRole.values()).toList(), USAGE_NOTE);
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return List.of(Permission.USER_MANAGEMENT);
  }

}
