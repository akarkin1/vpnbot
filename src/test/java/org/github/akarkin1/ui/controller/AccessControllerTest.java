package org.github.akarkin1.ui.controller;

import org.github.akarkin1.auth.AccessRequest;
import org.github.akarkin1.auth.AccessRequestService;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.FakePermissionsService;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.auth.UserRole;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.AccessScreens;
import org.github.akarkin1.ui.screen.Button;
import org.github.akarkin1.ui.screen.Screen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * §4.2, §4.3. Users: root (ROOT_ACCESS), admin (USER_MANAGEMENT), reader (read-only), a pending
 * admin known by username only, and the requester "newbie" without any permission. Permissions
 * live in a {@link FakePermissionsService}; the mocked {@link Authorizer} answers from the same
 * table (root implies every permission, as {@code WhiteListAuthorizer} does).
 */
@ExtendWith(MockitoExtension.class)
class AccessControllerTest {

  private static final Integer MESSAGE_ID = 42;
  private static final Instant NOW = Instant.parse("2026-10-11T12:00:00.123Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final long REQUESTER_ID = 7L;

  private static final UiContext ROOT = new UiContext(1L, 1L, "root", "Root", "en");
  private static final UiContext ADMIN = new UiContext(10L, 10L, "admin", "Admin", "en");
  private static final UiContext READER = new UiContext(20L, 20L, "reader", "Reader", "en");
  private static final UiContext REQUESTER = new UiContext(REQUESTER_ID, REQUESTER_ID, "newbie", "Newbie", "ru");
  private static final TgUser PENDING_ADMIN = new TgUser(null, "padmin");

  private static final AccessRequest REQUEST =
      new AccessRequest(REQUESTER_ID, "newbie", "Newbie", "ru", 1_760_097_600_123L);

  private static final Screen REQUESTED = screen("requested");
  private static final Screen ALREADY_REQUESTED = screen("alreadyRequested");
  private static final Screen ADMIN_REQUEST = screen("adminRequest");
  private static final Screen GRANTED = screen("granted");
  private static final Screen DECLINED = screen("declined");
  private static final Screen ALREADY_HANDLED = screen("alreadyHandled");
  private static final Screen ACCESS_GRANTED = screen("accessGranted");
  private static final Screen ACCESS_DECLINED = screen("accessDeclined");
  private static final Screen NOT_ALLOWED = screen("notAllowed");

  @Mock
  private AccessRequestService requests;
  @Mock
  private Authorizer authorizer;
  @Mock
  private UiMessenger messenger;
  @Mock
  private AccessScreens screens;
  @Mock
  private HomeController homeController;

  private FakePermissionsService permissions;
  private AccessController controller;

  @BeforeEach
  void setUp() {
    permissions = new FakePermissionsService()
        .withUser(1L, "root", Permission.ROOT_ACCESS)
        .withUser(10L, "admin", Permission.USER_MANAGEMENT)
        .withUser(20L, "reader", Permission.LIST_NODES, Permission.SUPPORTED_REGIONS)
        .withPending(PENDING_ADMIN.username(), Permission.USER_MANAGEMENT);
    lenient().when(authorizer.hasPermission(any(), any())).thenAnswer(invocation -> {
      TgUser user = invocation.getArgument(0);
      Set<Permission> granted = user.id() != null
          ? permissions.permissionsOf(user.id())
          : permissions.pendingPermissionsOf(user.username());
      return granted.contains(Permission.ROOT_ACCESS) || granted.contains(invocation.getArgument(1));
    });
    lenient().when(screens.requested()).thenReturn(REQUESTED);
    lenient().when(screens.alreadyRequested()).thenReturn(ALREADY_REQUESTED);
    lenient().when(screens.adminRequest(any(), any())).thenReturn(ADMIN_REQUEST);
    lenient().when(screens.granted(any(), any())).thenReturn(GRANTED);
    lenient().when(screens.declined(any())).thenReturn(DECLINED);
    lenient().when(screens.alreadyHandled()).thenReturn(ALREADY_HANDLED);
    lenient().when(screens.accessGranted(any())).thenReturn(ACCESS_GRANTED);
    lenient().when(screens.accessDeclined()).thenReturn(ACCESS_DECLINED);
    lenient().when(screens.notAllowed()).thenReturn(NOT_ALLOWED);
    controller = new AccessController(requests, permissions, authorizer, homeController, messenger,
                                      screens, CLOCK);
  }

  // --- request ---------------------------------------------------------------------------------

  @Test
  @DisplayName("4 AC-4: a user granted meanwhile gets the home screen; nothing is recorded or sent")
  void requestWhenGrantedMeanwhile() {
    permissions.withUser(REQUESTER_ID, "newbie", Permission.LIST_NODES, Permission.SUPPORTED_REGIONS);

    controller.request(REQUESTER, MESSAGE_ID);

    verify(homeController).refreshHome(REQUESTER, MESSAGE_ID);
    verify(requests, never()).create(any());
    verify(messenger, never()).send(any(), any());
  }

  @Test
  @DisplayName("4 AC-4: a pending request shows alreadyRequested; nothing is recorded or sent")
  void requestAlreadyPending() {
    when(requests.find(REQUESTER_ID)).thenReturn(Optional.of(REQUEST));

    controller.request(REQUESTER, MESSAGE_ID);

    verify(messenger).edit(REQUESTER, MESSAGE_ID, ALREADY_REQUESTED);
    verify(requests, never()).create(any());
    verify(messenger, never()).send(any(), any());
    verifyNoInteractions(homeController);
  }

  @Test
  @DisplayName("4 AC-4: a new request is recorded with the requester's details, the requester sees requested")
  void requestRecorded() {
    when(requests.find(REQUESTER_ID)).thenReturn(Optional.empty());

    controller.request(REQUESTER, MESSAGE_ID);

    AccessRequest created = createdRequest();
    assertEquals(REQUESTER_ID, created.userId());
    assertEquals("newbie", created.username());
    assertEquals("Newbie", created.firstName());
    assertEquals("ru", created.languageCode());
    assertEquals(NOW.toEpochMilli(), created.requestedAt());
    verify(messenger).edit(REQUESTER, MESSAGE_ID, REQUESTED);
    verifyNoInteractions(homeController);
  }

  @Test
  @DisplayName("4 AC-4, D-4: every admin with an id (root, USER_MANAGEMENT) gets adminRequest at chat id = user id; non-admins and pending admins do not")
  void requestNotifiesAdmins() {
    when(requests.find(REQUESTER_ID)).thenReturn(Optional.empty());

    controller.request(REQUESTER, MESSAGE_ID);

    assertEquals(Set.of(1L, 10L), sentChatIds(ADMIN_REQUEST, 2));
    assertEquals(Set.of(), sentLanguages(ADMIN_REQUEST).stream().filter(Objects::nonNull)
                     .collect(Collectors.toSet()),
                 "D-21: admins are notified in the bot's default language (no language code)");
    verify(screens, atLeastOnce()).adminRequest(argThat(request -> request.userId() == REQUESTER_ID),
                                                eq(Set.of()));
    verify(screens, never()).adminRequest(any(), argThat(selected -> !selected.isEmpty()));
  }

  @Test
  @DisplayName("4 §4.2: with no admin known the request is still recorded and the requester sees requested")
  void requestWithoutAdmins() {
    FakePermissionsService noAdmins = new FakePermissionsService()
        .withUser(20L, "reader", Permission.LIST_NODES, Permission.SUPPORTED_REGIONS);
    AccessController withoutAdmins = new AccessController(requests, noAdmins, authorizer,
                                                          homeController, messenger, screens, CLOCK);
    when(requests.find(REQUESTER_ID)).thenReturn(Optional.empty());

    withoutAdmins.request(REQUESTER, MESSAGE_ID);

    verify(requests).create(any());
    verify(messenger).edit(REQUESTER, MESSAGE_ID, REQUESTED);
    verify(messenger, never()).send(any(), any());
  }

  @Test
  @DisplayName("4 D-4: an admin that cannot be reached is logged; the others are still notified")
  void requestAdminNotificationFailure() {
    when(requests.find(REQUESTER_ID)).thenReturn(Optional.empty());
    lenient().doThrow(new IllegalStateException("chat not found"))
        .when(messenger).send(argThat(context -> context != null && context.chatId() == 1L), any());

    assertDoesNotThrow(() -> controller.request(REQUESTER, MESSAGE_ID));

    verify(requests).create(any());
    verify(messenger).edit(REQUESTER, MESSAGE_ID, REQUESTED);
    verify(messenger).send(argThat(context -> context != null && context.chatId() == 10L), eq(ADMIN_REQUEST));
  }

  // --- toggle ----------------------------------------------------------------------------------

  @Test
  @DisplayName("4 AC-5: a role tap redraws the message with the role ticked; nothing is written")
  void toggleOn() {
    when(requests.find(REQUESTER_ID)).thenReturn(Optional.of(REQUEST));

    controller.toggle(ADMIN, MESSAGE_ID, REQUESTER_ID, 1, UserRole.READ_ONLY);

    verify(screens).adminRequest(REQUEST, Set.of(UserRole.NODE_ADMIN, UserRole.READ_ONLY));
    verify(messenger).edit(ADMIN, MESSAGE_ID, ADMIN_REQUEST);
    assertNothingWritten();
  }

  @Test
  @DisplayName("4 AC-5: a tap on a ticked role unticks it; nothing is written")
  void toggleOff() {
    when(requests.find(REQUESTER_ID)).thenReturn(Optional.of(REQUEST));

    controller.toggle(ROOT, MESSAGE_ID, REQUESTER_ID, 3, UserRole.NODE_ADMIN);

    verify(screens).adminRequest(REQUEST, Set.of(UserRole.READ_ONLY));
    verify(messenger).edit(ROOT, MESSAGE_ID, ADMIN_REQUEST);
    assertNothingWritten();
  }

  @Test
  @DisplayName("4 AC-5: the redrawn message carries the new mask on every button (real screens)")
  void toggleRedrawsWithNewMask() {
    AccessController withRealScreens = new AccessController(requests, permissions, authorizer,
                                                            homeController, messenger,
                                                            new AccessScreens(), CLOCK);
    when(requests.find(REQUESTER_ID)).thenReturn(Optional.of(REQUEST));

    withRealScreens.toggle(ADMIN, MESSAGE_ID, REQUESTER_ID, 2, UserRole.USER_ADMIN);

    ArgumentCaptor<Screen> screen = ArgumentCaptor.forClass(Screen.class);
    verify(messenger).edit(eq(ADMIN), eq(MESSAGE_ID), screen.capture());
    List<String> data = screen.getValue().keyboard().stream()
        .flatMap(List::stream)
        .map(Button::callbackData)
        .toList();
    assertEquals(List.of("ACCESS_TOGGLE:7:6:NODE_ADMIN", "ACCESS_TOGGLE:7:6:READ_ONLY",
                         "ACCESS_TOGGLE:7:6:USER_ADMIN", "ACCESS_GRANT:7:6", "ACCESS_DECLINE:7"),
                 data);
    assertNothingWritten();
  }

  @Test
  @DisplayName("4 AC-5: a role tap by a user without USER_MANAGEMENT/root shows notAllowed")
  void toggleNotAllowed() {
    lenient().when(requests.find(REQUESTER_ID)).thenReturn(Optional.of(REQUEST));

    controller.toggle(READER, MESSAGE_ID, REQUESTER_ID, 1, UserRole.READ_ONLY);

    verify(messenger).edit(READER, MESSAGE_ID, NOT_ALLOWED);
    verify(screens, never()).adminRequest(any(), any());
    assertNothingWritten();
  }

  @Test
  @DisplayName("4 AC-5: a role tap on a request that is gone shows alreadyHandled")
  void toggleAlreadyHandled() {
    when(requests.find(REQUESTER_ID)).thenReturn(Optional.empty());

    controller.toggle(ADMIN, MESSAGE_ID, REQUESTER_ID, 1, UserRole.READ_ONLY);

    verify(messenger).edit(ADMIN, MESSAGE_ID, ALREADY_HANDLED);
    verify(screens, never()).adminRequest(any(), any());
    assertNothingWritten();
  }

  // --- grant / decline -------------------------------------------------------------------------

  @Test
  @DisplayName("4 AC-5: grant by a user without USER_MANAGEMENT/root shows notAllowed and changes nothing")
  void grantNotAllowed() {
    lenient().when(requests.find(REQUESTER_ID)).thenReturn(Optional.of(REQUEST));

    controller.grant(READER, MESSAGE_ID, REQUESTER_ID, Set.of(UserRole.NODE_ADMIN));

    verify(messenger).edit(READER, MESSAGE_ID, NOT_ALLOWED);
    assertNothingWritten();
    verify(messenger, never()).send(any(), any());
  }

  @Test
  @DisplayName("4 AC-5: decline by a user without USER_MANAGEMENT/root shows notAllowed and changes nothing")
  void declineNotAllowed() {
    lenient().when(requests.find(REQUESTER_ID)).thenReturn(Optional.of(REQUEST));

    controller.decline(READER, MESSAGE_ID, REQUESTER_ID);

    verify(messenger).edit(READER, MESSAGE_ID, NOT_ALLOWED);
    assertNothingWritten();
    verify(messenger, never()).send(any(), any());
  }

  @Test
  @DisplayName("4 AC-5: grant on a request that is gone (first decision wins) shows alreadyHandled and changes nothing")
  void grantAlreadyHandled() {
    when(requests.find(REQUESTER_ID)).thenReturn(Optional.empty());

    controller.grant(ADMIN, MESSAGE_ID, REQUESTER_ID, Set.of(UserRole.NODE_ADMIN));

    verify(messenger).edit(ADMIN, MESSAGE_ID, ALREADY_HANDLED);
    assertNothingWritten();
    verify(messenger, never()).send(any(), any());
  }

  @Test
  @DisplayName("4 AC-5: decline on a request that is gone (first decision wins) shows alreadyHandled and changes nothing")
  void declineAlreadyHandled() {
    when(requests.find(REQUESTER_ID)).thenReturn(Optional.empty());

    controller.decline(ADMIN, MESSAGE_ID, REQUESTER_ID);

    verify(messenger).edit(ADMIN, MESSAGE_ID, ALREADY_HANDLED);
    assertNothingWritten();
    verify(messenger, never()).send(any(), any());
  }

  @Test
  @DisplayName("4 AC-5: grant assigns the union of the selected roles' permissions under the requester's id and username")
  void grantAssignsUnion() {
    when(requests.find(REQUESTER_ID)).thenReturn(Optional.of(REQUEST));

    controller.grant(ADMIN, MESSAGE_ID, REQUESTER_ID, Set.of(UserRole.NODE_ADMIN, UserRole.USER_ADMIN));

    assertEquals(Set.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES, Permission.RUN_NODES,
                        Permission.USER_MANAGEMENT),
                 permissions.permissionsOf(REQUESTER_ID));
    assertEquals(Optional.of("newbie"),
                 permissions.storedUser(REQUESTER_ID).map(TgUser::username));
  }

  @Test
  @DisplayName("4 AC-5: grant deletes the request, edits the admin's message to granted and notifies the requester in their language")
  void grantDeletesAndNotifies() {
    when(requests.find(REQUESTER_ID)).thenReturn(Optional.of(REQUEST));
    Set<UserRole> roles = Set.of(UserRole.READ_ONLY);

    controller.grant(ROOT, MESSAGE_ID, REQUESTER_ID, roles);

    verify(requests).delete(REQUESTER_ID);
    verify(screens).granted(REQUEST, roles);
    verify(messenger).edit(ROOT, MESSAGE_ID, GRANTED);
    verify(screens).accessGranted(roles);
    UiContext requester = sentContext(ACCESS_GRANTED);
    assertEquals(REQUESTER_ID, requester.chatId());
    assertEquals("ru", requester.languageCode());
    assertEquals(Set.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES),
                 permissions.permissionsOf(REQUESTER_ID));
  }

  @Test
  @DisplayName("4 AC-5, §4.3: grant with no role selected changes nothing")
  void grantWithoutRolesChangesNothing() {
    lenient().when(requests.find(REQUESTER_ID)).thenReturn(Optional.of(REQUEST));

    controller.grant(ADMIN, MESSAGE_ID, REQUESTER_ID, Set.of());

    assertNothingWritten();
    verify(screens, never()).granted(any(), any());
    verify(screens, never()).declined(any());
    verify(messenger, never()).send(any(), any());
  }

  @Test
  @DisplayName("4 AC-5: decline grants nothing, deletes the request, edits to declined and notifies the requester in their language")
  void decline() {
    when(requests.find(REQUESTER_ID)).thenReturn(Optional.of(REQUEST));

    controller.decline(ADMIN, MESSAGE_ID, REQUESTER_ID);

    assertEquals(Set.of(),permissions.permissionsOf(REQUESTER_ID));
    assertEquals(List.of(), permissions.writes());
    verify(requests).delete(REQUESTER_ID);
    verify(screens).declined(REQUEST);
    verify(messenger).edit(ADMIN, MESSAGE_ID, DECLINED);
    UiContext requester = sentContext(ACCESS_DECLINED);
    assertEquals(REQUESTER_ID, requester.chatId());
    assertEquals("ru", requester.languageCode());
    verify(screens, never()).granted(any(), any());
  }

  @Test
  @DisplayName("4 AC-5: a failing requester notification does not fail the decision")
  void grantNotificationFailureSwallowed() {
    when(requests.find(REQUESTER_ID)).thenReturn(Optional.of(REQUEST));
    doThrow(new IllegalStateException("bot was blocked by the user")).when(messenger).send(any(), any());

    assertDoesNotThrow(() -> controller.grant(ADMIN, MESSAGE_ID, REQUESTER_ID, Set.of(UserRole.READ_ONLY)));

    assertEquals(Set.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES),
                 permissions.permissionsOf(REQUESTER_ID));
    verify(requests).delete(REQUESTER_ID);
    verify(messenger).edit(ADMIN, MESSAGE_ID, GRANTED);
  }

  @Test
  @DisplayName("4 AC-5: a failing requester notification on decline does not fail the decision")
  void declineNotificationFailureSwallowed() {
    when(requests.find(REQUESTER_ID)).thenReturn(Optional.of(REQUEST));
    doThrow(new IllegalStateException("bot was blocked by the user")).when(messenger).send(any(), any());

    assertDoesNotThrow(() -> controller.decline(ADMIN, MESSAGE_ID, REQUESTER_ID));

    verify(requests).delete(REQUESTER_ID);
    verify(messenger).edit(ADMIN, MESSAGE_ID, DECLINED);
  }

  // --- helpers ---------------------------------------------------------------------------------

  private void assertNothingWritten() {
    assertEquals(List.of(), permissions.writes(), "permissions were written");
    verify(requests, never()).create(any());
    verify(requests, never()).delete(anyLong());
  }

  private AccessRequest createdRequest() {
    ArgumentCaptor<AccessRequest> captor = ArgumentCaptor.forClass(AccessRequest.class);
    verify(requests).create(captor.capture());
    return captor.getValue();
  }

  private UiContext sentContext(Screen screen) {
    ArgumentCaptor<UiContext> captor = ArgumentCaptor.forClass(UiContext.class);
    verify(messenger).send(captor.capture(), eq(screen));
    return captor.getValue();
  }

  private List<String> sentLanguages(Screen screen) {
    ArgumentCaptor<UiContext> captor = ArgumentCaptor.forClass(UiContext.class);
    verify(messenger, atLeastOnce()).send(captor.capture(), eq(screen));
    return captor.getAllValues().stream().map(UiContext::languageCode).toList();
  }

  private Set<Long> sentChatIds(Screen screen, int times) {
    ArgumentCaptor<UiContext> captor = ArgumentCaptor.forClass(UiContext.class);
    verify(messenger, times(times)).send(captor.capture(), eq(screen));
    return captor.getAllValues().stream().map(UiContext::chatId).collect(Collectors.toSet());
  }

  private static Screen screen(String name) {
    return new Screen(name, List.of(), List.of());
  }

}
