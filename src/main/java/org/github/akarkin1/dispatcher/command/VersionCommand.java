package org.github.akarkin1.dispatcher.command;

import lombok.extern.log4j.Log4j2;

import java.util.List;

import static org.github.akarkin1.config.ConfigManager.*;

@Log4j2
public final class VersionCommand implements BotCommand<TextCommandResponse> {

  @Override
  public TextCommandResponse run(List<String> args) {
    String responseContent = "Application version: %s".formatted(getAppVersion());
    return new TextCommandResponse(responseContent);
  }

  @Override
  public String getDescription() {
    return "prints version of the backend application";
  }

}
