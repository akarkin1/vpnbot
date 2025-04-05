package org.github.akarkin1.dispatcher;

import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.UnauthorizedRequestException;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.dispatcher.command.BotCommand;
import org.github.akarkin1.dispatcher.response.CommandResponse;
import org.github.akarkin1.dispatcher.response.EmptyResponse;
import org.github.akarkin1.dispatcher.command.HelpCommand;
import org.github.akarkin1.dispatcher.response.TextCommandResponse;
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
public class CommandDispatcher {

  private final Map<String, BotCommand<?>> registeredCommand = new LinkedHashMap<>();

  private final BotCommunicator botCommunicator;

  private final Authorizer authorizer;

  public CommandDispatcher(BotCommunicator botCommunicator, Authorizer authorizer) {
    this.botCommunicator = botCommunicator;
    this.authorizer = authorizer;
    this.registerCommand("/help", new HelpCommand(this));
  }

  public void registerCommand(String name, BotCommand<?> command) {
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

  private void runCommand(BotCommand<?> botCommand, List<String> args) {
    String commandName = botCommand.getClass().getSimpleName();
    log.debug("Running command: {}, with arguments: {}", commandName, args);

    try {
      checkUserPermissions(botCommand);
      CommandResponse resp = botCommand.run(args);

      if (resp instanceof TextCommandResponse txtResp) {
        String commandOutput = txtResp.text();
        log.debug("Sending the result to telegram bot...");
        botCommunicator.sendMessageToTheBot(commandOutput, txtResp.params());
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
      botCommunicator.sendMessageToTheBot("${user.not-authorized.error}");
    } catch (CommandExecutionFailedException e) {
      log.debug("Failed to execute command {}. Sending error message to telegram bot...",
                commandName, e);
      botCommunicator.sendMessageToTheBot(e.getMessage());
    }
  }

  private void checkUserPermissions(BotCommand<?> botCommand) {
    String username = TgRequestContext.getUsername();
    for (Permission requiredPermission : botCommand.getRequiredPermissions()) {
      if (!authorizer.hasPermission(username, requiredPermission)) {
        throw new UnauthorizedRequestException(requiredPermission);
      }
    }

  }

  private void sendNotACommandError(Update updateEvent) {
    String userInput = updateEvent.getMessage().getText();
    botCommunicator.sendMessageToTheBot("${common.syntax.not-supported.error}", userInput);
  }

  private void sendUnknownCommandError(String command) {
    botCommunicator.sendMessageToTheBot("${common.command.not-supported.error}", command, getSupportedCommands());
  }

  public String getSupportedCommands() {
    String username = TgRequestContext.getUsername();
    return registeredCommand.entrySet()
        .stream()
        .filter(command -> isAuthorizedToUseCommand(command.getValue(), username))
        .map(entry -> "  %s – %s".formatted(entry.getKey(), entry.getValue().getDescription()))
        .collect(Collectors.joining(System.lineSeparator()));
  }

  private boolean isAuthorizedToUseCommand(BotCommand<?> command, String username) {
    return command.getRequiredPermissions()
        .stream()
        .allMatch(permission -> authorizer.hasPermission(username, permission));
  }
}
