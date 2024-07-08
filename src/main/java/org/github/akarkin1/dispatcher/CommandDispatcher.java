package org.github.akarkin1.dispatcher;

import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.exception.InvalidCommandException;
import org.github.akarkin1.dispatcher.command.BotCommand;
import org.github.akarkin1.dispatcher.command.CommandResponse;
import org.github.akarkin1.dispatcher.command.HelpCommand;
import org.github.akarkin1.dispatcher.command.TextCommandResponse;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
public class CommandDispatcher {

  private final Map<String, BotCommand<?>> registeredCommand = new HashMap<>();

  private final AbsSender sender;

  public CommandDispatcher(AbsSender sender) {
    this.sender = sender;
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
        sendUnknownCommandError(updateEvent, command);
        return;
      }

      runCommand(updateEvent, registeredCommand.get(command), args);
    } else {
      sendNotACommandError(updateEvent);
    }
  }

  private void runCommand(Update updateEvent, BotCommand<?> botCommand, List<String> args)
      throws TelegramApiException {
    log.debug("Running command: {}, with arguments: {}",
              botCommand.getClass().getSimpleName(),
              args);

    try {
      CommandResponse resp = botCommand.run(args);

      log.debug("Command result: {}", resp);
      if (resp instanceof TextCommandResponse txtResp) {
        String commandOutput = txtResp.text();
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(updateEvent.getMessage().getChatId()));
        sendMessage.setText(commandOutput);
        log.debug("Sending the result to telegram bot...");
        Message responseMessage = sender.execute(sendMessage);
        log.debug("Received response: {}", responseMessage);
      } else {
        throw new IllegalStateException("Unsupported Command type: %s".formatted(resp.getClass()));
      }
    } catch (InvalidCommandException e) {
      String errMessage = "Invalid command syntax: " + e.getMessage();
      SendMessage sendMessage = new SendMessage();
      sendMessage.setChatId(String.valueOf(updateEvent.getMessage().getChatId()));
      sendMessage.setText(errMessage);
      log.debug("Invalid command. Sending error message to telegram bot...", e);
      Message responseMessage = sender.execute(sendMessage);
      log.debug("Received response: {}", responseMessage);
    }

  }

  private void sendNotACommandError(Update updateEvent) throws TelegramApiException {
    String userInput = updateEvent.getMessage().getText();

    SendMessage sendMessage = new SendMessage();
    sendMessage.setChatId(String.valueOf(updateEvent.getMessage().getChatId()));
    sendMessage.setText(
        "Unsupported syntax: '%s'. Please enter a command (it should start with '/')".formatted(
            userInput));
    sender.execute(sendMessage);
  }

  private void sendUnknownCommandError(Update updateEvent, String command)
      throws TelegramApiException {
    SendMessage sendMessage = new SendMessage();
    sendMessage.setChatId(String.valueOf(updateEvent.getMessage().getChatId()));

    sendMessage.setText(
        "Unsupported command: '%s'. Please enter a valid command. The following commands are supported: %n%s"
            .formatted(command, getSupportedCommands()));
    sender.execute(sendMessage);
  }

  public String getSupportedCommands() {
    return registeredCommand.entrySet()
        .stream()
        .map(entry -> "  %s – %s".formatted(entry.getKey(), entry.getValue().getDescription()))
        .collect(Collectors.joining(System.lineSeparator()));
  }

}
