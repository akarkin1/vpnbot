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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapStartPrimerTest {

  private static final String EU = "eu-central-1";
  private static final String US = "us-east-1";

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
  @DisplayName("AC-P5: with two regions prime() runs permissions, listTasks, getNode + EC2 prime per region (sorted), register and getMe in this order, nothing else")
  void primesAllStepsInOrderWithRegions() throws TelegramApiException {
    // Reverse iteration order: the primer has to sort the regions itself.
    when(nodeService.getSupportedRegionIds()).thenReturn(new LinkedHashSet<>(List.of(US, EU)));

    primer.prime();

    InOrder inOrder = inOrder(permissionsService, nodeService, ec2ClientPool, eventsRegistry,
                              sender);
    inOrder.verify(permissionsService).getUserPermissions();
    inOrder.verify(nodeService).listTasks(SnapStartPrimer.PRIMER_USER);
    inOrder.verify(nodeService).getSupportedRegionIds();
    inOrder.verify(nodeService).getNode(EU, SnapStartPrimer.PRIMER_TASK_ID);
    inOrder.verify(ec2ClientPool).prime(EU);
    inOrder.verify(nodeService).getNode(US, SnapStartPrimer.PRIMER_TASK_ID);
    inOrder.verify(ec2ClientPool).prime(US);
    ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
    inOrder.verify(eventsRegistry).register(update.capture());
    inOrder.verify(sender).execute(any(GetMe.class));
    verifyNoMoreInteractions(permissionsService, nodeService, ec2ClientPool, eventsRegistry,
                             sender);

    assertEquals(SnapStartPrimer.PRIMER_UPDATE_ID, update.getValue().getUpdateId());
  }

  @Test
  @DisplayName("AC-P1/AC-P5: with no supported regions the per-region steps are skipped, nothing else is called")
  void primesAllStepsInOrderWithoutRegions() throws TelegramApiException {
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of());

    primer.prime();

    InOrder inOrder = inOrder(permissionsService, nodeService, eventsRegistry, sender);
    inOrder.verify(permissionsService).getUserPermissions();
    inOrder.verify(nodeService).listTasks(SnapStartPrimer.PRIMER_USER);
    inOrder.verify(nodeService).getSupportedRegionIds();
    ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
    inOrder.verify(eventsRegistry).register(update.capture());
    inOrder.verify(sender).execute(any(GetMe.class));
    verifyNoMoreInteractions(permissionsService, nodeService, eventsRegistry, sender);
    verifyNoInteractions(ec2ClientPool);

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
    givenTwoRegions();
    when(permissionsService.getUserPermissions()).thenThrow(new RuntimeException("dynamodb down"));

    assertDoesNotThrow(primer::prime);

    verifyAllStepsCalled();
  }

  @Test
  @DisplayName("AC-P2: a failing listTasks step does not stop the other steps")
  void listTasksFailureIsSwallowed() throws TelegramApiException {
    givenTwoRegions();
    when(nodeService.listTasks(SnapStartPrimer.PRIMER_USER))
        .thenThrow(new RuntimeException("ecs down"));

    assertDoesNotThrow(primer::prime);

    verifyAllStepsCalled();
  }

  @Test
  @DisplayName("AC-P2: a failing register step does not stop the other steps")
  void registerFailureIsSwallowed() throws TelegramApiException {
    givenTwoRegions();
    when(eventsRegistry.register(any(Update.class)))
        .thenThrow(new RuntimeException("conditional put failed"));

    assertDoesNotThrow(primer::prime);

    verifyAllStepsCalled();
  }

  @Test
  @DisplayName("AC-P2: a failing Telegram getMe step (TelegramApiException) is swallowed")
  void telegramFailureIsSwallowed() throws TelegramApiException {
    givenTwoRegions();
    when(sender.execute(any(GetMe.class))).thenThrow(new TelegramApiException("telegram down"));

    assertDoesNotThrow(primer::prime);

    verifyAllStepsCalled();
  }

  @Test
  @DisplayName("AC-P2: a runtime exception from the Telegram step is swallowed too")
  void telegramRuntimeFailureIsSwallowed() throws TelegramApiException {
    givenTwoRegions();
    when(sender.execute(any(GetMe.class))).thenThrow(new IllegalStateException("http client broken"));

    assertDoesNotThrow(primer::prime);

    verifyAllStepsCalled();
  }

  @Test
  @DisplayName("AC-P5: a failing getNode for one region does not stop that region's EC2 step, the other region or the later steps")
  void getNodeFailureForOneRegionIsSwallowed() throws TelegramApiException {
    givenTwoRegions();
    when(nodeService.getNode(EU, SnapStartPrimer.PRIMER_TASK_ID))
        .thenThrow(new RuntimeException("ecs describe failed"));

    assertDoesNotThrow(primer::prime);

    verifyAllStepsCalled();
  }

  @Test
  @DisplayName("AC-P5: a failing EC2 prime for one region does not stop the other region or the later steps")
  void ec2PrimeFailureForOneRegionIsSwallowed() throws TelegramApiException {
    givenTwoRegions();
    doThrow(new RuntimeException("ec2 describe failed")).when(ec2ClientPool).prime(EU);

    assertDoesNotThrow(primer::prime);

    verifyAllStepsCalled();
  }

  @Test
  @DisplayName("AC-P5: a failing getSupportedRegionIds skips only the per-region steps")
  void supportedRegionsFailureSkipsOnlyRegionSteps() throws TelegramApiException {
    when(nodeService.getSupportedRegionIds()).thenThrow(new RuntimeException("dynamodb down"));

    assertDoesNotThrow(primer::prime);

    verify(permissionsService).getUserPermissions();
    verify(nodeService).listTasks(SnapStartPrimer.PRIMER_USER);
    verify(nodeService, never()).getNode(anyString(), anyString());
    verifyNoInteractions(ec2ClientPool);
    verify(eventsRegistry).register(any(Update.class));
    verify(sender).execute(any(GetMe.class));
  }

  @Test
  @DisplayName("AC-P3: the primer update id is negative, the primer user is not blank, the primer task id is 32 zeros")
  void constants() {
    assertTrue(SnapStartPrimer.PRIMER_UPDATE_ID < 0);
    assertFalse(SnapStartPrimer.PRIMER_USER.isBlank());
    assertEquals("00000000000000000000000000000000", SnapStartPrimer.PRIMER_TASK_ID);
  }

  private void givenTwoRegions() {
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of(EU, US));
  }

  private void verifyAllStepsCalled() throws TelegramApiException {
    verify(permissionsService).getUserPermissions();
    verify(nodeService).listTasks(SnapStartPrimer.PRIMER_USER);
    verify(nodeService).getSupportedRegionIds();
    verify(nodeService).getNode(EU, SnapStartPrimer.PRIMER_TASK_ID);
    verify(nodeService).getNode(US, SnapStartPrimer.PRIMER_TASK_ID);
    verify(ec2ClientPool).prime(EU);
    verify(ec2ClientPool).prime(US);
    verify(eventsRegistry).register(any(Update.class));
    verify(sender).execute(any(GetMe.class));
  }
}
