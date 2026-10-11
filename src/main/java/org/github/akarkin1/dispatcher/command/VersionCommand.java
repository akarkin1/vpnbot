package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.Screen;

import java.util.List;

@Log4j2
@RequiredArgsConstructor
public final class VersionCommand implements BotCommand {

  private final String appVersion;

  @Override
  public Screen run(UiContext context, List<String> args) {
    return BotCommand.reply("${command.version.app-version.message}", appVersion);
  }

  @Override
  public String getDescription() {
    return "${command.version.description.message}";
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return List.of(Permission.ROOT_ACCESS);
  }

}
