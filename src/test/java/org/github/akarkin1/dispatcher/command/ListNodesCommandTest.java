package org.github.akarkin1.dispatcher.command;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.dispatcher.response.TextCommandResponse;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.tg.TgRequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

import static org.github.akarkin1.ui.TelegramUpdates.USER_ID;
import static org.github.akarkin1.ui.TelegramUpdates.messageUpdate;
import static org.github.akarkin1.ui.TelegramUpdates.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListNodesCommandTest {

  private static final TgUser ALEX = new TgUser(USER_ID, "alex");

  @Mock
  private TailscaleNodeService nodeService;
  @Mock
  private Authorizer authorizer;

  private ListNodesCommand command;

  @BeforeEach
  void setUp() {
    TgRequestContext.initContext(messageUpdate(user("alex", "Alex", "en"), "/listRunningNodes"));
    command = new ListNodesCommand(nodeService, authorizer);
  }

  @AfterEach
  void resetContext() {
    TgRequestContext.initContext(new Update());
  }

  @Test
  @DisplayName("4 §4.1: a non-root user's nodes are listed by the user id")
  void nonRootListsByUserId() {
    when(nodeService.listTasks(String.valueOf(USER_ID))).thenReturn(List.of());

    TextCommandResponse response = command.run(List.of());

    verify(authorizer).hasPermission(ALEX, Permission.ROOT_ACCESS);
    verify(nodeService).listTasks(String.valueOf(USER_ID));
    verify(nodeService, never()).listTasks("alex");
    assertEquals("${command.list-nodes.no-nodes-run.message}", response.text());
  }

  @Test
  @DisplayName("4 §4.1: root (checked with the TgUser of the request) lists every node")
  void rootListsAll() {
    when(authorizer.hasPermission(ALEX, Permission.ROOT_ACCESS)).thenReturn(true);
    when(nodeService.listTasks(null)).thenReturn(List.of());

    command.run(List.of());

    verify(nodeService).listTasks(null);
  }

}
