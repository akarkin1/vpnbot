package org.github.akarkin1.dispatcher;

import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.dispatcher.command.BotCommand;
import org.github.akarkin1.dispatcher.command.HelpCommand;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.ErrorScreen;
import org.github.akarkin1.ui.screen.Screen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Runs the system commands ({@code /help}, {@code /version}, …) and sends their replies. */
@Log4j2
public class CommandDispatcher implements CommandCatalog {

  private static final String NOT_SUPPORTED = "${common.command.not-supported.error}";
  private static final String NOT_AUTHORIZED = "${user.not-authorized.error}";

  private final Map<String, BotCommand> registeredCommands = new LinkedHashMap<>();
  private final ErrorScreen errorScreen = new ErrorScreen();

  private final UiMessenger messenger;

  private final Authorizer authorizer;

  public CommandDispatcher(UiMessenger messenger, Authorizer authorizer) {
    this.messenger = messenger;
    this.authorizer = authorizer;
    this.registerCommand("/help", new HelpCommand(this));
  }

  public void registerCommand(String name, BotCommand command) {
    registeredCommands.put(name, command);
  }

  /** Answers a message whose text starts with {@code /}. */
  public void handle(UiContext context, String text) {
    try {
      messenger.send(context, reply(context, text));
    } catch (Exception e) {
      log.error("Failed to handle the command '{}' of user {}", text, context.user(), e);
      sendGenericError(context);
    }
  }

  private Screen reply(UiContext context, String text) {
    String[] commandAndArgs = text.trim().split("\\s+");
    String name = commandAndArgs[0];
    List<String> args = Stream.of(commandAndArgs)
        .skip(1)
        .toList();

    TgUser user = context.user();
    BotCommand command = registeredCommands.get(name);
    if (command == null) {
      log.debug("User {} sent an unsupported command: {}", user, name);
      return BotCommand.reply(NOT_SUPPORTED + supportedCommands(user), name);
    }

    if (!isAuthorizedToUseCommand(command, user)) {
      log.debug("User {} is not authorized to run the command {}. Permissions required: {}",
                user, name, command.getRequiredPermissions());
      return BotCommand.reply(NOT_AUTHORIZED);
    }

    log.debug("Running command: {}, with arguments: {}", name, args);
    return command.run(context, args);
  }

  @Override
  public String supportedCommands(TgUser user) {
    return registeredCommands.entrySet()
        .stream()
        .filter(command -> isAuthorizedToUseCommand(command.getValue(), user))
        .map(entry -> "  %s – %s".formatted(entry.getKey(), entry.getValue().getDescription()))
        .collect(Collectors.joining(System.lineSeparator()));
  }

  private boolean isAuthorizedToUseCommand(BotCommand command, TgUser user) {
    return command.getRequiredPermissions()
        .stream()
        .allMatch(permission -> authorizer.hasPermission(user, permission));
  }

  private void sendGenericError(UiContext context) {
    try {
      messenger.send(context, errorScreen.generic());
    } catch (Exception e) {
      log.error("Failed to send the error message to chat {}", context.chatId(), e);
    }
  }

}
