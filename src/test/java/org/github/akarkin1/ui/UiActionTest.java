package org.github.akarkin1.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiActionTest {

  @Test
  @DisplayName("AC-1: HOME encodes to \"HOME\" and decodes back")
  void homeRoundTrip() {
    UiAction home = UiAction.home();

    assertEquals("HOME", home.encode());
    assertEquals(Optional.of(new UiAction(UiAction.Type.HOME, null)), UiAction.decode(home.encode()));
  }

  @Test
  @DisplayName("AC-1: HELP encodes to \"HELP\" and decodes back")
  void helpRoundTrip() {
    UiAction help = UiAction.help();

    assertEquals("HELP", help.encode());
    assertEquals(Optional.of(new UiAction(UiAction.Type.HELP, null)), UiAction.decode(help.encode()));
  }

  @Test
  @DisplayName("AC-1: RUN encodes to \"RUN:<regionId>\" and decodes back")
  void runRoundTrip() {
    UiAction run = UiAction.run("eu-central-1");

    assertEquals("RUN:eu-central-1", run.encode());
    assertEquals(Optional.of(new UiAction(UiAction.Type.RUN, "eu-central-1")),
                 UiAction.decode(run.encode()));
  }

  @Test
  @DisplayName("AC-1: decode rejects null, blank, unknown type, HOME:x, HELP:x, RUN and RUN:")
  void decodeRejectsInvalidData() {
    assertAll(Arrays.stream(new String[]{null, "", "   ", "STOP", "home", "HOME:x", "HELP:x", "RUN", "RUN:"})
                  .map(data -> (Executable) () -> assertEquals(Optional.empty(), UiAction.decode(data),
                                                  "decode(" + data + ")")));
  }

  @Test
  @DisplayName("AC-1: encode accepts exactly 64 bytes")
  void encodeAcceptsSixtyFourBytes() {
    String regionId = "r".repeat(60);

    assertEquals("RUN:" + regionId, UiAction.run(regionId).encode());
  }

  @Test
  @DisplayName("AC-1: encode rejects data over 64 bytes")
  void encodeRejectsLongData() {
    UiAction action = UiAction.run("r".repeat(61));

    assertThrows(IllegalArgumentException.class, action::encode);
  }

  @Test
  @DisplayName("AC-1: encode counts UTF-8 bytes, not characters")
  void encodeCountsUtf8Bytes() {
    UiAction action = UiAction.run("é".repeat(31));

    assertThrows(IllegalArgumentException.class, action::encode);
  }

  @Test
  @DisplayName("AC-1: RUN requires a non-blank argument")
  void runRequiresArgument() {
    assertAll(
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction(UiAction.Type.RUN, null)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction(UiAction.Type.RUN, " ")));
  }

  @Test
  @DisplayName("AC-1: HOME and HELP must not have an argument")
  void homeAndHelpRejectArgument() {
    assertAll(
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction(UiAction.Type.HOME, "x")),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction(UiAction.Type.HELP, "x")));
  }

}
