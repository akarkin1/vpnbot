package org.github.akarkin1.dispatcher;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.dispatcher.command.BotCommand;
import org.github.akarkin1.dispatcher.response.TextCommandResponse;
import org.github.akarkin1.tg.BotCommunicator;
import org.github.akarkin1.tg.TgRequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

import static org.github.akarkin1.ui.TelegramUpdates.USER_ID;
import static org.github.akarkin1.ui.TelegramUpdates.messageUpdate;
import static org.github.akarkin1.ui.TelegramUpdates.user;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The permission check of text commands uses the TgUser of the request (§4.1). */
@ExtendWith(MockitoExtension.class)
class CommandDispatcherTest {

  @Mock
  private BotCommunicator botCommunicator;
  @Mock
  private Authorizer authorizer;

  private CommandDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    dispatcher = new CommandDispatcher(botCommunicator, authorizer);
    dispatcher.registerCommand("/ping", new PingCommand());
  }

  @AfterEach
  void resetContext() {
    TgRequestContext.initContext(new Update());
  }

  @Test
  @DisplayName("4 §4.1: a command runs when the TgUser (id + username) of the request has its permission")
  void runsWithPermissionOfTgUser() {
    Update update = messageUpdate(user("alex", "Alex", "en"), "/ping");
    TgRequestContext.initContext(update);
    when(authorizer.hasPermission(new TgUser(USER_ID, "alex"), Permission.LIST_NODES)).thenReturn(true);

    dispatcher.handle(update);

    verify(botCommunicator).sendMessageToTheBot("pong");
  }

  @Test
  @DisplayName("4 §4.1: a user without a username is checked by the user id")
  void userWithoutUsernameCheckedById() {
    Update update = messageUpdate(user(null, "Anna", "en"), "/ping");
    TgRequestContext.initContext(update);
    when(authorizer.hasPermission(new TgUser(USER_ID, null), Permission.LIST_NODES)).thenReturn(true);

    dispatcher.handle(update);

    verify(botCommunicator).sendMessageToTheBot("pong");
  }

  @Test
  @DisplayName("§4.6 (unchanged): without the permission the command is refused")
  void refusedWithoutPermission() {
    Update update = messageUpdate(user("alex", "Alex", "en"), "/ping");
    TgRequestContext.initContext(update);

    dispatcher.handle(update);

    verify(botCommunicator).sendMessageToTheBot("${user.not-authorized.error}");
    verify(botCommunicator, never()).sendMessageToTheBot("pong");
  }

  private static final class PingCommand implements BotCommand<TextCommandResponse> {

    @Override
    public TextCommandResponse run(List<String> args) {
      return new TextCommandResponse("pong");
    }

    @Override
    public String getDescription() {
      return "ping";
    }

    @Override
    public List<Permission> getRequiredPermissions() {
      return List.of(Permission.LIST_NODES);
    }

  }

}
