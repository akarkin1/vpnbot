package org.github.akarkin1.ui.screen;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.region.RegionRequest;
import org.github.akarkin1.ui.UiAction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.github.akarkin1.ui.screen.CommonButtons.MENU;

/**
 * The screens of the region request: the Regions screen, the requester's side and the root users'
 * message. The region label is a bold param, the region id a {@code <code>} param (D-10).
 */
@RequiredArgsConstructor
public class RegionScreens {

  private static final int REGIONS_PER_ROW = 3;
  private static final String PENDING_PREFIX = "⏳ ";

  private final RegionLabels labels;

  /**
   * The regions that can be requested, sorted by city, three per row; the pending ones marked
   * with ⏳.
   */
  public Screen regions(List<String> available, Set<String> pending) {
    List<Button> buttons = available.stream()
        .sorted(Comparator.comparing(labels::city, String.CASE_INSENSITIVE_ORDER))
        .map(regionId -> Button.action(
            (pending.contains(regionId) ? PENDING_PREFIX : "") + labels.label(regionId),
            UiAction.regionRequest(regionId)))
        .toList();
    List<List<Button>> keyboard = new ArrayList<>();
    for (int from = 0; from < buttons.size(); from += REGIONS_PER_ROW) {
      keyboard.add(buttons.subList(from, Math.min(from + REGIONS_PER_ROW, buttons.size())));
    }
    keyboard.add(List.of(MENU));
    return new Screen("🌍 <b>${ui.regions.title}</b>\n${ui.regions.hint} ⏳ ${ui.regions.hint-pending}",
                      List.of(), keyboard);
  }

  public Screen none() {
    return menuScreen("ℹ️ ${ui.regions.none}", List.of());
  }

  public Screen notAllowed() {
    return menuScreen("⛔ ${ui.regions.not-allowed}", List.of());
  }

  public Screen requested(String regionId) {
    return menuScreen("📨 <b>%s</b>: ${ui.regions.requested}", List.of(labels.label(regionId)));
  }

  public Screen alreadyRequested(String regionId) {
    return menuScreen("⏳ <b>%s</b> ${ui.regions.already-requested}",
                      List.of(labels.label(regionId)));
  }

  public Screen alreadyAvailable(String regionId) {
    return menuScreen("ℹ️ <b>%s</b> ${ui.regions.already-available}",
                      List.of(labels.label(regionId)));
  }

  public Screen unknown() {
    return menuScreen("⚠️ ${ui.regions.unknown}", List.of());
  }

  /** The root user's message: who asks for which region, Deployed, notify and Decline. */
  public Screen adminRequest(RegionRequest request) {
    UserLine requester = requester(request);
    return new Screen("🌍 " + requester.template() + " ${ui.regions.asks} <b>%s</b>, <code>%s</code>",
                      concat(requester.params(), regionParams(request)), decisionKeyboard(request));
  }

  /** The root user's message again, with the hint that the region is not supported yet. */
  public Screen notDeployedYet(RegionRequest request) {
    Screen adminRequest = adminRequest(request);
    return new Screen(adminRequest.template() + "\n\n⚠️ ${ui.regions.not-deployed-yet}",
                      adminRequest.params(), adminRequest.keyboard());
  }

  /** The root user's message after Deployed, notify, no buttons. */
  public Screen deployedAdmin(RegionRequest request) {
    return closed("✅", "${ui.regions.deployed-admin}", request);
  }

  /** The root user's message after Decline, no buttons. */
  public Screen declinedAdmin(RegionRequest request) {
    return closed("✖", "${ui.regions.declined-admin}", request);
  }

  public Screen alreadyHandled() {
    return new Screen("ℹ️ ${ui.regions.already-handled}", List.of(), List.of());
  }

  /** The requester's notification: the region is set up. */
  public Screen available(String regionId) {
    return menuScreen("✅ <b>%s</b> ${ui.regions.available}", List.of(labels.label(regionId)));
  }

  /** The requester's notification of a decline, no buttons. */
  public Screen declined(String regionId) {
    return new Screen("✖ <b>%s</b>: ${ui.regions.declined}", List.of(labels.label(regionId)),
                      List.of());
  }

  /** {@code <b>label</b>, <code>id</code> requested by <user>: <outcome>} (D-10). */
  private Screen closed(String icon, String outcome, RegionRequest request) {
    UserLine requester = requester(request);
    return new Screen(icon + " <b>%s</b>, <code>%s</code> ${ui.regions.requested-by} "
                      + requester.template() + ": " + outcome,
                      concat(regionParams(request), requester.params()), List.of());
  }

  private List<Object> regionParams(RegionRequest request) {
    return List.of(labels.label(request.regionId()), request.regionId());
  }

  private static List<List<Button>> decisionKeyboard(RegionRequest request) {
    return List.of(List.of(
        Button.action("✅ ${ui.button.deployed-notify}", UiAction.regionDeployed(request.regionId())),
        Button.action("✖ ${ui.button.decline}", UiAction.regionDecline(request.regionId()))));
  }

  private static UserLine requester(RegionRequest request) {
    return UserLine.of(request.userId(), request.username(), request.firstName(),
                       request.lastName());
  }

  private static Screen menuScreen(String template, List<Object> params) {
    return new Screen(template, params, List.of(List.of(MENU)));
  }

  private static List<Object> concat(List<Object> first, List<Object> second) {
    List<Object> all = new ArrayList<>(first);
    all.addAll(second);
    return all;
  }

}
