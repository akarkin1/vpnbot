package org.github.akarkin1.ui;

import org.github.akarkin1.ui.controller.HomeController;
import org.github.akarkin1.ui.controller.LaunchController;
import org.github.akarkin1.ui.controller.NodeController;
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

import static org.github.akarkin1.ui.TelegramUpdates.CALLBACK_ID;
import static org.github.akarkin1.ui.TelegramUpdates.CHAT_ID;
import static org.github.akarkin1.ui.TelegramUpdates.MESSAGE_ID;
import static org.github.akarkin1.ui.TelegramUpdates.callbackUpdate;
import static org.github.akarkin1.ui.TelegramUpdates.messageUpdate;
import static org.github.akarkin1.ui.TelegramUpdates.user;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UiRouterTest {

  private static final UiContext CONTEXT = new UiContext(CHAT_ID, "alex", "Alex", "ru");
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
  private UiMessenger messenger;
  @Mock
  private ErrorScreen errorScreen;

  private UiRouter router;

  @BeforeEach
  void setUp() {
    router = new UiRouter(homeController, launchController, nodeController, messenger, errorScreen);
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
