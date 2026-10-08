package org.github.akarkin1.ui.screen;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.ecs.TaskInfo;
import org.github.akarkin1.ui.UiAction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
public class HomeScreen {

  private static final int REGIONS_PER_ROW = 3;
  private static final List<Button> REFRESH_HELP_ROW = List.of(
      Button.action("🔄 ${ui.button.refresh}", UiAction.home()),
      Button.action("❓ ${ui.button.help}", UiAction.help()));

  private final RegionLabels regionLabels;

  public Screen home(HomeModel model) {
    StringBuilder template = new StringBuilder();
    List<Object> params = new ArrayList<>();
    appendGreeting(model, template, params);
    if (!model.canListNodes() && !model.canRunNodes()) {
      template.append("\n\n${ui.home.no-access}");
    }
    if (model.canListNodes()) {
      appendNodes(model, template, params);
    }
    if (model.canRunNodes()) {
      appendStartNode(model, template);
    }
    return new Screen(template.toString(), params, keyboard(model));
  }

  private static void appendGreeting(HomeModel model, StringBuilder template, List<Object> params) {
    String displayName = displayName(model);
    if (displayName == null) {
      template.append("👋 ${ui.home.greeting}!");
    } else {
      template.append("👋 ${ui.home.greeting}, %s!");
      params.add(displayName);
    }
  }

  private static String displayName(HomeModel model) {
    if (StringUtils.isNotBlank(model.firstName())) {
      return model.firstName();
    }
    if (StringUtils.isNotBlank(model.username())) {
      return "@" + model.username();
    }
    return null;
  }

  private void appendNodes(HomeModel model, StringBuilder template, List<Object> params) {
    template.append(model.allNodes() ? "\n\n<b>${ui.home.all-nodes}</b>" : "\n\n<b>${ui.home.your-nodes}</b>");
    if (model.nodes().isEmpty()) {
      template.append("\n${ui.home.no-nodes}");
      return;
    }
    for (TaskInfo node : model.nodes()) {
      template.append("\n%s <b>%s</b> · %s · <code>%s</code>");
      params.addAll(NodeFormat.nodeParams(node, regionLabels));
    }
  }

  private static void appendStartNode(HomeModel model, StringBuilder template) {
    template.append("\n\n<b>${ui.home.start-node}</b>");
    if (model.regionIds().isEmpty()) {
      template.append("\n${ui.home.no-regions}");
    }
  }

  private List<List<Button>> keyboard(HomeModel model) {
    List<List<Button>> keyboard = new ArrayList<>();
    if (model.canRunNodes()) {
      keyboard.addAll(regionRows(model.regionIds()));
    }
    keyboard.add(REFRESH_HELP_ROW);
    return keyboard;
  }

  private List<List<Button>> regionRows(List<String> regionIds) {
    List<Button> buttons = regionIds.stream()
        .sorted(Comparator.comparing(regionLabels::city, String.CASE_INSENSITIVE_ORDER))
        .map(regionId -> Button.action(regionLabels.label(regionId), UiAction.run(regionId)))
        .toList();
    List<List<Button>> rows = new ArrayList<>();
    for (int from = 0; from < buttons.size(); from += REGIONS_PER_ROW) {
      rows.add(buttons.subList(from, Math.min(from + REGIONS_PER_ROW, buttons.size())));
    }
    return rows;
  }

}
