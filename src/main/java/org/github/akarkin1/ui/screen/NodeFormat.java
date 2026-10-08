package org.github.akarkin1.ui.screen;

import org.apache.commons.lang3.StringUtils;

final class NodeFormat {

  private static final String DASH = "—";

  private NodeFormat() {
  }

  static String statusEmoji(String state) {
    if ("HEALTHY".equals(state)) {
      return "🟢";
    }
    if ("UNHEALTHY".equals(state)) {
      return "🔴";
    }
    return "🟡";
  }

  static String orDash(String value) {
    return StringUtils.isBlank(value) ? DASH : value;
  }

}
