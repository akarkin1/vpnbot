package org.github.akarkin1.dispatcher.command.ecs;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.UserRole;
import org.github.akarkin1.auth.UserSignupService;
import org.github.akarkin1.dispatcher.command.TextCommandResponse;
import org.github.akarkin1.util.UserNameUtil;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class AssignRolesCommand implements BotCommandV2<TextCommandResponse> {

  private final UserSignupService signupService;

  @Override
  public TextCommandResponse run(List<String> args) {
    Set<String> knownRoleValues = Stream.of(UserRole.values())
      .map(UserRole::name)
      .collect(Collectors.toSet());

    if (args.isEmpty()) {
      return new TextCommandResponse(
        "${common.command.invalid-syntax.message} – "
        + "${command.assign-roles.invalid-syntax.no-username.error}\n"
        + "${command.assign-roles.usage-note}",
        knownRoleValues);
    }

    if (args.size() == 1) {
      return new TextCommandResponse(
        "${common.command.invalid-syntax.message} – "
        + "${command.assign-roles.invalid-syntax.no-role.error}\n"
        + "${command.assign-roles.usage-note}",
        knownRoleValues);
    }

    String username = UserNameUtil.normalizeUserName(args.getFirst());
    List<String> rolesValues = args.subList(1, args.size());

    List<String> unknownRoleValues = rolesValues.stream()
      .filter(Predicate.not(knownRoleValues::contains))
      .toList();
    if (!unknownRoleValues.isEmpty()) {
      return new TextCommandResponse(
        "${command.assign-roles.no-role-exists.error}",
        unknownRoleValues, knownRoleValues);
    }

    Set<UserRole> rolesToAssign = rolesValues.stream()
      .map(UserRole::valueOf)
      .collect(Collectors.toSet());
    signupService.assignRolesToUser(username, rolesToAssign);
    return new TextCommandResponse(
      "${command.assign-roles.roles-assigned.success.message}",
      username, rolesToAssign);
  }

  @Override
  public String getDescription() {
    return """
      ${command.assign-roles.description.message}
      ${command.assign-roles.usage-note} %s
      """.formatted(List.of(UserRole.values()));
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return List.of(Permission.USER_MANAGEMENT);
  }

}
