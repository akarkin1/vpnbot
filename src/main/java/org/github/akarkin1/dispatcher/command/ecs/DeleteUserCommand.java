package org.github.akarkin1.dispatcher.command.ecs;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.s3.PermissionsService;
import org.github.akarkin1.dispatcher.command.EmptyResponse;
import org.github.akarkin1.util.UserNameUtil;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

@RequiredArgsConstructor
public class DeleteUserCommand implements BotCommandV2<EmptyResponse> {

  private final PermissionsService permissionsService;
  private final Consumer<String> messageConsumer;

  @Override
  public EmptyResponse run(List<String> args) {
    List<String> usersNames = args.stream()
        .map(UserNameUtil::normalizeUserName)
        .filter(StringUtils::isNotBlank)
        .toList();

    if (usersNames.isEmpty()) {
      messageConsumer.accept("ERROR! You need to specify at least one user to delete.");
      return EmptyResponse.NONE;
    }

    Map<String, List<Permission>> userPermissions = permissionsService.getUserPermissions();
    List<String> notFoundUsers = usersNames.stream()
        .filter(Predicate.not(userPermissions::containsKey))
        .toList();

    boolean atLeastOneDeleted = false;
    for (String userName : usersNames) {
      if (notFoundUsers.contains(userName)) {
        messageConsumer.accept("ERROR! The following user is not found: %s.".formatted(userName));
        continue;
      }

      // do not allow to delete the root user
      if (userPermissions.get(userName).contains(Permission.ROOT_ACCESS)) {
        messageConsumer.accept("ERROR! Root user cannot be deleted: %s.".formatted(userName));
        continue;
      }

      permissionsService.deleteUser(userName);
      messageConsumer.accept("The user '%s' has been deleted successfully.".formatted(userName));
      atLeastOneDeleted = true;
    }

    if (!atLeastOneDeleted) {
      messageConsumer.accept("No users to delete.");
    }

    return EmptyResponse.NONE;
  }

  @Override
  public String getDescription() {
    return """
        Removes the specified users by telegram usernames
        USAGE: /deleteUser <TelegramUserNames>
        """;
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return List.of(Permission.DELETE_USERS);
  }
}
