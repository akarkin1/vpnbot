package org.github.akarkin1.ui.controller;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.ecs.TaskInfo;
import org.github.akarkin1.tailscale.NodeOwner;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.messenger.NodeNotifications;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.LaunchScreens;
import org.github.akarkin1.ui.screen.Screen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.regions.Region;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LaunchControllerTest {

  private static final String REGION = "eu-central-1";
  private static final UiContext CONTEXT = new UiContext(100L, "alex", "Alex", "en-US");
  private static final NodeOwner OWNER = new NodeOwner("alex", 100L, "en-US");
  private static final Integer MESSAGE_ID = 42;
  private static final Integer NEW_MESSAGE_ID = 77;
  private static final String HOST_NAME = "node-7";
  private static final Map<String, String> ENV = Map.of("TG_CHAT_ID", "100", "TG_MESSAGE_ID", "42");
  private static final Map<String, String> NEW_MESSAGE_ENV = Map.of("TG_CHAT_ID", "100",
                                                                    "TG_MESSAGE_ID", "77");
  private static final TaskInfo TASK = TaskInfo.builder()
      .id("task-1")
      .cluster("cluster-1")
      .region(Region.EU_CENTRAL_1)
      .build();
  private static final TaskInfo OWN_NODE_HERE = node("task-1", "alex-frankfurt-1", Region.EU_CENTRAL_1);
  private static final TaskInfo OWN_NODE_ELSEWHERE = node("task-2", "alex-nvirginia-1", Region.US_EAST_1);

  private static final Screen NOT_ALLOWED = screen("notAllowed");
  private static final Screen REGION_UNAVAILABLE = screen("regionUnavailable");
  private static final Screen STARTING = screen("starting");
  private static final Screen WAITING = screen("waiting");
  private static final Screen FAILED = screen("failed");
  private static final Screen EXISTING = screen("existingNodes");

  @Mock
  private TailscaleNodeService nodeService;
  @Mock
  private Authorizer authorizer;
  @Mock
  private UiMessenger messenger;
  @Mock
  private LaunchScreens launchScreens;
  @Mock
  private NodeNotifications nodeNotifications;

  private LaunchController controller;

  @BeforeEach
  void setUp() {
    controller = new LaunchController(nodeService, authorizer, messenger, launchScreens,
                                      nodeNotifications);
    lenient().when(launchScreens.notAllowed()).thenReturn(NOT_ALLOWED);
    lenient().when(launchScreens.regionUnavailable()).thenReturn(REGION_UNAVAILABLE);
    lenient().when(launchScreens.starting(REGION)).thenReturn(STARTING);
    lenient().when(launchScreens.waiting(REGION)).thenReturn(WAITING);
    lenient().when(launchScreens.failed(REGION)).thenReturn(FAILED);
  }

  @Test
  @DisplayName("2a AC-1: null username is not allowed and makes no service calls")
  void nullUsernameNotAllowed() {
    UiContext anonymous = new UiContext(100L, null, "Alex", "en-US");

    controller.launch(anonymous, MESSAGE_ID, REGION);

    verify(messenger).edit(anonymous, MESSAGE_ID, NOT_ALLOWED);
    verify(messenger, never()).edit(anonymous, MESSAGE_ID, STARTING);
    verifyNoInteractions(nodeService, nodeNotifications);
  }

  @Test
  @DisplayName("2a AC-1: user without RUN_NODES is not allowed and nothing is started")
  void withoutRunNodesNotAllowed() {
    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    verify(authorizer).hasPermission("alex", Permission.RUN_NODES);
    verify(messenger).edit(CONTEXT, MESSAGE_ID, NOT_ALLOWED);
    verify(messenger, never()).edit(CONTEXT, MESSAGE_ID, STARTING);
    verifyNoInteractions(nodeService, nodeNotifications);
  }

  @Test
  @DisplayName("2a AC-1, 2b AC-6: unsupported region shows starting, then regionUnavailable, and nothing is listed or started")
  void regionUnavailable() {
    allowRun();
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of("us-east-1"));

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(messenger);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, STARTING);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, REGION_UNAVAILABLE);
    verify(nodeService, never()).runNode(any(), any(), any(), any());
    verify(nodeService, never()).listTasks(any());
    verifyNoInteractions(nodeNotifications);
  }

  @Test
  @DisplayName("2a AC-1: starting is edited before the region check")
  void startingBeforeRegionCheck() {
    allowRun();
    supportRegion();
    when(nodeNotifications.build(CONTEXT, MESSAGE_ID, REGION)).thenReturn(ENV);
    when(nodeService.runNode(REGION, OWNER, null, ENV)).thenReturn(TASK);

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(messenger, nodeService);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, STARTING);
    inOrder.verify(nodeService).getSupportedRegionIds();
  }

  @Test
  @DisplayName("2a AC-1, 2b AC-6: without own nodes in the region, the node is run with the notifications env and a null hostName, then waiting is shown")
  void success() {
    allowRun();
    supportRegion();
    when(nodeNotifications.build(CONTEXT, MESSAGE_ID, REGION)).thenReturn(ENV);
    when(nodeService.runNode(REGION, OWNER, null, ENV)).thenReturn(TASK);

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(messenger, nodeService, nodeNotifications);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, STARTING);
    inOrder.verify(nodeService).getSupportedRegionIds();
    inOrder.verify(nodeService).listTasks("alex");
    inOrder.verify(nodeNotifications).build(CONTEXT, MESSAGE_ID, REGION);
    inOrder.verify(nodeService).runNode(REGION, OWNER, null, ENV);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, WAITING);
    verify(messenger, never()).edit(CONTEXT, MESSAGE_ID, FAILED);
    verify(messenger, never()).send(any(), any());
    verifyNoMoreInteractions(nodeService);
  }

  @Test
  @DisplayName("2a AC-1, 2b AC-6: runNode throwing shows failed after starting, no waiting")
  void runNodeThrows() {
    allowRun();
    supportRegion();
    when(nodeNotifications.build(CONTEXT, MESSAGE_ID, REGION)).thenReturn(ENV);
    when(nodeService.runNode(REGION, OWNER, null, ENV))
        .thenThrow(new IllegalStateException("boom"));

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(messenger, nodeService);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, STARTING);
    inOrder.verify(nodeService).runNode(REGION, OWNER, null, ENV);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, FAILED);
    verify(messenger, never()).edit(CONTEXT, MESSAGE_ID, WAITING);
    verify(nodeService).getSupportedRegionIds();
    verify(nodeService).listTasks("alex");
    verifyNoMoreInteractions(nodeService);
  }

  @Test
  @DisplayName("2b AC-6: launch with an own node in the region shows existingNodes and does not run a node")
  void launchShowsExistingNodes() {
    allowRun();
    supportRegion();
    when(nodeService.listTasks("alex")).thenReturn(List.of(OWN_NODE_HERE));
    when(launchScreens.existingNodes(REGION, List.of(OWN_NODE_HERE))).thenReturn(EXISTING);

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(messenger, nodeService);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, STARTING);
    inOrder.verify(nodeService).getSupportedRegionIds();
    inOrder.verify(nodeService).listTasks("alex");
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, EXISTING);
    verify(nodeService, never()).runNode(any(), any(), any(), any());
    verify(messenger, never()).edit(CONTEXT, MESSAGE_ID, WAITING);
    verifyNoInteractions(nodeNotifications);
  }

  @Test
  @DisplayName("2b AC-6: existingNodes gets only the user's nodes in the requested region, in list order")
  void existingNodesFilteredToRegion() {
    allowRun();
    supportRegion();
    TaskInfo secondHere = node("task-3", "alex-frankfurt-2", Region.EU_CENTRAL_1);
    when(nodeService.listTasks("alex"))
        .thenReturn(List.of(OWN_NODE_HERE, OWN_NODE_ELSEWHERE, secondHere));
    when(launchScreens.existingNodes(REGION, List.of(OWN_NODE_HERE, secondHere)))
        .thenReturn(EXISTING);

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    verify(messenger).edit(CONTEXT, MESSAGE_ID, EXISTING);
    verify(nodeService, never()).runNode(any(), any(), any(), any());
  }

  @Test
  @DisplayName("2b AC-6: own nodes only in other regions keep the unchanged 2a flow")
  void nodesInOtherRegionsOnly() {
    allowRun();
    supportRegion();
    when(nodeService.listTasks("alex")).thenReturn(List.of(OWN_NODE_ELSEWHERE));
    when(nodeNotifications.build(CONTEXT, MESSAGE_ID, REGION)).thenReturn(ENV);
    when(nodeService.runNode(REGION, OWNER, null, ENV)).thenReturn(TASK);

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    verify(nodeService).runNode(REGION, OWNER, null, ENV);
    verify(messenger).edit(CONTEXT, MESSAGE_ID, WAITING);
    verify(launchScreens, never()).existingNodes(any(), any());
  }

  @Test
  @DisplayName("2b AC-6: the reuse check lists only the user's own nodes, also for root")
  void reuseCheckUsesOwnNodesForRoot() {
    allowRun();
    lenient().when(authorizer.hasPermission("alex", Permission.ROOT_ACCESS)).thenReturn(true);
    supportRegion();
    when(nodeNotifications.build(CONTEXT, MESSAGE_ID, REGION)).thenReturn(ENV);
    when(nodeService.runNode(REGION, OWNER, null, ENV)).thenReturn(TASK);

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    verify(nodeService).listTasks("alex");
    verify(nodeService, never()).listTasks(null);
    verify(nodeService).runNode(REGION, OWNER, null, ENV);
  }

  @Test
  @DisplayName("2b AC-6: runNode receives the NodeOwner built from the context (username, chat id, language)")
  void runNodeReceivesOwnerFromContext() {
    UiContext russian = new UiContext(555L, "masha", "Masha", "ru");
    when(authorizer.hasPermission("masha", Permission.RUN_NODES)).thenReturn(true);
    supportRegion();
    when(nodeNotifications.build(russian, MESSAGE_ID, REGION)).thenReturn(ENV);

    controller.launch(russian, MESSAGE_ID, REGION);

    verify(nodeService).runNode(REGION, new NodeOwner("masha", 555L, "ru"), null, ENV);
  }

  @Test
  @DisplayName("2b AC-6: launchAnother skips the reuse check and runs a node although the user has one in the region")
  void launchAnotherSkipsReuseCheck() {
    allowRun();
    supportRegion();
    lenient().when(nodeService.listTasks("alex")).thenReturn(List.of(OWN_NODE_HERE));
    when(nodeNotifications.build(CONTEXT, MESSAGE_ID, REGION)).thenReturn(ENV);
    when(nodeService.runNode(REGION, OWNER, null, ENV)).thenReturn(TASK);

    controller.launchAnother(CONTEXT, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(messenger, nodeService, nodeNotifications);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, STARTING);
    inOrder.verify(nodeService).getSupportedRegionIds();
    inOrder.verify(nodeNotifications).build(CONTEXT, MESSAGE_ID, REGION);
    inOrder.verify(nodeService).runNode(REGION, OWNER, null, ENV);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, WAITING);
    verify(nodeService, never()).listTasks(any());
    verify(launchScreens, never()).existingNodes(any(), any());
  }

  @Test
  @DisplayName("2b AC-6: launchAnother without RUN_NODES is not allowed and nothing is started")
  void launchAnotherNotAllowed() {
    controller.launchAnother(CONTEXT, MESSAGE_ID, REGION);

    verify(messenger).edit(CONTEXT, MESSAGE_ID, NOT_ALLOWED);
    verify(messenger, never()).edit(CONTEXT, MESSAGE_ID, STARTING);
    verifyNoInteractions(nodeService, nodeNotifications);
  }

  @Test
  @DisplayName("2b AC-6: launchAnother with a null username is not allowed and makes no service calls")
  void launchAnotherNullUsername() {
    UiContext anonymous = new UiContext(100L, null, "Alex", "en-US");

    controller.launchAnother(anonymous, MESSAGE_ID, REGION);

    verify(messenger).edit(anonymous, MESSAGE_ID, NOT_ALLOWED);
    verifyNoInteractions(nodeService, nodeNotifications);
  }

  @Test
  @DisplayName("2b AC-6: launchAnother with an unsupported region shows starting, then regionUnavailable")
  void launchAnotherRegionUnavailable() {
    allowRun();
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of("us-east-1"));

    controller.launchAnother(CONTEXT, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(messenger);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, STARTING);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, REGION_UNAVAILABLE);
    verify(nodeService, never()).runNode(any(), any(), any(), any());
    verifyNoInteractions(nodeNotifications);
  }

  @Test
  @DisplayName("2b AC-6: launchAnother shows failed when runNode throws")
  void launchAnotherRunNodeThrows() {
    allowRun();
    supportRegion();
    when(nodeNotifications.build(CONTEXT, MESSAGE_ID, REGION)).thenReturn(ENV);
    when(nodeService.runNode(REGION, OWNER, null, ENV))
        .thenThrow(new IllegalStateException("boom"));

    controller.launchAnother(CONTEXT, MESSAGE_ID, REGION);

    verify(messenger).edit(CONTEXT, MESSAGE_ID, FAILED);
    verify(messenger, never()).edit(CONTEXT, MESSAGE_ID, WAITING);
  }

  @Test
  @DisplayName("2a AC-2, 2b AC-6: launchInNewMessage sends starting and uses its id for the notifications and the following edits (no reuse check)")
  void newMessageSuccess() {
    supportRegion();
    when(messenger.send(CONTEXT, STARTING)).thenReturn(NEW_MESSAGE_ID);
    when(nodeNotifications.build(CONTEXT, NEW_MESSAGE_ID, REGION)).thenReturn(NEW_MESSAGE_ENV);
    when(nodeService.runNode(REGION, OWNER, HOST_NAME, NEW_MESSAGE_ENV)).thenReturn(TASK);

    controller.launchInNewMessage(CONTEXT, REGION, HOST_NAME);

    InOrder inOrder = inOrder(messenger, nodeService, nodeNotifications);
    inOrder.verify(messenger).send(CONTEXT, STARTING);
    inOrder.verify(nodeService).getSupportedRegionIds();
    inOrder.verify(nodeNotifications).build(CONTEXT, NEW_MESSAGE_ID, REGION);
    inOrder.verify(nodeService).runNode(REGION, OWNER, HOST_NAME, NEW_MESSAGE_ENV);
    inOrder.verify(messenger).edit(CONTEXT, NEW_MESSAGE_ID, WAITING);
    verify(messenger, times(1)).edit(any(), any(), any());
    verifyNoMoreInteractions(nodeService);
  }

  @Test
  @DisplayName("2a AC-2: launchInNewMessage passes a null hostName through")
  void newMessageNullHostName() {
    supportRegion();
    when(messenger.send(CONTEXT, STARTING)).thenReturn(NEW_MESSAGE_ID);
    when(nodeNotifications.build(CONTEXT, NEW_MESSAGE_ID, REGION)).thenReturn(NEW_MESSAGE_ENV);
    when(nodeService.runNode(REGION, OWNER, null, NEW_MESSAGE_ENV)).thenReturn(TASK);

    controller.launchInNewMessage(CONTEXT, REGION, null);

    verify(nodeService).runNode(REGION, OWNER, null, NEW_MESSAGE_ENV);
    verify(messenger).edit(CONTEXT, NEW_MESSAGE_ID, WAITING);
  }

  @Test
  @DisplayName("2a AC-2: launchInNewMessage with an unsupported region edits the new message to regionUnavailable")
  void newMessageRegionUnavailable() {
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of("us-east-1"));
    when(messenger.send(CONTEXT, STARTING)).thenReturn(NEW_MESSAGE_ID);

    controller.launchInNewMessage(CONTEXT, REGION, HOST_NAME);

    InOrder inOrder = inOrder(messenger);
    inOrder.verify(messenger).send(CONTEXT, STARTING);
    inOrder.verify(messenger).edit(CONTEXT, NEW_MESSAGE_ID, REGION_UNAVAILABLE);
    verify(nodeService, never()).runNode(any(), any(), any(), any());
    verifyNoInteractions(nodeNotifications);
  }

  @Test
  @DisplayName("2a AC-2: launchInNewMessage edits the new message to failed when runNode throws")
  void newMessageRunNodeThrows() {
    supportRegion();
    when(messenger.send(CONTEXT, STARTING)).thenReturn(NEW_MESSAGE_ID);
    when(nodeNotifications.build(CONTEXT, NEW_MESSAGE_ID, REGION)).thenReturn(NEW_MESSAGE_ENV);
    when(nodeService.runNode(REGION, OWNER, HOST_NAME, NEW_MESSAGE_ENV))
        .thenThrow(new IllegalStateException("boom"));

    controller.launchInNewMessage(CONTEXT, REGION, HOST_NAME);

    verify(messenger).edit(CONTEXT, NEW_MESSAGE_ID, FAILED);
    verify(messenger, never()).edit(CONTEXT, NEW_MESSAGE_ID, WAITING);
  }

  private void allowRun() {
    when(authorizer.hasPermission("alex", Permission.RUN_NODES)).thenReturn(true);
  }

  private void supportRegion() {
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of(REGION, "us-east-1"));
  }

  private static TaskInfo node(String id, String hostName, Region region) {
    return TaskInfo.builder()
        .id(id)
        .hostName(hostName)
        .region(region)
        .runBy("alex")
        .build();
  }

  private static Screen screen(String name) {
    return new Screen(name, List.of(), List.of());
  }

}
