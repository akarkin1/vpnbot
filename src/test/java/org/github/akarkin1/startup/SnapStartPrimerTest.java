package org.github.akarkin1.startup;

import org.github.akarkin1.auth.PermissionsService;
import org.github.akarkin1.deduplication.UpdateEventsRegistry;
import org.github.akarkin1.ec2.Ec2ClientPool;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapStartPrimerTest {

  @Mock
  private TailscaleNodeService nodeService;
  @Mock
  private Ec2ClientPool ec2ClientPool;
  @Mock
  private PermissionsService permissionsService;
  @Mock
  private UpdateEventsRegistry eventsRegistry;
  @Mock
  private AbsSender sender;

  private SnapStartPrimer primer;

  @BeforeEach
  void setUp() {
    primer = new SnapStartPrimer(nodeService, ec2ClientPool, permissionsService, eventsRegistry,
                                 sender);
  }

  @Test
  @DisplayName("AC-P1: prime() runs permissions, listTasks, register and getMe in this order, nothing else")
  void primesAllStepsInOrder() throws TelegramApiException {
    primer.prime();

    InOrder inOrder = inOrder(permissionsService, nodeService, eventsRegistry, sender);
    inOrder.verify(permissionsService).getUserPermissions();
    inOrder.verify(nodeService).listTasks(SnapStartPrimer.PRIMER_USER);
    inOrder.verify(nodeService).getSupportedRegionIds();
    ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
    inOrder.verify(eventsRegistry).register(update.capture());
    inOrder.verify(sender).execute(any(GetMe.class));
    verifyNoMoreInteractions(permissionsService, nodeService, eventsRegistry, sender);

    assertEquals(SnapStartPrimer.PRIMER_UPDATE_ID, update.getValue().getUpdateId());
  }

  @Test
  @DisplayName("AC-P1: an already registered primer update (register returns false) is fine")
  void registerReturningFalseIsFine() throws TelegramApiException {
    when(eventsRegistry.register(any(Update.class))).thenReturn(false);

    assertDoesNotThrow(primer::prime);

    verify(sender).execute(any(GetMe.class));
  }

  @Test
  @DisplayName("AC-P2: a failing permissions step does not stop the other steps")
  void permissionsFailureIsSwallowed() throws TelegramApiException {
    when(permissionsService.getUserPermissions()).thenThrow(new RuntimeException("dynamodb down"));

    assertDoesNotThrow(primer::prime);

    verifyAllStepsCalled();
  }

  @Test
  @DisplayName("AC-P2: a failing listTasks step does not stop the other steps")
  void listTasksFailureIsSwallowed() throws TelegramApiException {
    when(nodeService.listTasks(SnapStartPrimer.PRIMER_USER))
        .thenThrow(new RuntimeException("ecs down"));

    assertDoesNotThrow(primer::prime);

    verifyAllStepsCalled();
  }

  @Test
  @DisplayName("AC-P2: a failing register step does not stop the other steps")
  void registerFailureIsSwallowed() throws TelegramApiException {
    when(eventsRegistry.register(any(Update.class)))
        .thenThrow(new RuntimeException("conditional put failed"));

    assertDoesNotThrow(primer::prime);

    verifyAllStepsCalled();
  }

  @Test
  @DisplayName("AC-P2: a failing Telegram getMe step (TelegramApiException) is swallowed")
  void telegramFailureIsSwallowed() throws TelegramApiException {
    when(sender.execute(any(GetMe.class))).thenThrow(new TelegramApiException("telegram down"));

    assertDoesNotThrow(primer::prime);

    verifyAllStepsCalled();
  }

  @Test
  @DisplayName("AC-P2: a runtime exception from the Telegram step is swallowed too")
  void telegramRuntimeFailureIsSwallowed() throws TelegramApiException {
    when(sender.execute(any(GetMe.class))).thenThrow(new IllegalStateException("http client broken"));

    assertDoesNotThrow(primer::prime);

    verifyAllStepsCalled();
  }

  @Test
  @DisplayName("AC-P3: the primer update id is negative and the primer user is not blank")
  void constants() {
    assertTrue(SnapStartPrimer.PRIMER_UPDATE_ID < 0);
    assertFalse(SnapStartPrimer.PRIMER_USER.isBlank());
  }

  private void verifyAllStepsCalled() throws TelegramApiException {
    verify(permissionsService).getUserPermissions();
    verify(nodeService).listTasks(SnapStartPrimer.PRIMER_USER);
    verify(eventsRegistry).register(any(Update.class));
    verify(sender).execute(any(GetMe.class));
  }
}
