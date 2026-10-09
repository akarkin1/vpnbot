package org.github.akarkin1.ui.controller;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.ecs.TaskInfo;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.ui.NodeRef;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.LaunchScreens;
import org.github.akarkin1.ui.screen.NodeScreens;
import org.github.akarkin1.ui.screen.Screen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.regions.Region;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Uses the real {@link NodeAccess} over a mocked {@link Authorizer}, so the scenarios read as the
 * roles of §4.2: alex (RUN_NODES), bob (RUN_NODES), reader (LIST_NODES only), root (ROOT_ACCESS).
 */
@ExtendWith(MockitoExtension.class)
class NodeControllerTest {

  private static final String REGION = "eu-central-1";
  private static final String TASK_ID = "0123456789abcdef0123456789abcdef";
  private static final NodeRef REF = new NodeRef(REGION, TASK_ID);
  private static final Integer MESSAGE_ID = 42;

  private static final UiContext ALEX = new UiContext(100L, "alex", "Alex", "en-US");
  private static final UiContext BOB = new UiContext(300L, "bob", "Bob", "en-US");
  private static final UiContext READER = new UiContext(400L, "reader", "Reader", "en-US");
  private static final UiContext ROOT = new UiContext(200L, "root", "Root", "en-US");

  /** alex's node, started in 2b: tagged with alex's chat id and language. */
  private static final TaskInfo ALEX_NODE = node("alex", "100", "ru");

  private static final Screen ALREADY_STOPPED = screen("alreadyStopped");
  private static final Screen NOT_ALLOWED = screen("notAllowed");
  private static final Screen CONFIRM = screen("confirmStop");
  private static final Screen STOPPING = screen("stopping");
  private static final Screen STOPPED_BY_ADMIN = screen("stoppedByAdmin");
  private static final Screen READY = screen("ready");

  @Mock
  private TailscaleNodeService nodeService;
  @Mock
  private Authorizer authorizer;
  @Mock
  private UiMessenger messenger;
  @Mock
  private NodeScreens nodeScreens;
  @Mock
  private LaunchScreens launchScreens;

  private NodeController controller;

  @BeforeEach
  void setUp() {
    controller = new NodeController(nodeService, new NodeAccess(authorizer), messenger, nodeScreens,
                                    launchScreens);
    grant("alex", Permission.LIST_NODES, Permission.RUN_NODES);
    grant("bob", Permission.LIST_NODES, Permission.RUN_NODES);
    grant("reader", Permission.LIST_NODES);
    grant("root", Permission.ROOT_ACCESS, Permission.LIST_NODES, Permission.RUN_NODES);
    lenient().when(nodeScreens.alreadyStopped()).thenReturn(ALREADY_STOPPED);
    lenient().when(nodeScreens.notAllowed()).thenReturn(NOT_ALLOWED);
    lenient().when(nodeScreens.confirmStop(any())).thenReturn(CONFIRM);
    lenient().when(nodeScreens.stopping(any())).thenReturn(STOPPING);
    lenient().when(nodeScreens.stoppedByAdmin(any())).thenReturn(STOPPED_BY_ADMIN);
    // D-10: the region check may use either of the two service methods
    lenient().when(nodeService.getSupportedRegionIds()).thenReturn(Set.of(REGION, "us-east-1"));
    lenient().when(nodeService.isRegionSupported(REGION)).thenReturn(true);
  }

  // --- stop ---------------------------------------------------------------------------------

  @Test
  @DisplayName("2b AC-3: stop of a node that is not running shows alreadyStopped and stops nothing")
  void stopAlreadyStopped() {
    when(nodeService.getNode(REGION, TASK_ID)).thenReturn(Optional.empty());

    controller.stop(ALEX, MESSAGE_ID, REF);

    verify(messenger).edit(ALEX, MESSAGE_ID, ALREADY_STOPPED);
    verify(nodeService, never()).stopNode(any(), any(), any());
    verify(messenger, never()).send(any(), any());
  }

  @Test
  @DisplayName("2b AC-3: stop of someone else's node by a non-root user shows notAllowed and stops nothing")
  void stopNotAllowedForOtherUser() {
    when(nodeService.getNode(REGION, TASK_ID)).thenReturn(Optional.of(ALEX_NODE));

    controller.stop(BOB, MESSAGE_ID, REF);

    verify(messenger).edit(BOB, MESSAGE_ID, NOT_ALLOWED);
    verify(nodeService, never()).stopNode(any(), any(), any());
    verify(messenger, never()).send(any(), any());
  }

  @Test
  @DisplayName("2b AC-3, D-10: stop by a user without RUN_NODES/ROOT_ACCESS shows notAllowed before any node-service call")
  void stopNotAllowedWithoutRunNodes() {
    controller.stop(READER, MESSAGE_ID, REF);

    verify(messenger).edit(READER, MESSAGE_ID, NOT_ALLOWED);
    verifyNoInteractions(nodeService);
  }

  @Test
  @DisplayName("2b D-10: stop with a null username shows notAllowed before any node-service call")
  void stopNullUsername() {
    UiContext anonymous = new UiContext(500L, null, "Anon", "en-US");

    controller.stop(anonymous, MESSAGE_ID, REF);

    verify(messenger).edit(anonymous, MESSAGE_ID, NOT_ALLOWED);
    verifyNoInteractions(nodeService);
  }

  @Test
  @DisplayName("2b D-10: stop in an unsupported region shows alreadyStopped without calling getNode")
  void stopUnsupportedRegion() {
    NodeRef unknownRegion = new NodeRef("xx-unknown-1", TASK_ID);

    controller.stop(ALEX, MESSAGE_ID, unknownRegion);

    verify(messenger).edit(ALEX, MESSAGE_ID, ALREADY_STOPPED);
    verify(nodeService, never()).getNode(any(), any());
    verify(nodeService, never()).stopNode(any(), any(), any());
  }

  @Test
  @DisplayName("2b AC-3, D-9: owner sees stopping first, then the node is stopped with the reason; nobody is notified")
  void stopByOwner() {
    when(nodeService.getNode(REGION, TASK_ID)).thenReturn(Optional.of(ALEX_NODE));

    controller.stop(ALEX, MESSAGE_ID, REF);

    InOrder inOrder = inOrder(nodeService, messenger);
    inOrder.verify(nodeService).getNode(REGION, TASK_ID);
    inOrder.verify(messenger).edit(ALEX, MESSAGE_ID, STOPPING);
    inOrder.verify(nodeService).stopNode(REGION, TASK_ID, "Stopped by @alex via the bot");
    verify(nodeScreens).stopping(ALEX_NODE);
    verify(nodeScreens, never()).confirmStop(any());
    verify(messenger, never()).send(any(), any());
  }

  @Test
  @DisplayName("2b AC-3: root on someone else's node gets the confirmation screen and nothing is stopped")
  void stopByRootOnOthersNodeAsksForConfirmation() {
    when(nodeService.getNode(REGION, TASK_ID)).thenReturn(Optional.of(ALEX_NODE));

    controller.stop(ROOT, MESSAGE_ID, REF);

    verify(nodeScreens).confirmStop(ALEX_NODE);
    verify(messenger).edit(ROOT, MESSAGE_ID, CONFIRM);
    verify(nodeService, never()).stopNode(any(), any(), any());
    verify(messenger, never()).send(any(), any());
  }

  @Test
  @DisplayName("2b AC-3, D-9: root on its own node stops at once without confirmation, stopping edited first")
  void stopByRootOnOwnNode() {
    TaskInfo rootNode = node("root", "200", "en");
    when(nodeService.getNode(REGION, TASK_ID)).thenReturn(Optional.of(rootNode));

    controller.stop(ROOT, MESSAGE_ID, REF);

    InOrder inOrder = inOrder(nodeService, messenger);
    inOrder.verify(messenger).edit(ROOT, MESSAGE_ID, STOPPING);
    inOrder.verify(nodeService).stopNode(REGION, TASK_ID, "Stopped by @root via the bot");
    verify(nodeScreens, never()).confirmStop(any());
    verify(messenger, never()).send(any(), any());
  }

  // --- confirmStop --------------------------------------------------------------------------

  @Test
  @DisplayName("2b AC-4, D-9: root confirms: root sees stopping, then the node is stopped, the owner is notified in the owner's chat and language")
  void confirmStopByRootNotifiesOwner() {
    when(nodeService.getNode(REGION, TASK_ID)).thenReturn(Optional.of(ALEX_NODE));

    controller.confirmStop(ROOT, MESSAGE_ID, REF);

    InOrder inOrder = inOrder(nodeService, messenger);
    inOrder.verify(nodeService).getNode(REGION, TASK_ID);
    inOrder.verify(messenger).edit(ROOT, MESSAGE_ID, STOPPING);
    inOrder.verify(nodeService).stopNode(REGION, TASK_ID, "Stopped by @root via the bot");
    verify(nodeScreens).stoppedByAdmin(ALEX_NODE);
    UiContext ownerContext = sentContext(STOPPED_BY_ADMIN);
    assertEquals(100L, ownerContext.chatId());
    assertEquals("ru", ownerContext.languageCode());
  }

  @Test
  @DisplayName("2b D-11: the owner notification passes the task's missing language code through as null")
  void confirmStopNotificationWithoutLanguage() {
    when(nodeService.getNode(REGION, TASK_ID)).thenReturn(Optional.of(node("alex", "100", null)));

    controller.confirmStop(ROOT, MESSAGE_ID, REF);

    UiContext ownerContext = sentContext(STOPPED_BY_ADMIN);
    assertEquals(100L, ownerContext.chatId());
    assertNull(ownerContext.languageCode());
  }

  @Test
  @DisplayName("2b D-10: confirmStop with a null username shows notAllowed before any node-service call")
  void confirmStopNullUsername() {
    UiContext anonymous = new UiContext(500L, null, "Anon", "en-US");

    controller.confirmStop(anonymous, MESSAGE_ID, REF);

    verify(messenger).edit(anonymous, MESSAGE_ID, NOT_ALLOWED);
    verifyNoInteractions(nodeService);
    verify(messenger, never()).send(any(), any());
  }

  @Test
  @DisplayName("2b D-10: confirmStop in an unsupported region shows alreadyStopped without calling getNode")
  void confirmStopUnsupportedRegion() {
    controller.confirmStop(ROOT, MESSAGE_ID, new NodeRef("xx-unknown-1", TASK_ID));

    verify(messenger).edit(ROOT, MESSAGE_ID, ALREADY_STOPPED);
    verify(nodeService, never()).getNode(any(), any());
    verify(nodeService, never()).stopNode(any(), any(), any());
    verify(messenger, never()).send(any(), any());
  }

  @Test
  @DisplayName("2b AC-4: confirmStop re-checks the node: not running shows alreadyStopped, nothing is stopped or sent")
  void confirmStopAlreadyStopped() {
    when(nodeService.getNode(REGION, TASK_ID)).thenReturn(Optional.empty());

    controller.confirmStop(ROOT, MESSAGE_ID, REF);

    verify(messenger).edit(ROOT, MESSAGE_ID, ALREADY_STOPPED);
    verify(nodeService, never()).stopNode(any(), any(), any());
    verify(messenger, never()).send(any(), any());
  }

  @Test
  @DisplayName("2b AC-4: confirmStop re-checks access: a non-root user on someone else's node gets notAllowed")
  void confirmStopNotAllowedForNonRoot() {
    when(nodeService.getNode(REGION, TASK_ID)).thenReturn(Optional.of(ALEX_NODE));

    controller.confirmStop(BOB, MESSAGE_ID, REF);

    verify(messenger).edit(BOB, MESSAGE_ID, NOT_ALLOWED);
    verify(nodeService, never()).stopNode(any(), any(), any());
    verify(messenger, never()).send(any(), any());
  }

  @Test
  @DisplayName("2b AC-4, D-10: confirmStop by a read-only user gets notAllowed before any node-service call")
  void confirmStopNotAllowedForReader() {
    controller.confirmStop(READER, MESSAGE_ID, REF);

    verify(messenger).edit(READER, MESSAGE_ID, NOT_ALLOWED);
    verifyNoInteractions(nodeService);
  }

  @Test
  @DisplayName("2b AC-4: a node without ChatId tag (started before 2b) is stopped without notifying the owner")
  void confirmStopWithoutChatIdTag() {
    when(nodeService.getNode(REGION, TASK_ID)).thenReturn(Optional.of(node("alex", null, null)));

    controller.confirmStop(ROOT, MESSAGE_ID, REF);

    verify(nodeService).stopNode(REGION, TASK_ID, "Stopped by @root via the bot");
    verify(messenger).edit(ROOT, MESSAGE_ID, STOPPING);
    verify(messenger, never()).send(any(), any());
  }

  @Test
  @DisplayName("2b AC-4 (D-2), D-9: root confirming the stop of its own node notifies nobody")
  void confirmStopOwnNodeByRootNotifiesNobody() {
    when(nodeService.getNode(REGION, TASK_ID)).thenReturn(Optional.of(node("root", "200", "en")));

    controller.confirmStop(ROOT, MESSAGE_ID, REF);

    InOrder inOrder = inOrder(nodeService, messenger);
    inOrder.verify(messenger).edit(ROOT, MESSAGE_ID, STOPPING);
    inOrder.verify(nodeService).stopNode(REGION, TASK_ID, "Stopped by @root via the bot");
    verify(messenger, never()).send(any(), any());
  }

  @Test
  @DisplayName("2b AC-4 (D-2), D-9: the owner confirming the stop of an own node stops it and notifies nobody")
  void confirmStopByOwnerNotifiesNobody() {
    when(nodeService.getNode(REGION, TASK_ID)).thenReturn(Optional.of(ALEX_NODE));

    controller.confirmStop(ALEX, MESSAGE_ID, REF);

    InOrder inOrder = inOrder(nodeService, messenger);
    inOrder.verify(messenger).edit(ALEX, MESSAGE_ID, STOPPING);
    inOrder.verify(nodeService).stopNode(REGION, TASK_ID, "Stopped by @alex via the bot");
    verify(messenger, never()).send(any(), any());
  }

  @Test
  @DisplayName("2b AC-4 (D-2): a failing owner notification is swallowed; the node is stopped and root sees stopping")
  void confirmStopNotificationFailureSwallowed() {
    when(nodeService.getNode(REGION, TASK_ID)).thenReturn(Optional.of(ALEX_NODE));
    doThrow(new IllegalStateException("chat not found")).when(messenger).send(any(), any());

    assertDoesNotThrow(() -> controller.confirmStop(ROOT, MESSAGE_ID, REF));

    verify(nodeService).stopNode(REGION, TASK_ID, "Stopped by @root via the bot");
    verify(messenger).edit(ROOT, MESSAGE_ID, STOPPING);
  }

  // --- use ----------------------------------------------------------------------------------

  @Test
  @DisplayName("2b AC-5: use shows the node card with the real host, IP and task id")
  void useShowsCard() {
    when(nodeService.getNode(REGION, TASK_ID)).thenReturn(Optional.of(ALEX_NODE));
    when(launchScreens.ready("alex-frankfurt-1", REGION, "1.2.3.4", TASK_ID)).thenReturn(READY);

    controller.use(ALEX, MESSAGE_ID, REF);

    verify(messenger).edit(ALEX, MESSAGE_ID, READY);
    verify(nodeService, never()).stopNode(any(), any(), any());
  }

  @Test
  @DisplayName("2b AC-5: root may open the card of someone else's node")
  void useByRootOnOthersNode() {
    when(nodeService.getNode(REGION, TASK_ID)).thenReturn(Optional.of(ALEX_NODE));
    when(launchScreens.ready("alex-frankfurt-1", REGION, "1.2.3.4", TASK_ID)).thenReturn(READY);

    controller.use(ROOT, MESSAGE_ID, REF);

    verify(messenger).edit(ROOT, MESSAGE_ID, READY);
  }

  @Test
  @DisplayName("2b AC-5: use of a node that is not running shows alreadyStopped")
  void useAlreadyStopped() {
    when(nodeService.getNode(REGION, TASK_ID)).thenReturn(Optional.empty());

    controller.use(ALEX, MESSAGE_ID, REF);

    verify(messenger).edit(ALEX, MESSAGE_ID, ALREADY_STOPPED);
    verify(launchScreens, never()).ready(any(), any(), any(), any());
  }

  @Test
  @DisplayName("2b D-10: use by a read-only user (even of an own node) shows notAllowed before any node-service call")
  void useByReaderNotAllowed() {
    controller.use(READER, MESSAGE_ID, REF);

    verify(messenger).edit(READER, MESSAGE_ID, NOT_ALLOWED);
    verifyNoInteractions(nodeService);
  }

  @Test
  @DisplayName("2b D-10: use with a null username shows notAllowed before any node-service call")
  void useNullUsername() {
    UiContext anonymous = new UiContext(500L, null, "Anon", "en-US");

    controller.use(anonymous, MESSAGE_ID, REF);

    verify(messenger).edit(anonymous, MESSAGE_ID, NOT_ALLOWED);
    verifyNoInteractions(nodeService);
  }

  @Test
  @DisplayName("2b D-10: use in an unsupported region shows alreadyStopped without calling getNode")
  void useUnsupportedRegion() {
    controller.use(ALEX, MESSAGE_ID, new NodeRef("xx-unknown-1", TASK_ID));

    verify(messenger).edit(ALEX, MESSAGE_ID, ALREADY_STOPPED);
    verify(nodeService, never()).getNode(any(), any());
  }

  @Test
  @DisplayName("2b AC-5: use of someone else's node by a non-root user shows notAllowed")
  void useNotAllowed() {
    when(nodeService.getNode(REGION, TASK_ID)).thenReturn(Optional.of(ALEX_NODE));

    controller.use(BOB, MESSAGE_ID, REF);

    verify(messenger).edit(BOB, MESSAGE_ID, NOT_ALLOWED);
    verify(launchScreens, never()).ready(any(), any(), any(), any());
  }

  private UiContext sentContext(Screen screen) {
    ArgumentCaptor<UiContext> captor = ArgumentCaptor.forClass(UiContext.class);
    verify(messenger).send(captor.capture(), eq(screen));
    return captor.getValue();
  }

  private void grant(String username, Permission... permissions) {
    for (Permission permission : permissions) {
      lenient().when(authorizer.hasPermission(username, permission)).thenReturn(true);
    }
  }

  private static TaskInfo node(String runBy, String chatId, String languageCode) {
    return TaskInfo.builder()
        .id(TASK_ID)
        .hostName("alex-frankfurt-1")
        .region(Region.EU_CENTRAL_1)
        .publicIp("1.2.3.4")
        .runBy(runBy)
        .chatId(chatId)
        .languageCode(languageCode)
        .build();
  }

  private static Screen screen(String name) {
    return new Screen(name, List.of(), List.of());
  }

}
