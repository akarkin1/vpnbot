package org.github.akarkin1.ui;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.UserRole;
import org.github.akarkin1.ui.UiAction.AccessDecline;
import org.github.akarkin1.ui.UiAction.AccessGrant;
import org.github.akarkin1.ui.UiAction.AccessToggle;
import org.github.akarkin1.ui.UiAction.ConfirmDeleteUser;
import org.github.akarkin1.ui.UiAction.ConfirmStop;
import org.github.akarkin1.ui.UiAction.DeleteUser;
import org.github.akarkin1.ui.UiAction.Help;
import org.github.akarkin1.ui.UiAction.Home;
import org.github.akarkin1.ui.UiAction.RegionDecline;
import org.github.akarkin1.ui.UiAction.RegionDeployed;
import org.github.akarkin1.ui.UiAction.RegionRequestAction;
import org.github.akarkin1.ui.UiAction.Regions;
import org.github.akarkin1.ui.UiAction.RequestAccess;
import org.github.akarkin1.ui.UiAction.Run;
import org.github.akarkin1.ui.UiAction.RunNew;
import org.github.akarkin1.ui.UiAction.ShowUser;
import org.github.akarkin1.ui.UiAction.Stop;
import org.github.akarkin1.ui.UiAction.ToggleRole;
import org.github.akarkin1.ui.UiAction.Use;
import org.github.akarkin1.ui.UiAction.Users;
import org.github.akarkin1.ui.controller.AccessController;
import org.github.akarkin1.ui.controller.HomeController;
import org.github.akarkin1.ui.controller.LaunchController;
import org.github.akarkin1.ui.controller.NodeController;
import org.github.akarkin1.ui.controller.RegionController;
import org.github.akarkin1.ui.controller.UsersController;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.ErrorScreen;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

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
  private final RegionController regionController;
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
    switch (action) {
      case Home() -> homeController.refreshHome(context, messageId);
      case Help() -> homeController.showHelp(context, messageId);
      case Run(var regionId) -> launchController.launch(context, messageId, regionId);
      case RunNew(var regionId) -> launchController.launchAnother(context, messageId, regionId);
      case Use(var node) -> nodeController.use(context, messageId, node);
      case Stop(var node) -> nodeController.stop(context, messageId, node);
      case ConfirmStop(var node) -> nodeController.confirmStop(context, messageId, node);
      case RequestAccess() -> accessController.request(context, messageId);
      case AccessToggle(var userId, var selected, var role) ->
          accessController.toggle(context, messageId, userId, selected, role);
      case AccessGrant(var userId, var selected) -> grant(context, messageId, userId, selected);
      case AccessDecline(var userId) -> accessController.decline(context, messageId, userId);
      case Users() -> usersController.show(context, messageId);
      case ShowUser(var key) -> usersController.showUser(context, messageId, key);
      case ToggleRole(var key, var role) -> usersController.toggleRole(context, messageId, key, role);
      case DeleteUser(var key) -> usersController.confirmDelete(context, messageId, key);
      case ConfirmDeleteUser(var key) -> usersController.delete(context, messageId, key);
      case Regions() -> regionController.show(context, messageId);
      case RegionRequestAction(var regionId) -> regionController.request(context, messageId, regionId);
      case RegionDeployed(var regionId) -> regionController.deployed(context, messageId, regionId);
      case RegionDecline(var regionId) -> regionController.decline(context, messageId, regionId);
    }
  }

  /** Grant with no role selected only answers the callback (already done). */
  private void grant(UiContext context, Integer messageId, long userId, Set<UserRole> selected) {
    if (selected.isEmpty()) {
      return;
    }
    accessController.grant(context, messageId, userId, selected);
  }

  private void sendGenericError(UiContext context) {
    try {
      messenger.send(context, errorScreen.generic());
    } catch (Exception e) {
      log.error("Failed to send the error message to chat {}", context.chatId(), e);
    }
  }

}
