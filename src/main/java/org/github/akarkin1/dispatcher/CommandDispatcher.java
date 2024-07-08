package org.github.akarkin1.dispatcher;

import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.dispatcher.command.BotCommand;
import org.github.akarkin1.dispatcher.command.CommandResponse;
import org.github.akarkin1.dispatcher.command.EmptyResponse;
import org.github.akarkin1.dispatcher.command.HelpCommand;
import org.github.akarkin1.dispatcher.command.TextCommandResponse;
import org.github.akarkin1.exception.CommandExecutionFailedException;
import org.github.akarkin1.exception.InvalidCommandException;
import org.github.akarkin1.tg.BotCommunicator;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
public class CommandDispatcher {

  private final Map<String, BotCommand<?>> registeredCommand = new HashMap<>();

  private final BotCommunicator botCommunicator;

  public CommandDispatcher(BotCommunicator botCommunicator) {
    this.botCommunicator = botCommunicator;
    this.registerCommand("/start", new HelpCommand(this));
    this.registerCommand("/help", new HelpCommand(this));
  }

  public void registerCommand(String name, BotCommand<?> command) {
    registeredCommand.put(name, command);
  }

  public void handle(Update updateEvent) throws TelegramApiException {
    String userInput = updateEvent.getMessage().getText();

    if (userInput.startsWith("/")) {
      String[] commandAndArgs = userInput.split("\\s+");
      String command = commandAndArgs[0];
      List<String> args = Stream.of(commandAndArgs)
          .skip(1)
          .toList();
      if (!registeredCommand.containsKey(command)) {
        sendUnknownCommandError(command);
        return;
      }

      runCommand(registeredCommand.get(command), args);
    } else {
      sendNotACommandError(updateEvent);
    }
  }

  private void runCommand(BotCommand<?> botCommand, List<String> args) {
    String commandName = botCommand.getClass().getSimpleName();
    log.debug("Running command: {}, with arguments: {}", commandName, args);

    try {
      CommandResponse resp = botCommand.run(args);

      String commandResponseType = resp.getClass().getSimpleName();
      log.debug("Command result: {}", resp);
      if (resp instanceof TextCommandResponse txtResp) {
        String commandOutput = txtResp.text();
        log.debug("Sending the result to telegram bot...");
        sendTextMessageToTheBot(commandOutput);
      } else if (!(resp instanceof EmptyResponse)) {
        throw new IllegalStateException(
            "Unsupported Command response type: %s".formatted(commandResponseType));
      }
    } catch (InvalidCommandException e) {
      String errMessage = "Invalid command syntax: " + e.getMessage();
      log.debug("Invalid command. Sending error message to telegram bot...", e);
      sendTextMessageToTheBot(errMessage);
    } catch (CommandExecutionFailedException e) {
      log.debug("Failed to execute command {}. Sending error message to telegram bot...",
                commandName, e);
      sendTextMessageToTheBot(e.getMessage());
    }

  }

  public void sendTextMessageToTheBot(String content) {
    botCommunicator.sendMessageToTheBot(content);
  }

  private void sendNotACommandError(Update updateEvent) {
    String userInput = updateEvent.getMessage().getText();
    String errorMessage = "Unsupported syntax: '%s'. Please enter a command (it should start with '/')"
        .formatted(userInput);
    sendTextMessageToTheBot(errorMessage);
  }

  private void sendUnknownCommandError(String command) {
    String errorMessage = "Unsupported command: '%s'. Please enter a valid command. "
        + "The following commands are supported: %n%s".formatted(command, getSupportedCommands());
    sendTextMessageToTheBot(errorMessage);
  }

  public String getSupportedCommands() {
    return registeredCommand.entrySet()
        .stream()
        .map(entry -> "  %s – %s".formatted(entry.getKey(), entry.getValue().getDescription()))
        .collect(Collectors.joining(System.lineSeparator()));
  }

}
