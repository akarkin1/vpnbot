package org.github.akarkin1.ui;

import org.github.akarkin1.auth.UserRole;
import org.github.akarkin1.ui.controller.AccessController;
import org.github.akarkin1.ui.controller.HomeController;
import org.github.akarkin1.ui.controller.LaunchController;
import org.github.akarkin1.ui.controller.NodeController;
import org.github.akarkin1.ui.controller.UsersController;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.ErrorScreen;
import org.github.akarkin1.ui.screen.Screen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.github.akarkin1.ui.TelegramUpdates.CALLBACK_ID;
import static org.github.akarkin1.ui.TelegramUpdates.CHAT_ID;
import static org.github.akarkin1.ui.TelegramUpdates.MESSAGE_ID;
import static org.github.akarkin1.ui.TelegramUpdates.USER_ID;
import static org.github.akarkin1.ui.TelegramUpdates.callbackUpdate;
import static org.github.akarkin1.ui.TelegramUpdates.messageUpdate;
import static org.github.akarkin1.ui.TelegramUpdates.user;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UiRouterTest {

  private static final UiContext CONTEXT = new UiContext(CHAT_ID, USER_ID, "alex", "Alex", null, "ru");
  private static final Screen ERROR = new Screen("error", List.of(), List.of());
  private static final String TASK_ID = "0123456789abcdef0123456789abcdef";
  private static final NodeRef NODE = new NodeRef("eu-central-1", TASK_ID);

  @Mock
  private HomeController homeController;
  @Mock
  private LaunchController launchController;
  @Mock
  private NodeController nodeController;
  @Mock
  private AccessController accessController;
  @Mock
  private UsersController usersController;
  @Mock
  private UiMessenger messenger;
  @Mock
  private ErrorScreen errorScreen;

  private UiRouter router;

  @BeforeEach
  void setUp() {
    router = new UiRouter(homeController, launchController, nodeController, accessController,
                          usersController, messenger, errorScreen);
  }

  // --- Phase 4 (§4.5): access requests and the Users screen ----------------------------------

  @Test
  @DisplayName("4 AC-8: REQUEST_ACCESS callback is answered first, then requests access")
  void requestAccessCallback() {
    router.handle(callbackUpdate("REQUEST_ACCESS"));

    InOrder inOrder = inOrder(messenger, accessController);
    inOrder.verify(messenger).answerCallback(CALLBACK_ID);
    inOrder.verify(accessController).request(CONTEXT, MESSAGE_ID);
    verifyNoInteractions(usersController, homeController);
  }

  @Test
  @DisplayName("4 AC-8: ACCESS_TOGGLE callback is answered first, then toggles the role with id and mask")
  void accessToggleCallback() {
    router.handle(callbackUpdate("ACCESS_TOGGLE:9999999999:5:READ_ONLY"));

    InOrder inOrder = inOrder(messenger, accessController);
    inOrder.verify(messenger).answerCallback(CALLBACK_ID);
    inOrder.verify(accessController).toggle(CONTEXT, MESSAGE_ID, 9_999_999_999L, 5, UserRole.READ_ONLY);
    verify(accessController, never()).grant(any(), any(), anyLong(), any());
    verify(accessController, never()).decline(any(), any(), anyLong());
  }

  @Test
  @DisplayName("4 AC-8: ACCESS_GRANT callback grants the roles of the mask")
  void accessGrantCallback() {
    router.handle(callbackUpdate("ACCESS_GRANT:123:5"));

    InOrder inOrder = inOrder(messenger, accessController);
    inOrder.verify(messenger).answerCallback(CALLBACK_ID);
    inOrder.verify(accessController).grant(CONTEXT, MESSAGE_ID, 123L,
                                           Set.of(UserRole.NODE_ADMIN, UserRole.USER_ADMIN));
  }

  @Test
  @DisplayName("4 AC-5, §4.3: ACCESS_GRANT with mask 0 only answers the callback, nothing changes")
  void accessGrantWithoutRolesOnlyAnswers() {
    router.handle(callbackUpdate("ACCESS_GRANT:123:0"));

    verify(messenger).answerCallback(CALLBACK_ID);
    verify(messenger, never()).edit(any(), any(), any());
    verify(messenger, never()).send(any(), any());
    verifyNoInteractions(accessController, usersController, homeController);
  }

  @Test
  @DisplayName("4 AC-8: ACCESS_DECLINE callback declines")
  void accessDeclineCallback() {
    router.handle(callbackUpdate("ACCESS_DECLINE:123"));

    InOrder inOrder = inOrder(messenger, accessController);
    inOrder.verify(messenger).answerCallback(CALLBACK_ID);
    inOrder.verify(accessController).decline(CONTEXT, MESSAGE_ID, 123L);
  }

  @Test
  @DisplayName("4 AC-8: USERS callback shows the users list")
  void usersCallback() {
    router.handle(callbackUpdate("USERS"));

    InOrder inOrder = inOrder(messenger, usersController);
    inOrder.verify(messenger).answerCallback(CALLBACK_ID);
    inOrder.verify(usersController).show(CONTEXT, MESSAGE_ID);
    verifyNoInteractions(accessController, homeController);
  }

  @Test
  @DisplayName("4 AC-8: USER callback shows the user card by id and by @username key")
  void userCallback() {
    router.handle(callbackUpdate("USER:123"));
    router.handle(callbackUpdate("USER:@carol"));

    verify(usersController).showUser(CONTEXT, MESSAGE_ID, "123");
    verify(usersController).showUser(CONTEXT, MESSAGE_ID, "@carol");
  }

  @Test
  @DisplayName("4 AC-8: USER_ROLE callback toggles the role of the user")
  void userRoleCallback() {
    router.handle(callbackUpdate("USER_ROLE:@carol:USER_ADMIN"));

    InOrder inOrder = inOrder(messenger, usersController);
    inOrder.verify(messenger).answerCallback(CALLBACK_ID);
    inOrder.verify(usersController).toggleRole(CONTEXT, MESSAGE_ID, "@carol", UserRole.USER_ADMIN);
  }

  @Test
  @DisplayName("4 AC-8: USER_DEL callback asks to confirm the removal")
  void userDeleteCallback() {
    router.handle(callbackUpdate("USER_DEL:123"));

    verify(usersController).confirmDelete(CONTEXT, MESSAGE_ID, "123");
    verify(usersController, never()).delete(any(), any(), any());
  }

  @Test
  @DisplayName("4 AC-8: USER_DEL_CONFIRM callback removes the user")
  void userDeleteConfirmCallback() {
    router.handle(callbackUpdate("USER_DEL_CONFIRM:123"));

    verify(usersController).delete(CONTEXT, MESSAGE_ID, "123");
    verify(usersController, never()).confirmDelete(any(), any(), any());
  }

  @Test
  @DisplayName("4 AC-8: an invalid new callback (unknown role) refreshes home")
  void invalidPhase4CallbackRefreshesHome() {
    router.handle(callbackUpdate("USER_ROLE:123:ROOT"));

    verify(homeController).refreshHome(CONTEXT, MESSAGE_ID);
    verifyNoInteractions(accessController, usersController);
  }

  @Test
  @DisplayName("AC-11: canHandle is true for a callback query")
  void canHandleCallback() {
    assertTrue(router.canHandle(callbackUpdate("HOME")));
  }

  @Test
  @DisplayName("AC-11: canHandle is true for /start, /start xyz and /menu")
  void canHandleStartAndMenu() {
    assertTrue(router.canHandle(messageUpdate("/start")));
    assertTrue(router.canHandle(messageUpdate("/start xyz")));
    assertTrue(router.canHandle(messageUpdate("/menu")));
  }

  @Test
  @DisplayName("AC-11: canHandle is true for plain text and null text")
  void canHandlePlainAndNullText() {
    assertTrue(router.canHandle(messageUpdate("hi")));
    assertTrue(router.canHandle(messageUpdate(null)));
  }

  @Test
  @DisplayName("AC-11: canHandle is false for other commands")
  void cannotHandleOtherCommands() {
    assertFalse(router.canHandle(messageUpdate("/help")));
    assertFalse(router.canHandle(messageUpdate("/runNodeIn Frankfurt")));
    assertFalse(router.canHandle(messageUpdate("/listRunningNodes")));
  }

  @Test
  @DisplayName("AC-11: canHandle is false for commands that only start with /start or /menu")
  void cannotHandleSimilarCommands() {
    assertFalse(router.canHandle(messageUpdate("/startNode")));
    assertFalse(router.canHandle(messageUpdate("/menus")));
  }

  @Test
  @DisplayName("AC-12: message shows home as a new message")
  void messageShowsHome() {
    router.handle(messageUpdate("/start"));

    verify(homeController).showHome(CONTEXT);
    verifyNoInteractions(launchController);
  }

  @Test
  @DisplayName("AC-12: HOME callback is answered first, then refreshes home")
  void homeCallback() {
    router.handle(callbackUpdate("HOME"));

    InOrder inOrder = inOrder(messenger, homeController);
    inOrder.verify(messenger).answerCallback(CALLBACK_ID);
    inOrder.verify(homeController).refreshHome(CONTEXT, MESSAGE_ID);
  }

  @Test
  @DisplayName("AC-12: HELP callback is answered first, then shows help")
  void helpCallback() {
    router.handle(callbackUpdate("HELP"));

    InOrder inOrder = inOrder(messenger, homeController);
    inOrder.verify(messenger).answerCallback(CALLBACK_ID);
    inOrder.verify(homeController).showHelp(CONTEXT, MESSAGE_ID);
  }

  @Test
  @DisplayName("AC-12: RUN callback is answered first, then launches a node in the region")
  void runCallback() {
    router.handle(callbackUpdate("RUN:eu-central-1"));

    InOrder inOrder = inOrder(messenger, launchController);
    inOrder.verify(messenger).answerCallback(CALLBACK_ID);
    inOrder.verify(launchController).launch(CONTEXT, MESSAGE_ID, "eu-central-1");
  }

  @Test
  @DisplayName("2b AC-10: RUN_NEW callback is answered first, then launches another node in the region")
  void runNewCallback() {
    router.handle(callbackUpdate("RUN_NEW:eu-central-1"));

    InOrder inOrder = inOrder(messenger, launchController);
    inOrder.verify(messenger).answerCallback(CALLBACK_ID);
    inOrder.verify(launchController).launchAnother(CONTEXT, MESSAGE_ID, "eu-central-1");
    verify(launchController, never()).launch(any(), any(), any());
    verifyNoInteractions(nodeController);
  }

  @Test
  @DisplayName("2b AC-10: USE callback is answered first, then shows the node")
  void useCallback() {
    router.handle(callbackUpdate("USE:eu-central-1:" + TASK_ID));

    InOrder inOrder = inOrder(messenger, nodeController);
    inOrder.verify(messenger).answerCallback(CALLBACK_ID);
    inOrder.verify(nodeController).use(CONTEXT, MESSAGE_ID, NODE);
    verifyNoInteractions(launchController);
  }

  @Test
  @DisplayName("2b AC-10: STOP callback is answered first, then requests the stop")
  void stopCallback() {
    router.handle(callbackUpdate("STOP:eu-central-1:" + TASK_ID));

    InOrder inOrder = inOrder(messenger, nodeController);
    inOrder.verify(messenger).answerCallback(CALLBACK_ID);
    inOrder.verify(nodeController).stop(CONTEXT, MESSAGE_ID, NODE);
    verify(nodeController, never()).confirmStop(any(), any(), any());
  }

  @Test
  @DisplayName("2b AC-10: STOP_CONFIRM callback is answered first, then confirms the stop")
  void stopConfirmCallback() {
    router.handle(callbackUpdate("STOP_CONFIRM:eu-central-1:" + TASK_ID));

    InOrder inOrder = inOrder(messenger, nodeController);
    inOrder.verify(messenger).answerCallback(CALLBACK_ID);
    inOrder.verify(nodeController).confirmStop(CONTEXT, MESSAGE_ID, NODE);
    verify(nodeController, never()).stop(any(), any(), any());
  }

  @Test
  @DisplayName("2b AC-10: STOP with the unfilled {{TASK_ID}} placeholder reaches the node controller")
  void stopWithPlaceholderCallback() {
    router.handle(callbackUpdate("STOP:eu-central-1:{{TASK_ID}}"));

    verify(nodeController).stop(CONTEXT, MESSAGE_ID, new NodeRef("eu-central-1", "{{TASK_ID}}"));
  }

  @Test
  @DisplayName("2b AC-10: STOP without a task id is undecodable and refreshes home")
  void stopWithoutTaskIdRefreshesHome() {
    router.handle(callbackUpdate("STOP:eu-central-1"));

    verify(homeController).refreshHome(CONTEXT, MESSAGE_ID);
    verifyNoInteractions(nodeController);
  }

  @Test
  @DisplayName("AC-12: undecodable callback data refreshes home")
  void undecodableCallback() {
    router.handle(callbackUpdate("garbage"));

    InOrder inOrder = inOrder(messenger, homeController);
    inOrder.verify(messenger).answerCallback(CALLBACK_ID);
    inOrder.verify(homeController).refreshHome(CONTEXT, MESSAGE_ID);
    verifyNoInteractions(launchController, nodeController);
  }

  @Test
  @DisplayName("AC-12: callback without a message shows home as a new message")
  void callbackWithoutMessage() {
    router.handle(callbackUpdate(user("alex", "Alex", "ru"), "HOME", false));

    InOrder inOrder = inOrder(messenger, homeController);
    inOrder.verify(messenger).answerCallback(CALLBACK_ID);
    inOrder.verify(homeController).showHome(any(UiContext.class));
  }

  @Test
  @DisplayName("AC-12: controller exception sends the generic error and is not thrown")
  void controllerExceptionSendsGenericError() {
    when(errorScreen.generic()).thenReturn(ERROR);
    doThrow(new IllegalStateException("boom")).when(launchController)
        .launch(CONTEXT, MESSAGE_ID, "eu-central-1");

    assertDoesNotThrow(() -> router.handle(callbackUpdate("RUN:eu-central-1")));

    verify(messenger).send(CONTEXT, ERROR);
  }

  @Test
  @DisplayName("AC-12: exception on a message sends the generic error and is not thrown")
  void messageExceptionSendsGenericError() {
    when(errorScreen.generic()).thenReturn(ERROR);
    doThrow(new IllegalStateException("boom")).when(homeController).showHome(CONTEXT);

    assertDoesNotThrow(() -> router.handle(messageUpdate("hi")));

    verify(messenger).send(CONTEXT, ERROR);
  }

  @Test
  @DisplayName("AC-12: failure to send the generic error is swallowed")
  void errorSendFailureSwallowed() {
    when(errorScreen.generic()).thenReturn(ERROR);
    doThrow(new IllegalStateException("boom")).when(homeController).refreshHome(CONTEXT, MESSAGE_ID);
    doThrow(new IllegalStateException("telegram down")).when(messenger).send(CONTEXT, ERROR);

    assertDoesNotThrow(() -> router.handle(callbackUpdate("HOME")));
  }

}
