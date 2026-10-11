package org.github.akarkin1.ui;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.RoleSet;
import org.github.akarkin1.ui.controller.AccessController;
import org.github.akarkin1.ui.controller.HomeController;
import org.github.akarkin1.ui.controller.LaunchController;
import org.github.akarkin1.ui.controller.NodeController;
import org.github.akarkin1.ui.controller.UsersController;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.ErrorScreen;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Optional;
import java.util.Set;

@Log4j2
@RequiredArgsConstructor
public class UiRouter {

  private static final String COMMAND_PREFIX = "/";
  private static final Set<String> UI_COMMANDS = Set.of("/start", "/menu");

  private final HomeController homeController;
  private final LaunchController launchController;
  private final NodeController nodeController;
  private final AccessController accessController;
  private final UsersController usersController;
  private final UiMessenger messenger;
  private final ErrorScreen errorScreen;

  public boolean canHandle(Update update) {
    if (update.hasCallbackQuery()) {
      return true;
    }

    if (!update.hasMessage()) {
      return false;
    }

    String text = update.getMessage().getText();
    return text == null
           || !text.startsWith(COMMAND_PREFIX)
           || UI_COMMANDS.contains(text.split("\\s+", 2)[0]);
  }

  public void handle(Update update) {
    UiContext context;
    try {
      context = UiContext.fromUpdate(update);
    } catch (RuntimeException e) {
      log.error("Failed to read the UI context from update: {}", update, e);
      return;
    }

    try {
      route(update, context);
    } catch (Exception e) {
      log.error("Failed to handle UI update: {}", update, e);
      sendGenericError(context);
    }
  }

  private void route(Update update, UiContext context) {
    if (!update.hasCallbackQuery()) {
      homeController.showHome(context);
      return;
    }

    CallbackQuery callbackQuery = update.getCallbackQuery();
    messenger.answerCallback(callbackQuery.getId());

    Message callbackMessage = callbackQuery.getMessage();
    if (callbackMessage == null) {
      homeController.showHome(context);
      return;
    }

    Integer messageId = callbackMessage.getMessageId();
    UiAction.decode(callbackQuery.getData())
        .ifPresentOrElse(action -> dispatch(context, messageId, action),
                         () -> homeController.refreshHome(context, messageId));
  }

  private void dispatch(UiContext context, Integer messageId, UiAction action) {
    switch (action.type()) {
      case HOME -> homeController.refreshHome(context, messageId);
      case HELP -> homeController.showHelp(context, messageId);
      case RUN -> launchController.launch(context, messageId, action.arg());
      case RUN_NEW -> launchController.launchAnother(context, messageId, action.arg());
      case USE -> nodeController.use(context, messageId, action.nodeRef().orElseThrow());
      case STOP -> nodeController.stop(context, messageId, action.nodeRef().orElseThrow());
      case STOP_CONFIRM -> nodeController.confirmStop(context, messageId, action.nodeRef().orElseThrow());
      case REQUEST_ACCESS -> accessController.request(context, messageId);
      case ACCESS_TOGGLE -> accessController.toggle(context, messageId, action.userId().orElseThrow(),
                                                    action.roleMask().orElseThrow(),
                                                    action.role().orElseThrow());
      case ACCESS_GRANT -> grant(context, messageId, action);
      case ACCESS_DECLINE -> accessController.decide(context, messageId, action.userId().orElseThrow(),
                                                     Optional.empty());
      case USERS -> usersController.show(context, messageId);
      case USER -> usersController.showUser(context, messageId, action.userKey().orElseThrow());
      case USER_ROLE -> usersController.toggleRole(context, messageId, action.userKey().orElseThrow(),
                                                   action.role().orElseThrow());
      case USER_DEL -> usersController.confirmDelete(context, messageId, action.userKey().orElseThrow());
      case USER_DEL_CONFIRM -> usersController.delete(context, messageId, action.userKey().orElseThrow());
    }
  }

  /** Grant with no role selected only answers the callback (already done). */
  private void grant(UiContext context, Integer messageId, UiAction action) {
    int mask = action.roleMask().orElseThrow();
    if (mask == 0) {
      return;
    }
    accessController.decide(context, messageId, action.userId().orElseThrow(),
                            Optional.of(RoleSet.decode(mask)));
  }

  private void sendGenericError(UiContext context) {
    try {
      messenger.send(context, errorScreen.generic());
    } catch (Exception e) {
      log.error("Failed to send the error message to chat {}", context.chatId(), e);
    }
  }

}
