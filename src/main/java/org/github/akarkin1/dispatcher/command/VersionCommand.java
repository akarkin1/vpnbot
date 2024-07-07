package org.github.akarkin1.dispatcher.command;

import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.LambdaHandler;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

@Log4j2
public final class VersionCommand implements BotCommand<TextCommandResponse> {

  @Override
  public TextCommandResponse run(List<String> args) {
    String responseContent = "Application version: %s".formatted(readVersion());
    return new TextCommandResponse(responseContent);
  }

  @Override
  public String getDescription() {
    return "prints version of the backend application";
  }


  private static String readVersion() {
    try (InputStream in = LambdaHandler.class.getResourceAsStream("/version")) {
      assert in != null : "resource 'version' is missing";
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
        return reader.readLine();
      }
    } catch (Exception e) {
      log.error("Failed to read version: ", e);
      return "<unknown>";
    }
  }
}
