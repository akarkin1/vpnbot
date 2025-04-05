package org.github.akarkin1.dispatcher.command.ecs;

import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.dispatcher.command.TextCommandResponse;

import java.util.List;

import static org.github.akarkin1.config.ConfigManager.getAppVersion;

@Log4j2
public final class VersionCommandV2 implements BotCommandV2<TextCommandResponse> {

  @Override
  public TextCommandResponse run(List<String> args) {
    return new TextCommandResponse("${command.version.app-version.message}",
                                   getAppVersion());
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
