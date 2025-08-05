package org.github.akarkin1.dispatcher.command;

import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.dispatcher.response.TextCommandResponse;

import java.util.List;

@Log4j2
public final class VersionCommand implements BotCommand<TextCommandResponse> {
  private final ConfigManager configManager;

  public VersionCommand(ConfigManager configManager) {
    this.configManager = configManager;
  }

  @Override
  public TextCommandResponse run(List<String> args) {
    return new TextCommandResponse("${command.version.app-version.message}",
                                   configManager.getAppVersion());
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
