package org.github.akarkin1.dispatcher.command.ecs;

import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.UnauthorizedRequestException;
import org.github.akarkin1.auth.UserPermission;
import org.github.akarkin1.dispatcher.command.CommandResponse;
import org.github.akarkin1.dispatcher.command.EmptyResponse;
import org.github.akarkin1.dispatcher.command.TextCommandResponse;
import org.github.akarkin1.exception.CommandExecutionFailedException;
import org.github.akarkin1.exception.InvalidCommandException;
import org.github.akarkin1.tg.BotCommunicator;
import org.github.akarkin1.tg.TgRequestContext;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
public class CommandDispatcherV2 {

  private final Map<String, BotCommandV2<?>> registeredCommand = new LinkedHashMap<>();

  private final BotCommunicator botCommunicator;

  private final Authorizer authorizer;

  public CommandDispatcherV2(BotCommunicator botCommunicator, Authorizer authorizer) {
    this.botCommunicator = botCommunicator;
    this.authorizer = authorizer;
    this.registerCommand("/help", new HelpCommandV2(this));
  }

  public void registerCommand(String name, BotCommandV2<?> command) {
    registeredCommand.put(name, command);
  }

  public void handle(Update updateEvent) {
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

  private void runCommand(BotCommandV2<?> botCommand, List<String> args) {
    String commandName = botCommand.getClass().getSimpleName();
    log.debug("Running command: {}, with arguments: {}", commandName, args);

    try {
      checkUserPermissions(botCommand);
      CommandResponse resp = botCommand.run(args);

      if (resp instanceof TextCommandResponse txtResp) {
        String commandOutput = txtResp.text();
        log.debug("Sending the result to telegram bot...");
        botCommunicator.sendMessageToTheBot(commandOutput);
      } else if (!(resp instanceof EmptyResponse)) {
        throw new IllegalStateException(
            "Unsupported Command response type: %s"
                .formatted(resp == null ? "null" : resp.getClass().getSimpleName()));
      }
    } catch (InvalidCommandException e) {
      String errMessage = "Invalid command syntax: " + e.getMessage();
      log.debug("Invalid command. Sending error message to telegram bot...", e);
      botCommunicator.sendMessageToTheBot(errMessage);
    } catch (UnauthorizedRequestException e) {
      log.debug("User {} is not authorized to run the command: {}. Permission required: {}",
                TgRequestContext.getUsername(),
                botCommand.getClass().getSimpleName(),
                e.getRequiredPermission());
      botCommunicator.sendMessageToTheBot("You are not allowed to run this command.");
    } catch (CommandExecutionFailedException e) {
      log.debug("Failed to execute command {}. Sending error message to telegram bot...",
                commandName, e);
      botCommunicator.sendMessageToTheBot(e.getMessage());
    }
  }

  private void checkUserPermissions(BotCommandV2<?> botCommand) {
    String username = TgRequestContext.getUsername();
    for (UserPermission requiredPermission : botCommand.getRequiredPermissions()) {
      if (!authorizer.hasPermission(username, requiredPermission)) {
        throw new UnauthorizedRequestException(requiredPermission);
      }
    }

  }

  private void sendNotACommandError(Update updateEvent) {
    String userInput = updateEvent.getMessage().getText();
    String errorMessage = "Unsupported syntax: '%s'. Please enter a command (it should start with '/')"
        .formatted(userInput);
    botCommunicator.sendMessageToTheBot(errorMessage);
  }

  private void sendUnknownCommandError(String command) {
    String errorMessage = "Unsupported command: '%s'. Please enter a valid command. "
        + "The following commands are supported: %n%s".formatted(command, getSupportedCommands());
    botCommunicator.sendMessageToTheBot(errorMessage);
  }

  public String getSupportedCommands() {
    String username = TgRequestContext.getUsername();
    return registeredCommand.entrySet()
        .stream()
        .filter(command -> isAuthorizedToUseCommand(command.getValue(), username))
        .map(entry -> "  %s – %s".formatted(entry.getKey(), entry.getValue().getDescription()))
        .collect(Collectors.joining(System.lineSeparator()));
  }

  private boolean isAuthorizedToUseCommand(BotCommandV2<?> command, String username) {
    return command.getRequiredPermissions()
        .stream()
        .allMatch(permission -> authorizer.hasPermission(username, permission));
  }
}
