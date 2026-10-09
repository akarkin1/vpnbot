package org.github.akarkin1.ui.controller;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.ecs.TaskInfo;
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

  private static final Screen NOT_ALLOWED = screen("notAllowed");
  private static final Screen REGION_UNAVAILABLE = screen("regionUnavailable");
  private static final Screen STARTING = screen("starting");
  private static final Screen WAITING = screen("waiting");
  private static final Screen FAILED = screen("failed");

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
  @DisplayName("2a AC-1: unsupported region shows starting, then regionUnavailable, and nothing is started")
  void regionUnavailable() {
    allowRun();
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of("us-east-1"));

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(messenger);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, STARTING);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, REGION_UNAVAILABLE);
    verify(nodeService, never()).runNode(any(), any(), any(), any());
    verifyNoInteractions(nodeNotifications);
  }

  @Test
  @DisplayName("2a AC-1: starting is edited before the region check")
  void startingBeforeRegionCheck() {
    allowRun();
    supportRegion();
    when(nodeNotifications.build(CONTEXT, MESSAGE_ID, REGION)).thenReturn(ENV);
    when(nodeService.runNode(REGION, "alex", null, ENV)).thenReturn(TASK);

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(messenger, nodeService);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, STARTING);
    inOrder.verify(nodeService).getSupportedRegionIds();
  }

  @Test
  @DisplayName("2a AC-1: success runs the node with the notifications env and a null hostName, then shows waiting")
  void success() {
    allowRun();
    supportRegion();
    when(nodeNotifications.build(CONTEXT, MESSAGE_ID, REGION)).thenReturn(ENV);
    when(nodeService.runNode(REGION, "alex", null, ENV)).thenReturn(TASK);

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(messenger, nodeService, nodeNotifications);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, STARTING);
    inOrder.verify(nodeService).getSupportedRegionIds();
    inOrder.verify(nodeNotifications).build(CONTEXT, MESSAGE_ID, REGION);
    inOrder.verify(nodeService).runNode(REGION, "alex", null, ENV);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, WAITING);
    verify(messenger, never()).edit(CONTEXT, MESSAGE_ID, FAILED);
    verify(messenger, never()).send(any(), any());
    verifyNoMoreInteractions(nodeService);
  }

  @Test
  @DisplayName("2a AC-1: runNode throwing shows failed after starting, no waiting")
  void runNodeThrows() {
    allowRun();
    supportRegion();
    when(nodeNotifications.build(CONTEXT, MESSAGE_ID, REGION)).thenReturn(ENV);
    when(nodeService.runNode(REGION, "alex", null, ENV))
        .thenThrow(new IllegalStateException("boom"));

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(messenger, nodeService);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, STARTING);
    inOrder.verify(nodeService).runNode(REGION, "alex", null, ENV);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, FAILED);
    verify(messenger, never()).edit(CONTEXT, MESSAGE_ID, WAITING);
    verifyNoMoreInteractions(nodeService);
  }

  @Test
  @DisplayName("2a AC-2: launchInNewMessage sends starting and uses its id for the notifications and the following edits")
  void newMessageSuccess() {
    supportRegion();
    when(messenger.send(CONTEXT, STARTING)).thenReturn(NEW_MESSAGE_ID);
    when(nodeNotifications.build(CONTEXT, NEW_MESSAGE_ID, REGION)).thenReturn(NEW_MESSAGE_ENV);
    when(nodeService.runNode(REGION, "alex", HOST_NAME, NEW_MESSAGE_ENV)).thenReturn(TASK);

    controller.launchInNewMessage(CONTEXT, REGION, HOST_NAME);

    InOrder inOrder = inOrder(messenger, nodeService, nodeNotifications);
    inOrder.verify(messenger).send(CONTEXT, STARTING);
    inOrder.verify(nodeService).getSupportedRegionIds();
    inOrder.verify(nodeNotifications).build(CONTEXT, NEW_MESSAGE_ID, REGION);
    inOrder.verify(nodeService).runNode(REGION, "alex", HOST_NAME, NEW_MESSAGE_ENV);
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
    when(nodeService.runNode(REGION, "alex", null, NEW_MESSAGE_ENV)).thenReturn(TASK);

    controller.launchInNewMessage(CONTEXT, REGION, null);

    verify(nodeService).runNode(REGION, "alex", null, NEW_MESSAGE_ENV);
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
    when(nodeService.runNode(REGION, "alex", HOST_NAME, NEW_MESSAGE_ENV))
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

  private static Screen screen(String name) {
    return new Screen(name, List.of(), List.of());
  }

}
