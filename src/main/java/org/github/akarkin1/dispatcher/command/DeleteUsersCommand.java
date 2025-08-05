package org.github.akarkin1.dispatcher.command;

import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.auth.EntitlementUtil;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.UserEntitlements;
import org.github.akarkin1.auth.s3.EntitlementsService;
import org.github.akarkin1.dispatcher.response.EmptyResponse;
import org.github.akarkin1.message.MessageConsumer;
import org.github.akarkin1.util.UserNameUtil;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class DeleteUsersCommand implements BotCommand<EmptyResponse> {

  private final EntitlementsService entitlementsService;
  private final MessageConsumer messageConsumer;

  public DeleteUsersCommand(EntitlementsService entitlementsService, MessageConsumer messageConsumer) {
    this.entitlementsService = entitlementsService;
    this.messageConsumer = messageConsumer;
  }

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

    Map<String, List<UserEntitlements.Entitlement>> userEntitlements = entitlementsService.getUserEntitlements();
    List<String> notFoundUsers = usersNames.stream()
        .filter(Predicate.not(userEntitlements::containsKey))
        .toList();

    boolean atLeastOneDeleted = false;
    for (String userName : usersNames) {
      if (notFoundUsers.contains(userName)) {
        messageConsumer.accept("${command.delete-users.user-not-found.error} %s.".formatted(userName));
        continue;
      }

      // do not allow deleting the root user
      if (EntitlementUtil.hasUserPermission(userName, userEntitlements, Permission.ROOT_ACCESS)) {
        messageConsumer.accept("${command.delete-users.root-cannot-be-deleted.error} %s.".formatted(userName));
        continue;
      }

      entitlementsService.deleteUser(userName);
      messageConsumer.accept("${command.delete-users.user-is-deleted.message}", userName);
      atLeastOneDeleted = true;
    }

    if (!atLeastOneDeleted) {
      messageConsumer.accept("${command.delete-users.no-user-to-delete.message}");
    }

    return EmptyResponse.NONE;
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
