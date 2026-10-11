package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.PermissionsService;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.dispatcher.response.EmptyResponse;
import org.github.akarkin1.message.MessageConsumer;
import org.github.akarkin1.util.UserNameUtil;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
public class DeleteUsersCommand implements BotCommand<EmptyResponse> {

  private final PermissionsService permissionsService;
  private final MessageConsumer messageConsumer;

  @Override
  public EmptyResponse run(List<String> args) {
    List<String> usersNames = args.stream()
        .map(UserNameUtil::normalizeUserName)
        .filter(StringUtils::isNotBlank)
        .toList();

    if (usersNames.isEmpty()) {
      messageConsumer.accept("${command.delete-users.no-user-specified.error}");
      return EmptyResponse.NONE;
    }

    Map<TgUser, List<Permission>> users = permissionsService.getUsers();

    boolean atLeastOneDeleted = false;
    for (String userName : usersNames) {
      Optional<TgUser> user = resolveUser(userName, users);
      if (user.isEmpty()) {
        messageConsumer.accept("${command.delete-users.user-not-found.error} %s.".formatted(userName));
        continue;
      }

      // do not allow to delete the root user
      List<Permission> permissions = users.getOrDefault(user.get(), List.of());
      if (permissions.contains(Permission.ROOT_ACCESS)) {
        messageConsumer.accept("${command.delete-users.root-cannot-be-deleted.error} %s.".formatted(userName));
        continue;
      }

      permissionsService.deleteUser(user.get());
      messageConsumer.accept("${command.delete-users.user-is-deleted.message}", userName);
      atLeastOneDeleted = true;
    }

    if (!atLeastOneDeleted) {
      messageConsumer.accept("${command.delete-users.no-user-to-delete.message}");
    }

    return EmptyResponse.NONE;
  }

  /** A known user by id (all digits) or by username. */
  private Optional<TgUser> resolveUser(String userName, Map<TgUser, List<Permission>> users) {
    Optional<Long> id = UserArguments.parseId(userName);
    if (id.isPresent()) {
      TgUser byId = new TgUser(id.get(), null);
      return users.keySet().stream().filter(byId::equals).findFirst();
    }
    return permissionsService.findByUsername(userName);
  }

  @Override
  public String getDescription() {
    return "${command.delete-users.description.message}";
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return List.of(Permission.DELETE_USERS);
  }
}
