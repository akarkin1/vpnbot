package org.github.akarkin1.dispatcher.command.ecs;

import org.github.akarkin1.auth.UserPermission;
import org.github.akarkin1.dispatcher.command.CommandResponse;

import java.util.Collections;
import java.util.List;

public interface BotCommandV2<R extends CommandResponse> {

  R run(List<String> args);

  default R run() {
    return run(Collections.emptyList());
  }

  String getDescription();

  List<UserPermission> getRequiredPermissions();

}
