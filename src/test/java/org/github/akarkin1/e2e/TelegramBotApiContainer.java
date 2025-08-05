package org.github.akarkin1.e2e;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class TelegramBotApiContainer extends GenericContainer<TelegramBotApiContainer> {
  public TelegramBotApiContainer() {
    super(DockerImageName.parse("aiogram/telegram-bot-api:latest"));
    withExposedPorts(8081); // Default port for the API server
  }
}
