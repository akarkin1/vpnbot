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

  public static final String USAGE_NOTE = """
          Usage: /assignRole <TelegramUsername> <UserRoleList>,
          whereas:
            - <TelegramUsername> – is the username of the telegram user, for which role will be assigned.
            - <UserRoleList> – is the list separated by space of user role. Each User Role can accept one of the following values: %s
      """;

  private final UserSignupService signupService;

  @Override
  public TextCommandResponse run(List<String> args) {
    Set<String> knownRoleValues = Stream.of(UserRole.values())
        .map(UserRole::name)
        .collect(Collectors.toSet());

    if (args.isEmpty()) {
      return new TextCommandResponse(
          "Invalid command syntax – username need to be specified.\n"
          + USAGE_NOTE.formatted(knownRoleValues));
    }

    if (args.size() == 1) {
      return new TextCommandResponse(
          "Invalid command syntax – at least one of the roles needs to be specified.\n"
          + USAGE_NOTE.formatted(knownRoleValues));
    }

    String username = args.getFirst();
    List<String> rolesValues = args.subList(1, args.size());

    List<String> unknownRoleValues = rolesValues.stream()
        .filter(Predicate.not(knownRoleValues::contains))
        .toList();
    if (!unknownRoleValues.isEmpty()) {
      return new TextCommandResponse(
          "There are no such roles: %s. Valid values for the roles are – %s"
              .formatted(unknownRoleValues, knownRoleValues));
    }

    Set<UserRole> rolesToAssign = rolesValues.stream()
        .map(UserRole::valueOf)
        .collect(Collectors.toSet());
    signupService.assignRolesToUser(username, rolesToAssign);
    return new TextCommandResponse(
        "The following roles are assigned successfully to the user %s: %s"
            .formatted(username, rolesToAssign));
  }

  @Override
  public String getDescription() {
    return """
        Assigns list of User Roles to the user specified.
        %s
        """.formatted(USAGE_NOTE.formatted(List.of(UserRole.values())));
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return List.of(Permission.USER_MANAGEMENT);
  }

}
