package org.github.akarkin1.dispatcher.command;

import java.util.Collections;
import java.util.List;

public sealed interface BotCommand<R extends CommandResponse>
    permits ListInstancesCommand, VersionCommand, HelpCommand, StopInstanceCommand,
    StartInstanceCommand, StartServerCommand, StopServerCommand {

  R run(List<String> args);

  default R run() {
    return run(Collections.emptyList());
  }

  String getDescription();

}
