package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.ServiceRole;
import org.github.akarkin1.auth.ServiceRole.Role;
import org.github.akarkin1.auth.UserSignupService;
import org.github.akarkin1.dispatcher.response.TextCommandResponse;
import org.github.akarkin1.util.UserNameUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class AssignRolesCommand implements BotCommand<TextCommandResponse> {

  private final UserSignupService signupService;

  @Override
  public TextCommandResponse run(List<String> args) {
    Set<String> knownRoleValues = Stream.of(Role.values())
        .map(Role::name)
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
    Map<String, String> serviceByRole = new HashMap<>();
    for (String serviceRoleVal : args.subList(1, args.size())) {
      if (serviceRoleVal.contains(":")) {
        String[] srParts = serviceRoleVal.split(":");
        // first goes a Service, the second goes Role
        // the map is Role to Service
        serviceByRole.put(srParts[1], srParts[0]);
      } else {
        serviceByRole.put(serviceRoleVal, null);
      }
    }
    Set<String> rolesValues = serviceByRole.keySet();

    List<String> unknownRoleValues = rolesValues.stream()
        .filter(Predicate.not(knownRoleValues::contains))
        .toList();
    if (!unknownRoleValues.isEmpty()) {
      return new TextCommandResponse(
          "${command.assign-roles.no-role-exists.error}",
          unknownRoleValues, knownRoleValues);
    }

    Set<ServiceRole> serviceRolesToAssign = serviceByRole.entrySet().stream()
        .map(roleToSvcEntry -> new ServiceRole(roleToSvcEntry.getValue(), Role.valueOf(roleToSvcEntry.getKey())))
        .collect(Collectors.toSet());
    signupService.assignRolesToUser(username, serviceRolesToAssign);
    return new TextCommandResponse(
        "${command.assign-roles.roles-assigned.success.message}",
        username, serviceRolesToAssign);
  }

  @Override
  public String getDescription() {
    return "${command.assign-roles.description.message}%n${command.assign-roles.usage-note} "
        + List.of(Role.values());
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return List.of(Permission.USER_MANAGEMENT);
  }

}
