package org.github.akarkin1.ui.controller;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.ecs.EcsNode;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeControllerTest {

  private static final UiContext CONTEXT = new UiContext(100L, 7L, "alex", "Alex", null, "en-US");
  private static final TgUser ALEX = new TgUser(7L, "alex");
  private static final Integer MESSAGE_ID = 42;
  private static final Screen HOME = new Screen("home", List.of(), List.of());
  private static final Screen HELP = new Screen("help", List.of(), List.of());
  private static final List<EcsNode> NODES = List.of(EcsNode.builder().id("task-1").build());

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
    when(nodeService.listNodes("7")).thenReturn(NODES);
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of("eu-central-1"));
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.showHome(CONTEXT);

    assertEquals(new HomeModel("Alex", "alex", true, true, false, NODES, List.of("eu-central-1"),
                               List.of(), false, true, true),
                 capturedModel());
  }

  @Test
  @DisplayName("AC-9: root user lists all nodes with listNodes(null)")
  void rootListsAllNodes() {
    grant(Permission.LIST_NODES, Permission.ROOT_ACCESS);
    when(nodeService.listNodes(null)).thenReturn(NODES);
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.showHome(CONTEXT);

    verify(nodeService).listNodes(null);
    assertEquals(new HomeModel("Alex", "alex", true, false, true, NODES, List.of(), List.of(), true, true, true),
                 capturedModel());
  }

  @Test
  @DisplayName("4 §4.4: a user admin without node permissions manages users, has access, makes no node service calls")
  void userAdmin() {
    grant(Permission.USER_MANAGEMENT);
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.showHome(CONTEXT);

    verifyNoInteractions(nodeService);
    assertEquals(new HomeModel("Alex", "alex", false, false, false, List.of(), List.of(), List.of(),
                               true, true, false),
                 capturedModel());
  }

  @Test
  @DisplayName("4 §4.2: any permission means access; a read-only user does not manage users")
  void readOnlyHasAccessButNoUsers() {
    grant(Permission.LIST_NODES, Permission.SUPPORTED_REGIONS);
    when(nodeService.listNodes("7")).thenReturn(List.of());
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.showHome(CONTEXT);

    HomeModel model = capturedModel();
    assertTrue(model.hasAccess());
    assertFalse(model.canManageUsers());
  }

  @Test
  @DisplayName("4 AC-9: non-root user lists own nodes by the user id (listNodes(\"7\"))")
  void listsOwnNodesByUserId() {
    grant(Permission.LIST_NODES);
    when(nodeService.listNodes("7")).thenReturn(NODES);
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.showHome(CONTEXT);

    verify(nodeService).listNodes("7");
    verify(nodeService, never()).listNodes("alex");
  }

  @Test
  @DisplayName("4 AC-9 (was 2b AC-8 null username): a user without a username is authorized by the user id and lists own nodes by id")
  void userWithoutUsername() {
    UiContext anna = new UiContext(100L, 11L, null, "Anna", null, "en-US");
    TgUser annaUser = new TgUser(11L, null);
    EcsNode node = EcsNode.builder().id("task-1").runBy("11").build();
    lenient().when(authorizer.hasPermission(annaUser, Permission.LIST_NODES)).thenReturn(true);
    lenient().when(authorizer.hasPermission(annaUser, Permission.RUN_NODES)).thenReturn(true);
    when(nodeService.listNodes("11")).thenReturn(List.of(node));
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of("eu-central-1"));
    when(nodeAccess.canStop(annaUser, node)).thenReturn(true);
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.showHome(anna);

    assertEquals(new HomeModel("Anna", null, true, true, false, List.of(node), List.of("eu-central-1"),
                               List.of("task-1"), false, true, true),
                 capturedModel());
    verify(messenger).send(anna, HOME);
  }

  @Test
  @DisplayName("AC-9, 2b AC-8: a user without permissions (and without a username) makes no node service calls and has no stoppable nodes")
  void userWithoutUsernameAndPermissions() {
    UiContext anonymous = new UiContext(100L, 11L, null, "Alex", null, "en-US");
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.showHome(anonymous);

    verifyNoInteractions(nodeService, nodeAccess);
    assertEquals(new HomeModel("Alex", null, false, false, false, List.of(), List.of(), List.of(), false, false, false),
                 capturedModel());
    verify(messenger).send(anonymous, HOME);
  }

  @Test
  @DisplayName("AC-9: user without LIST_NODES and RUN_NODES makes no node service calls")
  void noPermissions() {
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.showHome(CONTEXT);

    verifyNoInteractions(nodeService);
    assertEquals(new HomeModel("Alex", "alex", false, false, false, List.of(), List.of(), List.of(), false, false, false),
                 capturedModel());
  }

  @Test
  @DisplayName("AC-9: read-only user lists nodes but does not load regions")
  void readOnlyUser() {
    grant(Permission.LIST_NODES);
    when(nodeService.listNodes("7")).thenReturn(NODES);
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.showHome(CONTEXT);

    verify(nodeService, never()).getSupportedRegionIds();
    assertEquals(new HomeModel("Alex", "alex", true, false, false, NODES, List.of(), List.of(), false, true, false),
                 capturedModel());
  }

  @Test
  @DisplayName("AC-9: user with RUN_NODES only loads regions but does not list nodes")
  void runOnlyUser() {
    grant(Permission.RUN_NODES);
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of("eu-central-1"));
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.showHome(CONTEXT);

    verify(nodeService, never()).listNodes(any());
    assertEquals(new HomeModel("Alex", "alex", false, true, false, List.of(), List.of("eu-central-1"),
                               List.of(), false, true, true),
                 capturedModel());
  }

  @Test
  @DisplayName("2b AC-8: stoppableTaskIds holds the ids of the listed nodes the user may stop, in list order")
  void stoppableTaskIds() {
    EcsNode first = EcsNode.builder().id("task-1").runBy("7").build();
    EcsNode second = EcsNode.builder().id("task-2").runBy("8").build();
    EcsNode third = EcsNode.builder().id("task-3").runBy("7").build();
    List<EcsNode> nodes = List.of(first, second, third);
    grant(Permission.LIST_NODES, Permission.RUN_NODES);
    when(nodeService.listNodes("7")).thenReturn(nodes);
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of("eu-central-1"));
    lenient().when(nodeAccess.canStop(ALEX, first)).thenReturn(true);
    lenient().when(nodeAccess.canStop(ALEX, second)).thenReturn(false);
    lenient().when(nodeAccess.canStop(ALEX, third)).thenReturn(true);
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.showHome(CONTEXT);

    assertEquals(new HomeModel("Alex", "alex", true, true, false, nodes, List.of("eu-central-1"),
                               List.of("task-1", "task-3"), false, true, true),
                 capturedModel());
  }

  @Test
  @DisplayName("2b AC-8: refreshHome computes the stoppable nodes as well")
  void refreshHomeStoppableTaskIds() {
    EcsNode node = EcsNode.builder().id("task-9").runBy("8").build();
    grant(Permission.LIST_NODES, Permission.RUN_NODES, Permission.ROOT_ACCESS);
    when(nodeService.listNodes(null)).thenReturn(List.of(node));
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of());
    when(nodeAccess.canStop(ALEX, node)).thenReturn(true);
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.refreshHome(CONTEXT, MESSAGE_ID);

    assertEquals(List.of("task-9"), capturedModel().stoppableTaskIds());
  }

  @Test
  @DisplayName("2b AC-8: no stoppable nodes when the user may stop none of them")
  void noStoppableNodes() {
    grant(Permission.LIST_NODES);
    when(nodeService.listNodes("7")).thenReturn(NODES);
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
    when(nodeService.listNodes("7")).thenReturn(NODES);
    when(homeScreen.home(any())).thenReturn(HOME);

    controller.refreshHome(CONTEXT, MESSAGE_ID);

    assertEquals(new HomeModel("Alex", "alex", true, false, false, NODES, List.of(), List.of(), false, true, false),
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
      lenient().when(authorizer.hasPermission(ALEX, permission)).thenReturn(true);
    }
  }

  private HomeModel capturedModel() {
    ArgumentCaptor<HomeModel> captor = ArgumentCaptor.forClass(HomeModel.class);
    verify(homeScreen).home(captor.capture());
    return captor.getValue();
  }

}
