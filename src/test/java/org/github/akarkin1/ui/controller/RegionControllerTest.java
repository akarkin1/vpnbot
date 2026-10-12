package org.github.akarkin1.ui.controller;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.FakePermissionsService;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.ec2.RegionCatalog;
import org.github.akarkin1.region.RegionRequest;
import org.github.akarkin1.region.RegionRequestService;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.Button;
import org.github.akarkin1.ui.screen.RegionLabels;
import org.github.akarkin1.ui.screen.RegionScreens;
import org.github.akarkin1.ui.screen.Screen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 5 §4.2–§4.4, AC-2–AC-4, D-1, D-3, D-9. Users: two root users (ROOT_ACCESS), a user admin
 * (USER_MANAGEMENT only: not root, D-1), the requester "runner" (RUN_NODES), a reader (read-only),
 * and a pending root known by username only. Permissions live in a {@link FakePermissionsService};
 * the mocked {@link Authorizer} answers from the same table (root implies every permission, as
 * {@code WhiteListAuthorizer} does). The screens are mocks returning one marker screen per method,
 * except for the Regions screen tests, which use the real {@link RegionScreens} over a
 * {@link RegionLabels} with cities so the sorting is observable on the keyboard.
 */
@ExtendWith(MockitoExtension.class)
class RegionControllerTest {

  private static final Integer MESSAGE_ID = 42;
  private static final Instant NOW = Instant.parse("2026-10-12T09:30:00.456Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final long REQUESTER_ID = 7L;
  private static final String REGION = "eu-west-2";

  private static final UiContext ROOT = new UiContext(1L, 1L, "root", "Root", null, "en");
  private static final UiContext ADMIN = new UiContext(10L, 10L, "admin", "Admin", null, "en");
  private static final UiContext READER = new UiContext(20L, 20L, "reader", "Reader", null, "en");
  private static final UiContext REQUESTER =
      new UiContext(REQUESTER_ID, REQUESTER_ID, "runner", "Run", "Ner", "ru");
  private static final TgUser PENDING_ROOT = new TgUser(null, "proot");

  private static final RegionRequest REQUEST =
      new RegionRequest(REGION, REQUESTER_ID, "runner", "Run", "Ner", "ru", 1_760_000_000_000L);

  private static final Screen NONE = screen("none");
  private static final Screen NOT_ALLOWED = screen("notAllowed");
  private static final Screen REQUESTED = screen("requested");
  private static final Screen ALREADY_REQUESTED = screen("alreadyRequested");
  private static final Screen ALREADY_AVAILABLE = screen("alreadyAvailable");
  private static final Screen UNKNOWN = screen("unknown");
  private static final Screen ADMIN_REQUEST = screen("adminRequest");
  private static final Screen NOT_DEPLOYED_YET = screen("notDeployedYet");
  private static final Screen DEPLOYED_ADMIN = screen("deployedAdmin");
  private static final Screen DECLINED_ADMIN = screen("declinedAdmin");
  private static final Screen ALREADY_HANDLED = screen("alreadyHandled");
  private static final Screen AVAILABLE = screen("available");
  private static final Screen DECLINED = screen("declined");

  /** Cities and countries of the regions the catalog returns in these tests. */
  private static final RegionLabels LABELS = new RegionLabels(
      Map.of("eu-central-1", "Frankfurt",
             "eu-west-2", "London",
             "us-east-1", "N. Virginia",
             "ap-northeast-1", "Tokyo",
             "eu-north-1", "Stockholm",
             "sa-east-1", "Sao Paulo",
             "ap-south-1", "mumbai"),
      Map.of("eu-central-1", "DE",
             "eu-west-2", "GB",
             "us-east-1", "US",
             "ap-northeast-1", "JP",
             "eu-north-1", "SE",
             "sa-east-1", "BR",
             "ap-south-1", "IN"));
  private static final Button MENU = new Button("🏠 ${ui.button.menu}", "HOME", null);

  @Mock
  private RegionCatalog catalog;
  @Mock
  private TailscaleNodeService nodeService;
  @Mock
  private RegionRequestService regionRequests;
  @Mock
  private Authorizer authorizer;
  @Mock
  private UiMessenger messenger;
  @Mock
  private RegionScreens screens;

  private FakePermissionsService permissions;
  private RegionController controller;

  @BeforeEach
  void setUp() {
    permissions = new FakePermissionsService()
        .withUser(1L, "root", Permission.ROOT_ACCESS)
        .withUser(2L, "root2", Permission.ROOT_ACCESS)
        .withUser(10L, "admin", Permission.USER_MANAGEMENT, Permission.DELETE_USERS)
        .withUser(REQUESTER_ID, "runner", Permission.RUN_NODES, Permission.LIST_NODES,
                  Permission.SUPPORTED_REGIONS)
        .withUser(20L, "reader", Permission.LIST_NODES, Permission.SUPPORTED_REGIONS)
        .withPending(PENDING_ROOT.username(), Permission.ROOT_ACCESS);
    lenient().when(authorizer.hasPermission(any(), any())).thenAnswer(invocation -> {
      TgUser user = invocation.getArgument(0);
      if (user == null) {
        return false;
      }
      Set<Permission> granted = user.id() != null
          ? permissions.permissionsOf(user.id())
          : permissions.pendingPermissionsOf(user.username());
      return granted.contains(Permission.ROOT_ACCESS) || granted.contains(invocation.getArgument(1));
    });
    lenient().when(screens.none()).thenReturn(NONE);
    lenient().when(screens.notAllowed()).thenReturn(NOT_ALLOWED);
    lenient().when(screens.requested(anyString())).thenReturn(REQUESTED);
    lenient().when(screens.alreadyRequested(anyString())).thenReturn(ALREADY_REQUESTED);
    lenient().when(screens.alreadyAvailable(anyString())).thenReturn(ALREADY_AVAILABLE);
    lenient().when(screens.unknown()).thenReturn(UNKNOWN);
    lenient().when(screens.adminRequest(any())).thenReturn(ADMIN_REQUEST);
    lenient().when(screens.notDeployedYet(any())).thenReturn(NOT_DEPLOYED_YET);
    lenient().when(screens.deployedAdmin(any())).thenReturn(DEPLOYED_ADMIN);
    lenient().when(screens.declinedAdmin(any())).thenReturn(DECLINED_ADMIN);
    lenient().when(screens.alreadyHandled()).thenReturn(ALREADY_HANDLED);
    lenient().when(screens.available(anyString())).thenReturn(AVAILABLE);
    lenient().when(screens.declined(anyString())).thenReturn(DECLINED);
    controller = controller(screens);
  }

  // --- show (§4.2, AC-2) -------------------------------------------------------------------------

  @Test
  @DisplayName("5 AC-2: a user who may not run nodes (read-only, or USER_MANAGEMENT only) gets notAllowed; the catalog is not asked")
  void showNotAllowed() {
    controller.show(READER, MESSAGE_ID);
    controller.show(ADMIN, MESSAGE_ID);

    verify(messenger).edit(READER, MESSAGE_ID, NOT_ALLOWED);
    verify(messenger).edit(ADMIN, MESSAGE_ID, NOT_ALLOWED);
    verifyNoInteractions(catalog, regionRequests);
    verify(screens, never()).regions(any(), any());
  }

  @Test
  @DisplayName("5 AC-2: available = enabled − supported, sorted by city case-insensitively, three per row, ⏳ on the regions of open requests, Menu last")
  void showListsAvailableRegionsSortedByCity() {
    when(catalog.enabledRegions()).thenReturn(Set.of("us-east-1", "eu-central-1", "ap-northeast-1",
                                                     "eu-west-2", "sa-east-1", "eu-north-1",
                                                     "ap-south-1", "xx-new-1"));
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of("eu-central-1", "eu-north-1"));
    when(regionRequests.findAll()).thenReturn(List.of(
        REQUEST, new RegionRequest("eu-central-1", 8L, null, "Other", null, "en", 1L)));

    controller(new RegionScreens(LABELS)).show(REQUESTER, MESSAGE_ID);

    Screen screen = editedScreen(REQUESTER);
    assertEquals("🌍 <b>${ui.regions.title}</b>\n${ui.regions.hint} ⏳ ${ui.regions.hint-pending}", screen.template());
    assertEquals(List.of(
                     List.of(region("⏳ 🇬🇧 London", "eu-west-2"),
                             region("🇮🇳 mumbai", "ap-south-1"),
                             region("🇺🇸 N. Virginia", "us-east-1")),
                     List.of(region("🇧🇷 Sao Paulo", "sa-east-1"),
                             region("🇯🇵 Tokyo", "ap-northeast-1"),
                             region("🌐 xx-new-1", "xx-new-1")),
                     List.of(MENU)),
                 screen.keyboard());
  }

  @Test
  @DisplayName("5 AC-2, D-2: a root user without RUN_NODES may open the Regions screen")
  void showForRoot() {
    when(catalog.enabledRegions()).thenReturn(Set.of("eu-west-2", "eu-central-1"));
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of());
    lenient().when(regionRequests.findAll()).thenReturn(List.of());

    controller(new RegionScreens(LABELS)).show(ROOT, MESSAGE_ID);

    assertEquals(List.of(List.of(region("🇩🇪 Frankfurt", "eu-central-1"), region("🇬🇧 London", "eu-west-2")),
                         List.of(MENU)),
                 editedScreen(ROOT).keyboard());
  }

  @Test
  @DisplayName("5 AC-2: with every enabled region set up already the user gets none")
  void showNoneWhenEverythingIsSetUp() {
    when(catalog.enabledRegions()).thenReturn(Set.of("eu-central-1", "eu-west-2"));
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of("eu-central-1", "eu-west-2", "us-east-1"));
    lenient().when(regionRequests.findAll()).thenReturn(List.of());

    controller.show(REQUESTER, MESSAGE_ID);

    verify(messenger).edit(REQUESTER, MESSAGE_ID, NONE);
    verify(screens, never()).regions(any(), any());
  }

  @Test
  @DisplayName("5 AC-2: an empty catalog gives none")
  void showNoneWhenCatalogIsEmpty() {
    when(catalog.enabledRegions()).thenReturn(Set.of());
    lenient().when(nodeService.getSupportedRegionIds()).thenReturn(Set.of("eu-central-1"));
    lenient().when(regionRequests.findAll()).thenReturn(List.of());

    controller.show(REQUESTER, MESSAGE_ID);

    verify(messenger).edit(REQUESTER, MESSAGE_ID, NONE);
  }

  @Test
  @DisplayName("5 §4.5: a catalog (EC2) failure propagates to the router's generic error path")
  void showCatalogFailurePropagates() {
    when(catalog.enabledRegions()).thenThrow(new IllegalStateException("ec2 down"));
    lenient().when(nodeService.getSupportedRegionIds()).thenReturn(Set.of());

    assertThrows(IllegalStateException.class, () -> controller.show(REQUESTER, MESSAGE_ID));
  }

  // --- request (§4.3, AC-3) ----------------------------------------------------------------------

  @Test
  @DisplayName("5 AC-3 (1): a user who may not run nodes gets notAllowed; nothing is recorded or sent")
  void requestNotAllowed() {
    lenient().when(catalog.enabledRegions()).thenReturn(Set.of(REGION));
    lenient().when(nodeService.getSupportedRegionIds()).thenReturn(Set.of());
    lenient().when(regionRequests.find(REGION)).thenReturn(Optional.empty());

    controller.request(READER, MESSAGE_ID, REGION);
    controller.request(ADMIN, MESSAGE_ID, REGION);

    verify(messenger).edit(READER, MESSAGE_ID, NOT_ALLOWED);
    verify(messenger).edit(ADMIN, MESSAGE_ID, NOT_ALLOWED);
    assertNothingRecordedOrSent();
  }

  @Test
  @DisplayName("5 AC-3 (2): a region that is supported already gets alreadyAvailable – before the catalog and the open-request checks")
  void requestAlreadyAvailable() {
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of(REGION, "eu-central-1"));
    lenient().when(catalog.enabledRegions()).thenReturn(Set.of("eu-central-1"));
    lenient().when(regionRequests.find(REGION)).thenReturn(Optional.of(REQUEST));

    controller.request(REQUESTER, MESSAGE_ID, REGION);

    verify(screens).alreadyAvailable(REGION);
    verify(messenger).edit(REQUESTER, MESSAGE_ID, ALREADY_AVAILABLE);
    assertNothingRecordedOrSent();
  }

  @Test
  @DisplayName("5 AC-3 (3): a region not in the catalog (not enabled, a stale button) gets unknown – before the open-request check")
  void requestUnknownRegion() {
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of("eu-central-1"));
    when(catalog.enabledRegions()).thenReturn(Set.of("eu-central-1", "us-east-1"));
    lenient().when(regionRequests.find(REGION)).thenReturn(Optional.of(REQUEST));

    controller.request(REQUESTER, MESSAGE_ID, REGION);

    verify(messenger).edit(REQUESTER, MESSAGE_ID, UNKNOWN);
    assertNothingRecordedOrSent();
  }

  @Test
  @DisplayName("5 AC-3 (4), D-3: an open request for the region gets alreadyRequested; nobody is notified again")
  void requestAlreadyRequested() {
    stubRequestable();
    when(regionRequests.find(REGION)).thenReturn(Optional.of(
        new RegionRequest(REGION, 8L, "first", "First", null, "en", 1L)));

    controller.request(REQUESTER, MESSAGE_ID, REGION);

    verify(screens).alreadyRequested(REGION);
    verify(messenger).edit(REQUESTER, MESSAGE_ID, ALREADY_REQUESTED);
    assertNothingRecordedOrSent();
  }

  @Test
  @DisplayName("5 AC-3 (5): a new request carries the requester's id, names and language and requestedAt from the clock; the requester sees requested")
  void requestRecorded() {
    stubRequestable();

    controller.request(REQUESTER, MESSAGE_ID, REGION);

    assertEquals(new RegionRequest(REGION, REQUESTER_ID, "runner", "Run", "Ner", "ru", NOW.toEpochMilli()),
                 createdRequest());
    verify(screens).requested(REGION);
    verify(messenger).edit(REQUESTER, MESSAGE_ID, REQUESTED);
  }

  @Test
  @DisplayName("5 AC-3, D-1: every root user with an id gets adminRequest of the new request at chat id = user id; USER_MANAGEMENT admins, other users and pending roots do not")
  void requestNotifiesRootUsersOnly() {
    stubRequestable();

    controller.request(REQUESTER, MESSAGE_ID, REGION);

    RegionRequest created = createdRequest();
    verify(screens, atLeastOnce()).adminRequest(created);
    verify(screens, never()).adminRequest(argThat(request -> !created.equals(request)));
    List<UiContext> admins = sentContexts(ADMIN_REQUEST, 2);
    assertEquals(Set.of(1L, 2L), admins.stream().map(UiContext::chatId).collect(Collectors.toSet()));
    assertAll(admins.stream().map(admin -> (Executable) () -> assertEquals(
        admin.chatId(), admin.userId(), "chat id = user id: " + admin)));
    verify(messenger, never()).send(argThat(context -> context != null && context.chatId() == 10L), any());
    verify(messenger, never()).send(argThat(context -> context != null && context.chatId() == REQUESTER_ID),
                                    any());
  }

  @Test
  @DisplayName("5 AC-3: the request is recorded before the root users are notified")
  void requestRecordedBeforeNotifying() {
    stubRequestable();

    controller.request(REQUESTER, MESSAGE_ID, REGION);

    InOrder inOrder = inOrder(regionRequests, messenger);
    inOrder.verify(regionRequests).create(any());
    inOrder.verify(messenger, times(2)).send(any(), eq(ADMIN_REQUEST));
  }

  @Test
  @DisplayName("5 D-9: a root requester gets their own request message too, as do the other root users")
  void rootRequesterNotifiedToo() {
    stubRequestable();

    controller.request(ROOT, MESSAGE_ID, REGION);

    assertEquals(new RegionRequest(REGION, 1L, "root", "Root", null, "en", NOW.toEpochMilli()),
                 createdRequest());
    verify(messenger).edit(ROOT, MESSAGE_ID, REQUESTED);
    assertEquals(Set.of(1L, 2L), sentContexts(ADMIN_REQUEST, 2).stream().map(UiContext::chatId)
        .collect(Collectors.toSet()));
  }

  @Test
  @DisplayName("5 §4.3: with no root user the request still stands and the requester sees requested; nothing is sent")
  void requestWithoutRootUsers() {
    FakePermissionsService noRoot = new FakePermissionsService()
        .withUser(10L, "admin", Permission.USER_MANAGEMENT)
        .withUser(REQUESTER_ID, "runner", Permission.RUN_NODES)
        .withPending(PENDING_ROOT.username(), Permission.ROOT_ACCESS);
    permissions = noRoot;
    RegionController withoutRoot = new RegionController(catalog, nodeService, regionRequests, noRoot,
                                                        authorizer, messenger, screens, CLOCK);
    stubRequestable();

    assertDoesNotThrow(() -> withoutRoot.request(REQUESTER, MESSAGE_ID, REGION));

    verify(regionRequests).create(any());
    verify(messenger).edit(REQUESTER, MESSAGE_ID, REQUESTED);
    verify(messenger, never()).send(any(), any());
  }

  @Test
  @DisplayName("5 AC-3: a root user who cannot be reached is logged; the other root user still gets the message and the request stands")
  void requestAdminNotificationFailure() {
    stubRequestable();
    lenient().doThrow(new IllegalStateException("chat not found"))
        .when(messenger).send(argThat(context -> context != null && context.chatId() == 1L), any());

    assertDoesNotThrow(() -> controller.request(REQUESTER, MESSAGE_ID, REGION));

    verify(regionRequests).create(any());
    verify(messenger).edit(REQUESTER, MESSAGE_ID, REQUESTED);
    verify(messenger).send(argThat(context -> context != null && context.chatId() == 2L), eq(ADMIN_REQUEST));
  }

  // --- deployed (§4.4, AC-4) ---------------------------------------------------------------------

  @Test
  @DisplayName("5 AC-4, D-1: Deployed by a non-root user (USER_MANAGEMENT, RUN_NODES) gets notAllowed and changes nothing")
  void deployedNotAllowed() {
    lenient().when(regionRequests.find(REGION)).thenReturn(Optional.of(REQUEST));
    lenient().when(nodeService.getSupportedRegionIds()).thenReturn(Set.of(REGION));

    controller.deployed(ADMIN, MESSAGE_ID, REGION);
    controller.deployed(REQUESTER, MESSAGE_ID, REGION);

    verify(messenger).edit(ADMIN, MESSAGE_ID, NOT_ALLOWED);
    verify(messenger).edit(REQUESTER, MESSAGE_ID, NOT_ALLOWED);
    assertNothingRecordedOrSent();
  }

  @Test
  @DisplayName("5 AC-4: Deployed on a request that is gone (first decision wins) gets alreadyHandled and changes nothing")
  void deployedAlreadyHandled() {
    when(regionRequests.find(REGION)).thenReturn(Optional.empty());
    lenient().when(nodeService.getSupportedRegionIds()).thenReturn(Set.of(REGION));

    controller.deployed(ROOT, MESSAGE_ID, REGION);

    verify(messenger).edit(ROOT, MESSAGE_ID, ALREADY_HANDLED);
    assertNothingRecordedOrSent();
  }

  @Test
  @DisplayName("5 AC-4: Deployed before the region is supported edits to notDeployedYet (with the buttons), deletes nothing and notifies nobody")
  void deployedNotYet() {
    when(regionRequests.find(REGION)).thenReturn(Optional.of(REQUEST));
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of("eu-central-1"));

    controller.deployed(ROOT, MESSAGE_ID, REGION);

    verify(screens).notDeployedYet(REQUEST);
    verify(messenger).edit(ROOT, MESSAGE_ID, NOT_DEPLOYED_YET);
    assertNothingRecordedOrSent();
    verify(screens, never()).deployedAdmin(any());
  }

  @Test
  @DisplayName("5 AC-4: Deployed on a supported region deletes the request, edits to deployedAdmin and notifies the requester in their language with available(region)")
  void deployedNotifiesRequester() {
    when(regionRequests.find(REGION)).thenReturn(Optional.of(REQUEST));
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of("eu-central-1", REGION));

    controller.deployed(ROOT, MESSAGE_ID, REGION);

    verify(regionRequests).delete(REGION);
    verify(screens).deployedAdmin(REQUEST);
    verify(messenger).edit(ROOT, MESSAGE_ID, DEPLOYED_ADMIN);
    verify(screens).available(REGION);
    UiContext requester = sentContext(AVAILABLE);
    assertEquals(REQUESTER_ID, requester.chatId());
    assertEquals(REQUESTER_ID, requester.userId());
    assertEquals("ru", requester.languageCode());
    verify(regionRequests, never()).create(any());
  }

  @Test
  @DisplayName("5 AC-4: a failing requester notification does not fail Deployed")
  void deployedNotificationFailureSwallowed() {
    when(regionRequests.find(REGION)).thenReturn(Optional.of(REQUEST));
    when(nodeService.getSupportedRegionIds()).thenReturn(Set.of(REGION));
    doThrow(new IllegalStateException("bot was blocked by the user")).when(messenger).send(any(), any());

    assertDoesNotThrow(() -> controller.deployed(ROOT, MESSAGE_ID, REGION));

    verify(regionRequests).delete(REGION);
    verify(messenger).edit(ROOT, MESSAGE_ID, DEPLOYED_ADMIN);
  }

  // --- decline (§4.4, AC-4) ----------------------------------------------------------------------

  @Test
  @DisplayName("5 AC-4, D-1: Decline by a non-root user (USER_MANAGEMENT, RUN_NODES) gets notAllowed and changes nothing")
  void declineNotAllowed() {
    lenient().when(regionRequests.find(REGION)).thenReturn(Optional.of(REQUEST));

    controller.decline(ADMIN, MESSAGE_ID, REGION);
    controller.decline(REQUESTER, MESSAGE_ID, REGION);

    verify(messenger).edit(ADMIN, MESSAGE_ID, NOT_ALLOWED);
    verify(messenger).edit(REQUESTER, MESSAGE_ID, NOT_ALLOWED);
    assertNothingRecordedOrSent();
  }

  @Test
  @DisplayName("5 AC-4: Decline on a request that is gone gets alreadyHandled and changes nothing")
  void declineAlreadyHandled() {
    when(regionRequests.find(REGION)).thenReturn(Optional.empty());

    controller.decline(ROOT, MESSAGE_ID, REGION);

    verify(messenger).edit(ROOT, MESSAGE_ID, ALREADY_HANDLED);
    assertNothingRecordedOrSent();
  }

  @Test
  @DisplayName("5 AC-4: Decline deletes the request, edits to declinedAdmin and notifies the requester in their language with declined(region)")
  void declineNotifiesRequester() {
    when(regionRequests.find(REGION)).thenReturn(Optional.of(REQUEST));
    lenient().when(nodeService.getSupportedRegionIds()).thenReturn(Set.of());

    controller.decline(ROOT, MESSAGE_ID, REGION);

    verify(regionRequests).delete(REGION);
    verify(screens).declinedAdmin(REQUEST);
    verify(messenger).edit(ROOT, MESSAGE_ID, DECLINED_ADMIN);
    verify(screens).declined(REGION);
    UiContext requester = sentContext(DECLINED);
    assertEquals(REQUESTER_ID, requester.chatId());
    assertEquals("ru", requester.languageCode());
    verify(screens, never()).deployedAdmin(any());
    verify(screens, never()).available(anyString());
  }

  @Test
  @DisplayName("5 AC-4: a failing requester notification does not fail Decline")
  void declineNotificationFailureSwallowed() {
    when(regionRequests.find(REGION)).thenReturn(Optional.of(REQUEST));
    doThrow(new IllegalStateException("bot was blocked by the user")).when(messenger).send(any(), any());

    assertDoesNotThrow(() -> controller.decline(ROOT, MESSAGE_ID, REGION));

    verify(regionRequests).delete(REGION);
    verify(messenger).edit(ROOT, MESSAGE_ID, DECLINED_ADMIN);
  }

  // --- helpers -----------------------------------------------------------------------------------

  private RegionController controller(RegionScreens regionScreens) {
    return new RegionController(catalog, nodeService, regionRequests, permissions, authorizer,
                                messenger, regionScreens, CLOCK);
  }

  /** {@link #REGION} is enabled, not supported and has no open request. */
  private void stubRequestable() {
    lenient().when(nodeService.getSupportedRegionIds()).thenReturn(Set.of("eu-central-1"));
    lenient().when(catalog.enabledRegions()).thenReturn(Set.of("eu-central-1", REGION, "us-east-1"));
    lenient().when(regionRequests.find(REGION)).thenReturn(Optional.empty());
  }

  private void assertNothingRecordedOrSent() {
    verify(regionRequests, never()).create(any());
    verify(regionRequests, never()).delete(anyString());
    verify(messenger, never()).send(any(), any());
  }

  private RegionRequest createdRequest() {
    ArgumentCaptor<RegionRequest> captor = ArgumentCaptor.forClass(RegionRequest.class);
    verify(regionRequests).create(captor.capture());
    return captor.getValue();
  }

  private Screen editedScreen(UiContext context) {
    ArgumentCaptor<Screen> captor = ArgumentCaptor.forClass(Screen.class);
    verify(messenger).edit(eq(context), eq(MESSAGE_ID), captor.capture());
    return captor.getValue();
  }

  private UiContext sentContext(Screen screen) {
    ArgumentCaptor<UiContext> captor = ArgumentCaptor.forClass(UiContext.class);
    verify(messenger).send(captor.capture(), eq(screen));
    return captor.getValue();
  }

  private List<UiContext> sentContexts(Screen screen, int times) {
    ArgumentCaptor<UiContext> captor = ArgumentCaptor.forClass(UiContext.class);
    verify(messenger, times(times)).send(captor.capture(), eq(screen));
    return captor.getAllValues();
  }

  private static Button region(String label, String regionId) {
    return new Button(label, "REGION_REQ:" + regionId, null);
  }

  private static Screen screen(String name) {
    return new Screen(name, List.of(), List.of());
  }

}
