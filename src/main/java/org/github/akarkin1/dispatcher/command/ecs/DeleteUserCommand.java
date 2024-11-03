package org.github.akarkin1.dispatcher.command.ecs;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.s3.PermissionsService;
import org.github.akarkin1.dispatcher.command.EmptyResponse;

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
    if (args.isEmpty()) {
      messageConsumer.accept("ERROR! You need to specify at least one user to delete.");
      return EmptyResponse.NONE;
    }

    Map<String, List<Permission>> userPermissions = permissionsService.getUserPermissions();
    List<String> notFoundUsers = args.stream()
        .filter(Predicate.not(userPermissions::containsKey))
        .toList();

    if (!notFoundUsers.isEmpty()) {
      messageConsumer.accept("ERROR! The following users were not found: %s.".formatted(notFoundUsers));
    }

    boolean atLeastSingleDeleted = false;
    for (String arg : args) {
      if (!notFoundUsers.contains(arg)) {
        permissionsService.deleteUser(arg);
        messageConsumer.accept("The user '%s' has been deleted successfully.".formatted(arg));
        atLeastSingleDeleted = true;
      }
    }

    if (!atLeastSingleDeleted) {
      messageConsumer.accept("No users found to delete.");
    }

    return EmptyResponse.NONE;
  }

  @Override
  public String getDescription() {
    return "removes the specified users by telegram usernames";
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return List.of(Permission.USER_MANAGEMENT);
  }
}
