package org.github.akarkin1.ui.controller;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.ecs.TaskInfo;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.HelpScreen;
import org.github.akarkin1.ui.screen.HomeModel;
import org.github.akarkin1.ui.screen.HomeScreen;
import org.github.akarkin1.ui.screen.Screen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeControllerTest {

  private static final UiContext CONTEXT = new UiContext(100L, "alex", "Alex", "en-US");
  private static final Integer MESSAGE_ID = 42;
  private static final Screen HOME = new Screen("home", List.of(), List.of());
  private static final Screen HELP = new Screen("help", List.of(), List.of());
  private static final List<TaskInfo> NODES = List.of(TaskInfo.builder().id("task-1").build());

  @Mock
  private TailscaleNodeService nodeService;
  @Mock
  private Authorizer authorizer;
  @Mock
  private NodeAccess nodeAccess;
  @Mock
  private UiMessenger messenger;
  @Mock
  private HomeScreen homeScreen;
  @Mock
  private HelpScreen helpScreen;

  private HomeController controller;

  @BeforeEach
  void setUp() {
    controller = new HomeController(nodeService, authorizer, nodeAccess, messenger, homeScreen,
                                    helpScreen);
  }

  @Test
  @DisplayName("AC-9: non-root user lists own nodes and supported regions")
  void nonRootListsOwnNodes() {
    grant(Permission.LIST_NODES, Permission.RUN_NODES);
    when(nodeService.listTasks("alex")).thenReturn(NODES);
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of("eu-central-1"));
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.showHome(CONTEXT);

    assertEquals(new HomeModel("Alex", "alex", true, true, false, NODES, List.of("eu-central-1"),
                               List.of()),
                 capturedModel());
  }

  @Test
  @DisplayName("AC-9: root user lists all nodes with listTasks(null)")
  void rootListsAllNodes() {
    grant(Permission.LIST_NODES, Permission.ROOT_ACCESS);
    when(nodeService.listTasks(null)).thenReturn(NODES);
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.showHome(CONTEXT);

    verify(nodeService).listTasks(null);
    assertEquals(new HomeModel("Alex", "alex", true, false, true, NODES, List.of(), List.of()),
                 capturedModel());
  }

  @Test
  @DisplayName("AC-9, 2b AC-8: null username makes no service calls, has no permissions and no stoppable nodes")
  void nullUsername() {
    UiContext anonymous = new UiContext(100L, null, "Alex", "en-US");
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.showHome(anonymous);

    verifyNoInteractions(nodeService, authorizer, nodeAccess);
    assertEquals(new HomeModel("Alex", null, false, false, false, List.of(), List.of(), List.of()),
                 capturedModel());
    verify(messenger).send(anonymous, HOME);
  }

  @Test
  @DisplayName("AC-9: user without LIST_NODES and RUN_NODES makes no node service calls")
  void noPermissions() {
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.showHome(CONTEXT);

    verifyNoInteractions(nodeService);
    assertEquals(new HomeModel("Alex", "alex", false, false, false, List.of(), List.of(), List.of()),
                 capturedModel());
  }

  @Test
  @DisplayName("AC-9: read-only user lists nodes but does not load regions")
  void readOnlyUser() {
    grant(Permission.LIST_NODES);
    when(nodeService.listTasks("alex")).thenReturn(NODES);
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.showHome(CONTEXT);

    verify(nodeService, never()).getSupportedRegionIds();
    assertEquals(new HomeModel("Alex", "alex", true, false, false, NODES, List.of(), List.of()),
                 capturedModel());
  }

  @Test
  @DisplayName("AC-9: user with RUN_NODES only loads regions but does not list nodes")
  void runOnlyUser() {
    grant(Permission.RUN_NODES);
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of("eu-central-1"));
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.showHome(CONTEXT);

    verify(nodeService, never()).listTasks(any());
    assertEquals(new HomeModel("Alex", "alex", false, true, false, List.of(), List.of("eu-central-1"),
                               List.of()),
                 capturedModel());
  }

  @Test
  @DisplayName("2b AC-8: stoppableTaskIds holds the ids of the listed nodes the user may stop, in list order")
  void stoppableTaskIds() {
    TaskInfo first = TaskInfo.builder().id("task-1").runBy("alex").build();
    TaskInfo second = TaskInfo.builder().id("task-2").runBy("bob").build();
    TaskInfo third = TaskInfo.builder().id("task-3").runBy("alex").build();
    List<TaskInfo> nodes = List.of(first, second, third);
    grant(Permission.LIST_NODES, Permission.RUN_NODES);
    when(nodeService.listTasks("alex")).thenReturn(nodes);
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of("eu-central-1"));
    lenient().when(nodeAccess.canStop("alex", first)).thenReturn(true);
    lenient().when(nodeAccess.canStop("alex", second)).thenReturn(false);
    lenient().when(nodeAccess.canStop("alex", third)).thenReturn(true);
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.showHome(CONTEXT);

    assertEquals(new HomeModel("Alex", "alex", true, true, false, nodes, List.of("eu-central-1"),
                               List.of("task-1", "task-3")),
                 capturedModel());
  }

  @Test
  @DisplayName("2b AC-8: refreshHome computes the stoppable nodes as well")
  void refreshHomeStoppableTaskIds() {
    TaskInfo node = TaskInfo.builder().id("task-9").runBy("bob").build();
    grant(Permission.LIST_NODES, Permission.RUN_NODES, Permission.ROOT_ACCESS);
    when(nodeService.listTasks(null)).thenReturn(List.of(node));
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of());
    when(nodeAccess.canStop("alex", node)).thenReturn(true);
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.refreshHome(CONTEXT, MESSAGE_ID);

    assertEquals(List.of("task-9"), capturedModel().stoppableTaskIds());
  }

  @Test
  @DisplayName("2b AC-8: no stoppable nodes when the user may stop none of them")
  void noStoppableNodes() {
    grant(Permission.LIST_NODES);
    when(nodeService.listTasks("alex")).thenReturn(NODES);
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.showHome(CONTEXT);

    assertEquals(List.of(), capturedModel().stoppableTaskIds());
  }

  @Test
  @DisplayName("AC-9: showHome sends a new message")
  void showHomeSends() {
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.showHome(CONTEXT);

    verify(messenger).send(CONTEXT, HOME);
    verify(messenger, never()).edit(any(), any(), any());
  }

  @Test
  @DisplayName("AC-9: refreshHome edits the message")
  void refreshHomeEdits() {
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.refreshHome(CONTEXT, MESSAGE_ID);

    verify(messenger).edit(CONTEXT, MESSAGE_ID, HOME);
    verify(messenger, never()).send(any(), any());
  }

  @Test
  @DisplayName("AC-9: refreshHome builds the same model as showHome")
  void refreshHomeBuildsModel() {
    grant(Permission.LIST_NODES);
    when(nodeService.listTasks("alex")).thenReturn(NODES);
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.refreshHome(CONTEXT, MESSAGE_ID);

    assertEquals(new HomeModel("Alex", "alex", true, false, false, NODES, List.of(), List.of()),
                 capturedModel());
  }

  @Test
  @DisplayName("AC-9: showHelp edits the message with the help screen")
  void showHelpEdits() {
    when(helpScreen.help()).thenReturn(HELP);

    controller.showHelp(CONTEXT, MESSAGE_ID);

    verify(messenger).edit(CONTEXT, MESSAGE_ID, HELP);
    verify(messenger, never()).send(any(), any());
  }

  private void grant(Permission... permissions) {
    for (Permission permission : permissions) {
      lenient().when(authorizer.hasPermission("alex", permission)).thenReturn(true);
    }
  }

  private HomeModel capturedModel() {
    ArgumentCaptor<HomeModel> captor = ArgumentCaptor.forClass(HomeModel.class);
    verify(homeScreen).home(captor.capture());
    return captor.getValue();
  }

}
