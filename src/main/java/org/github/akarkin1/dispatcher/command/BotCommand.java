package org.github.akarkin1.dispatcher.command;

import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.dispatcher.response.CommandResponse;

import java.util.Collections;
import java.util.List;

public interface BotCommand<R extends CommandResponse> {

  R run(List<String> args);

  default R run() {
    return run(Collections.emptyList());
  }

  String getDescription();

  List<Permission> getRequiredPermissions();

}
