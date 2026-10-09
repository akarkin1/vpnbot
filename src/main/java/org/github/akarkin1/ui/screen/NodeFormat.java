package org.github.akarkin1.ui.screen;

import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.ecs.RunTaskStatus;
import org.github.akarkin1.ecs.TaskInfo;

import java.util.List;

final class NodeFormat {

  private static final String DASH = "—";

  private NodeFormat() {
  }

  static String statusEmoji(String state) {
    if (RunTaskStatus.HEALTHY.name().equals(state)) {
      return "🟢";
    }
    if (RunTaskStatus.UNHEALTHY.name().equals(state)) {
      return "🔴";
    }
    return "🟡";
  }

  static String orDash(String value) {
    return StringUtils.isBlank(value) ? DASH : value;
  }

  /** Escapes {@code %} in dynamic button-label parts, since the translator formats labels. */
  static String escapePercent(String value) {
    return value.replace("%", "%%");
  }

  static List<Object> nodeParams(TaskInfo node, RegionLabels regionLabels) {
    String regionLabel = node.getRegion() == null ? null : regionLabels.label(node.getRegion().id());
    return List.of(statusEmoji(node.getState()),
                   orDash(node.getHostName()),
                   orDash(regionLabel),
                   orDash(node.getPublicIp()));
  }

}
