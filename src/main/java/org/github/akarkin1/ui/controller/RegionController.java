package org.github.akarkin1.ui.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.PermissionsService;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.ec2.RegionCatalog;
import org.github.akarkin1.region.RegionRequest;
import org.github.akarkin1.region.RegionRequestService;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.RegionScreens;
import org.github.akarkin1.ui.screen.Screen;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Region requests: a user who may run nodes asks for a region that is not set up yet, every root
 * user gets a message with Deployed, notify and Decline (D-1), the first decision wins and the
 * requester is notified.
 */
@Log4j2
@RequiredArgsConstructor
public class RegionController {

  private final RegionCatalog catalog;
  private final TailscaleNodeService nodeService;
  private final RegionRequestService regionRequests;
  private final PermissionsService permissionsService;
  private final Authorizer authorizer;
  private final UiMessenger messenger;
  private final RegionScreens screens;
  private final Clock clock;

  /** The Regions screen: the enabled regions without node infrastructure, pending ones marked. */
  public void show(UiContext context, Integer messageId) {
    if (!mayRunNodes(context.user())) {
      messenger.edit(context, messageId, screens.notAllowed());
      return;
    }
    Set<String> supported = nodeService.getSupportedRegionIds();
    // RegionScreens.regions sorts by city: the controller has no RegionLabels (§7)
    List<String> available = catalog.enabledRegions().stream()
        .filter(regionId -> !supported.contains(regionId))
        .sorted()
        .toList();
    if (available.isEmpty()) {
      messenger.edit(context, messageId, screens.none());
      return;
    }
    Set<String> pending = regionRequests.findAll().stream()
        .map(RegionRequest::regionId)
        .collect(Collectors.toSet());
    messenger.edit(context, messageId, screens.regions(available, pending));
  }

  /** The tap on a region of the Regions screen. */
  public void request(UiContext context, Integer messageId, String regionId) {
    if (!mayRunNodes(context.user())) {
      messenger.edit(context, messageId, screens.notAllowed());
      return;
    }
    if (nodeService.getSupportedRegionIds().contains(regionId)) {
      messenger.edit(context, messageId, screens.alreadyAvailable(regionId));
      return;
    }
    if (!catalog.enabledRegions().contains(regionId)) {
      messenger.edit(context, messageId, screens.unknown());
      return;
    }
    if (regionRequests.find(regionId).isPresent()) {
      messenger.edit(context, messageId, screens.alreadyRequested(regionId));
      return;
    }

    RegionRequest request = new RegionRequest(regionId, context.userId(), context.username(),
                                              context.firstName(), context.lastName(),
                                              context.languageCode(), clock.millis());
    regionRequests.create(request);
    log.info("User {} requested region {}", request.userId(), regionId);
    messenger.edit(context, messageId, screens.requested(regionId));
    notifyRootUsers(request);
  }

  /**
   * A root user's Deployed, notify: closes the request and notifies the requester once the region
   * is supported; before that only adds the not-deployed-yet hint.
   */
  public void deployed(UiContext context, Integer messageId, String regionId) {
    findDecidable(context, messageId, regionId).ifPresent(request -> {
      if (!nodeService.getSupportedRegionIds().contains(regionId)) {
        messenger.edit(context, messageId, screens.notDeployedYet(request));
        return;
      }
      regionRequests.delete(regionId);
      log.info("Region request of user {} for {} deployed by user {}", request.userId(), regionId,
               context.userId());

      messenger.edit(context, messageId, screens.deployedAdmin(request));
      notifyRequester(request, screens.available(regionId));
    });
  }

  /** A root user's Decline: deletes the request and notifies the requester. */
  public void decline(UiContext context, Integer messageId, String regionId) {
    findDecidable(context, messageId, regionId).ifPresent(request -> {
      regionRequests.delete(regionId);
      log.info("Region request of user {} for {} declined by user {}", request.userId(), regionId,
               context.userId());

      messenger.edit(context, messageId, screens.declinedAdmin(request));
      notifyRequester(request, screens.declined(regionId));
    });
  }

  /** {@code RUN_NODES} or root (D-2), the condition of the region buttons on the home card. */
  private boolean mayRunNodes(TgUser user) {
    return user.id() != null
           && (authorizer.hasPermission(user, Permission.RUN_NODES) || isRoot(user));
  }

  private boolean isRoot(TgUser user) {
    return authorizer.hasPermission(user, Permission.ROOT_ACCESS);
  }

  /** The open request if the caller is root; otherwise edits the message and returns empty. */
  private Optional<RegionRequest> findDecidable(UiContext context, Integer messageId,
                                                String regionId) {
    if (!isRoot(context.user())) {
      messenger.edit(context, messageId, screens.notAllowed());
      return Optional.empty();
    }
    Optional<RegionRequest> found = regionRequests.find(regionId);
    if (found.isEmpty()) {
      messenger.edit(context, messageId, screens.alreadyHandled());
      return Optional.empty();
    }
    return found;
  }

  /** Sends the request to every root user (D-1, D-9); a private chat's id equals the user id. */
  // Pre-registration by username ended with 2026.10. Remove once PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
  @SuppressWarnings("removal")
  private void notifyRootUsers(RegionRequest request) {
    List<TgUser> rootUsers = permissionsService.getUsers().entrySet().stream()
        .filter(entry -> entry.getValue().contains(Permission.ROOT_ACCESS))
        .map(Map.Entry::getKey)
        .filter(rootUser -> !rootUser.pending())
        .toList();
    if (rootUsers.isEmpty()) {
      log.warn("No root user to notify of the request of user {} for region {}", request.userId(),
               request.regionId());
      return;
    }

    Screen screen = screens.adminRequest(request);
    for (TgUser rootUser : rootUsers) {
      // the user record has no language: the bot's default
      UiContext rootChat = new UiContext(rootUser.id(), rootUser.id(), rootUser.username(), null,
                                         null, null);
      try {
        messenger.send(rootChat, screen);
      } catch (RuntimeException e) {
        log.error("Failed to notify root user {} of the request of user {} for region {}",
                  rootUser.id(), request.userId(), request.regionId(), e);
      }
    }
  }

  private void notifyRequester(RegionRequest request, Screen screen) {
    UiContext requesterContext = new UiContext(request.userId(), request.userId(),
                                               request.username(), request.firstName(),
                                               request.lastName(), request.languageCode());
    try {
      messenger.send(requesterContext, screen);
    } catch (RuntimeException e) {
      log.error("Failed to notify user {} of the decision on region {}", request.userId(),
                request.regionId(), e);
    }
  }

}
