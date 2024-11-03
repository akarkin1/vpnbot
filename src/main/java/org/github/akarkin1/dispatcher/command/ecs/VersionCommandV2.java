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
    String responseContent = "Application version: %s".formatted(getAppVersion());
    return new TextCommandResponse(responseContent);
  }

  @Override
  public String getDescription() {
    return "prints version of the backend application";
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return List.of(Permission.ROOT_ACCESS);
  }

}
