package org.github.akarkin1.ui.controller;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.ecs.RunTaskStatus;
import org.github.akarkin1.ecs.TaskInfo;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.ui.UiContext;
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
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LaunchControllerTest {

  private static final String REGION = "eu-central-1";
  private static final UiContext CONTEXT = new UiContext(100L, "alex", "Alex", "en-US");
  private static final Integer MESSAGE_ID = 42;
  private static final TaskInfo TASK = TaskInfo.builder()
      .id("task-1")
      .cluster("cluster-1")
      .region(Region.EU_CENTRAL_1)
      .build();
  private static final TaskInfo FULL_INFO = TaskInfo.builder()
      .id("task-1")
      .cluster("cluster-1")
      .region(Region.EU_CENTRAL_1)
      .hostName("node-1")
      .publicIp("1.2.3.4")
      .state("HEALTHY")
      .build();

  private static final Screen NOT_ALLOWED = screen("notAllowed");
  private static final Screen REGION_UNAVAILABLE = screen("regionUnavailable");
  private static final Screen STARTING = screen("starting");
  private static final Screen WAITING = screen("waiting");
  private static final Screen READY = screen("ready");
  private static final Screen STILL_STARTING = screen("stillStarting");
  private static final Screen FAILED = screen("failed");

  @Mock
  private TailscaleNodeService nodeService;
  @Mock
  private Authorizer authorizer;
  @Mock
  private UiMessenger messenger;
  @Mock
  private LaunchScreens launchScreens;

  private LaunchController controller;

  @BeforeEach
  void setUp() {
    controller = new LaunchController(nodeService, authorizer, messenger, launchScreens);
    lenient().when(launchScreens.notAllowed()).thenReturn(NOT_ALLOWED);
    lenient().when(launchScreens.regionUnavailable()).thenReturn(REGION_UNAVAILABLE);
    lenient().when(launchScreens.starting(REGION)).thenReturn(STARTING);
    lenient().when(launchScreens.waiting(REGION)).thenReturn(WAITING);
    lenient().when(launchScreens.ready(FULL_INFO)).thenReturn(READY);
    lenient().when(launchScreens.stillStarting(REGION)).thenReturn(STILL_STARTING);
    lenient().when(launchScreens.failed(REGION)).thenReturn(FAILED);
  }

  @Test
  @DisplayName("AC-10: null username is not allowed and makes no service calls")
  void nullUsernameNotAllowed() {
    UiContext anonymous = new UiContext(100L, null, "Alex", "en-US");

    controller.launch(anonymous, MESSAGE_ID, REGION);

    verify(messenger).edit(anonymous, MESSAGE_ID, NOT_ALLOWED);
    verifyNoInteractions(nodeService);
  }

  @Test
  @DisplayName("AC-10: user without RUN_NODES is not allowed and nothing is started")
  void withoutRunNodesNotAllowed() {
    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    verify(messenger).edit(CONTEXT, MESSAGE_ID, NOT_ALLOWED);
    verify(nodeService, never()).runNode(any(), any(), any());
    verify(messenger, never()).edit(CONTEXT, MESSAGE_ID, STARTING);
  }

  @Test
  @DisplayName("AC-10: unsupported region shows starting, then regionUnavailable, and nothing is started")
  void regionUnavailable() {
    allowRun();
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of("us-east-1"));

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(messenger);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, STARTING);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, REGION_UNAVAILABLE);
    verify(nodeService, never()).runNode(any(), any(), any());
  }

  @Test
  @DisplayName("AC-10: starting is edited before the region check and before runNode")
  void startingBeforeRegionCheckAndRunNode() {
    allowRun();
    supportRegion();
    when(nodeService.runNode(REGION, "alex", null)).thenReturn(TASK);
    when(nodeService.checkNodeStatus(TASK)).thenReturn(RunTaskStatus.UNKNOWN);

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(messenger, nodeService);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, STARTING);
    inOrder.verify(nodeService).getSupportedRegionIds();
    inOrder.verify(nodeService).runNode(REGION, "alex", null);
  }

  @Test
  @DisplayName("AC-10: runNode throwing shows failed after starting, no waiting")
  void runNodeThrows() {
    allowRun();
    supportRegion();
    when(nodeService.runNode(REGION, "alex", null)).thenThrow(new IllegalStateException("boom"));

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(messenger, nodeService);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, STARTING);
    inOrder.verify(nodeService).runNode(REGION, "alex", null);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, FAILED);
    verify(messenger, never()).edit(CONTEXT, MESSAGE_ID, WAITING);
    verify(nodeService, never()).checkNodeStatus(any());
  }

  @Test
  @DisplayName("AC-10: HEALTHY with full info shows ready; starting before region check and runNode, waiting before checkNodeStatus")
  void healthyWithInfo() {
    allowRun();
    supportRegion();
    when(nodeService.runNode(REGION, "alex", null)).thenReturn(TASK);
    when(nodeService.checkNodeStatus(TASK)).thenReturn(RunTaskStatus.HEALTHY);
    when(nodeService.getFullTaskInfo(Region.EU_CENTRAL_1, "cluster-1", "task-1"))
        .thenReturn(Optional.of(FULL_INFO));

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(messenger, nodeService);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, STARTING);
    inOrder.verify(nodeService).getSupportedRegionIds();
    inOrder.verify(nodeService).runNode(REGION, "alex", null);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, WAITING);
    inOrder.verify(nodeService).checkNodeStatus(TASK);
    inOrder.verify(nodeService).getFullTaskInfo(Region.EU_CENTRAL_1, "cluster-1", "task-1");
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, READY);
  }

  @Test
  @DisplayName("AC-10: HEALTHY without full info shows stillStarting")
  void healthyWithoutInfo() {
    allowRun();
    supportRegion();
    when(nodeService.runNode(REGION, "alex", null)).thenReturn(TASK);
    when(nodeService.checkNodeStatus(TASK)).thenReturn(RunTaskStatus.HEALTHY);
    when(nodeService.getFullTaskInfo(Region.EU_CENTRAL_1, "cluster-1", "task-1"))
        .thenReturn(Optional.empty());

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(messenger);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, WAITING);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, STILL_STARTING);
    verify(launchScreens, never()).ready(any());
  }

  @Test
  @DisplayName("AC-10: getFullTaskInfo throwing shows stillStarting")
  void fullTaskInfoThrows() {
    allowRun();
    supportRegion();
    when(nodeService.runNode(REGION, "alex", null)).thenReturn(TASK);
    when(nodeService.checkNodeStatus(TASK)).thenReturn(RunTaskStatus.HEALTHY);
    when(nodeService.getFullTaskInfo(Region.EU_CENTRAL_1, "cluster-1", "task-1"))
        .thenThrow(new IllegalStateException("boom"));

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(messenger, nodeService);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, WAITING);
    inOrder.verify(nodeService).getFullTaskInfo(Region.EU_CENTRAL_1, "cluster-1", "task-1");
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, STILL_STARTING);
    verify(messenger, never()).edit(CONTEXT, MESSAGE_ID, FAILED);
  }

  @Test
  @DisplayName("AC-10: UNKNOWN status shows stillStarting")
  void unknownStatus() {
    allowRun();
    supportRegion();
    when(nodeService.runNode(REGION, "alex", null)).thenReturn(TASK);
    when(nodeService.checkNodeStatus(TASK)).thenReturn(RunTaskStatus.UNKNOWN);

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(messenger);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, WAITING);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, STILL_STARTING);
    verify(messenger, never()).edit(CONTEXT, MESSAGE_ID, FAILED);
  }

  @Test
  @DisplayName("AC-10: UNHEALTHY status shows failed")
  void unhealthyStatus() {
    allowRun();
    supportRegion();
    when(nodeService.runNode(REGION, "alex", null)).thenReturn(TASK);
    when(nodeService.checkNodeStatus(TASK)).thenReturn(RunTaskStatus.UNHEALTHY);

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(messenger);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, WAITING);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, FAILED);
    verify(messenger, never()).edit(CONTEXT, MESSAGE_ID, STILL_STARTING);
  }

  @Test
  @DisplayName("AC-10: checkNodeStatus throwing shows failed after waiting")
  void checkNodeStatusThrows() {
    allowRun();
    supportRegion();
    when(nodeService.runNode(REGION, "alex", null)).thenReturn(TASK);
    when(nodeService.checkNodeStatus(TASK)).thenThrow(new IllegalStateException("boom"));

    controller.launch(CONTEXT, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(messenger, nodeService);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, WAITING);
    inOrder.verify(nodeService).checkNodeStatus(TASK);
    inOrder.verify(messenger).edit(CONTEXT, MESSAGE_ID, FAILED);
    verify(nodeService, never()).getFullTaskInfo(any(), any(), any());
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
