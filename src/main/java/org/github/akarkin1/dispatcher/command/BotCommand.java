package org.github.akarkin1.dispatcher.command;

import java.util.Collections;
import java.util.List;

public sealed interface BotCommand<R extends CommandResponse> permits
    ListInstancesCommand,
    VersionCommand,
    HelpCommand,
    StopInstanceCommand,
    StartInstanceCommand,
    StartServerCommandV2,
    StopServerCommandV2,
    RestartServerCommand,
    StartServerCommand,
    StopServerCommand,
    RebootServerCommand,
    RunNodeCommand,
    ListNodeCommand,
    SupportedRegionCommand {

  R run(List<String> args);

  default R run() {
    return run(Collections.emptyList());
  }

  String getDescription();

}
