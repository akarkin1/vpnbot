package org.github.akarkin1.ui.screen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeFormatTest {

  @Test
  @DisplayName("AC-5: status emoji is green for HEALTHY, red for UNHEALTHY, yellow otherwise")
  void statusEmoji() {
    assertAll(
        () -> assertEquals("🟢", NodeFormat.statusEmoji("HEALTHY")),
        () -> assertEquals("🔴", NodeFormat.statusEmoji("UNHEALTHY")),
        () -> assertEquals("🟡", NodeFormat.statusEmoji("UNKNOWN")),
        () -> assertEquals("🟡", NodeFormat.statusEmoji(null)));
  }

  @Test
  @DisplayName("AC-5: null or blank values are shown as an em dash")
  void orDash() {
    assertAll(
        () -> assertEquals("—", NodeFormat.orDash(null)),
        () -> assertEquals("—", NodeFormat.orDash("")),
        () -> assertEquals("—", NodeFormat.orDash("  ")),
        () -> assertEquals("node-1", NodeFormat.orDash("node-1")));
  }

  @Test
  @DisplayName("2b D-11: escapePercent doubles every % so a label survives String.formatted")
  void escapePercent() {
    assertAll(
        () -> assertEquals("node-1", NodeFormat.escapePercent("node-1")),
        () -> assertEquals("100%%-node", NodeFormat.escapePercent("100%-node")),
        () -> assertEquals("%%%%s", NodeFormat.escapePercent("%%s")),
        () -> assertEquals("a%sb", NodeFormat.escapePercent("a%sb").formatted()));
  }

}
