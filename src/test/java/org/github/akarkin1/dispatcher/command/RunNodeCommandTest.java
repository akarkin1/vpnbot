package org.github.akarkin1.dispatcher.command;

import org.github.akarkin1.dispatcher.response.EmptyResponse;
import org.github.akarkin1.message.MessageConsumer;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.tg.TgRequestContext;
import org.github.akarkin1.ui.NodeLauncher;
import org.github.akarkin1.ui.UiContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

import static org.github.akarkin1.ui.TelegramUpdates.CHAT_ID;
import static org.github.akarkin1.ui.TelegramUpdates.messageUpdate;
import static org.github.akarkin1.ui.TelegramUpdates.user;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunNodeCommandTest {

  private static final UiContext EXPECTED_CONTEXT = new UiContext(CHAT_ID, "alex", null, "ru");

  @Mock
  private TailscaleNodeService nodeService;
  @Mock
  private MessageConsumer messageConsumer;
  @Mock
  private NodeLauncher nodeLauncher;

  private RunNodeCommand command;

  @BeforeEach
  void setUp() {
    TgRequestContext.initContext(messageUpdate(user("alex", "Alex", "ru"), "/runNodeIn Frankfurt"));
    command = new RunNodeCommand(nodeService, messageConsumer, nodeLauncher);
  }

  @AfterEach
  void resetContext() {
    TgRequestContext.initContext(new Update());
  }

  @Test
  @DisplayName("2a AC-3: a missing region is answered with the usage message, nothing is launched")
  void missingRegion() {
    assertSame(EmptyResponse.NONE, command.run(List.of()));

    verify(messageConsumer).accept(
        "${command.run-node.missing-arg.region.error}. ${common.command.description.message}: "
        + "${command.run-node.description.message}");
    verifyNoInteractions(nodeLauncher);
  }

  @Test
  @DisplayName("2a AC-3: an invalid region is answered with the invalid-name error, nothing is launched")
  void invalidRegion() {
    when(nodeService.isRegionValid("Atlantis")).thenReturn(false);

    assertSame(EmptyResponse.NONE, command.run(List.of("Atlantis")));

    verify(messageConsumer).accept("${common.region.invalid-name.error}", "Atlantis");
    verifyNoInteractions(nodeLauncher);
  }

  @Test
  @DisplayName("2a AC-3: an unsupported region is answered with the not-supported error, nothing is launched")
  void unsupportedRegion() {
    when(nodeService.isRegionValid("Tokyo")).thenReturn(true);
    when(nodeService.isRegionSupported("Tokyo")).thenReturn(false);

    assertSame(EmptyResponse.NONE, command.run(List.of("Tokyo")));

    verify(messageConsumer).accept("${common.region.not-supported.error}", "Tokyo");
    verifyNoInteractions(nodeLauncher);
  }

  @Test
  @DisplayName("2a AC-3: a hostname in use is answered with the name-in-use error, nothing is launched")
  void hostnameInUse() {
    when(nodeService.isRegionValid("Frankfurt")).thenReturn(true);
    when(nodeService.isRegionSupported("Frankfurt")).thenReturn(true);
    when(nodeService.isHostnameAvailable("Frankfurt", "node-1")).thenReturn(false);

    assertSame(EmptyResponse.NONE, command.run(List.of("Frankfurt", "node-1")));

    verify(messageConsumer).accept("${command.run-node.node.name-is-incorrect-or-in-use.error}",
                                   "node-1");
    verifyNoInteractions(nodeLauncher);
  }

  @Test
  @DisplayName("2a AC-3: a valid command launches in a new message with the region id, the hostname and the TgRequestContext")
  void validWithHostname() {
    when(nodeService.isRegionValid("Frankfurt")).thenReturn(true);
    when(nodeService.isRegionSupported("Frankfurt")).thenReturn(true);
    when(nodeService.isHostnameAvailable("Frankfurt", "node-1")).thenReturn(true);
    when(nodeService.toRegionId("Frankfurt")).thenReturn("eu-central-1");

    assertSame(EmptyResponse.NONE, command.run(List.of("Frankfurt", "node-1")));

    verify(nodeLauncher).launchInNewMessage(EXPECTED_CONTEXT, "eu-central-1", "node-1");
    verify(nodeService, never()).runNode(any(), any(), any(), any());
    verifyNoInteractions(messageConsumer);
  }

  @Test
  @DisplayName("2a AC-3: a valid command without a hostname launches with a null hostname")
  void validWithoutHostname() {
    when(nodeService.isRegionValid("eu-central-1")).thenReturn(true);
    when(nodeService.isRegionSupported("eu-central-1")).thenReturn(true);
    when(nodeService.toRegionId("eu-central-1")).thenReturn("eu-central-1");

    assertSame(EmptyResponse.NONE, command.run(List.of("eu-central-1")));

    verify(nodeLauncher).launchInNewMessage(EXPECTED_CONTEXT, "eu-central-1", null);
    verify(nodeService, never()).runNode(any(), any(), any(), any());
    verifyNoInteractions(messageConsumer);
  }

}
