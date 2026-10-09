package org.github.akarkin1.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiActionTest {

  private static final String TASK_ID = "0123456789abcdef0123456789abcdef";
  private static final NodeRef NODE = new NodeRef("eu-central-1", TASK_ID);

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

  @Test
  @DisplayName("2b AC-1: RUN_NEW encodes to \"RUN_NEW:<regionId>\" and decodes back, without a node reference")
  void runNewRoundTrip() {
    UiAction runNew = UiAction.runNew("eu-central-1");

    assertEquals("RUN_NEW:eu-central-1", runNew.encode());
    assertEquals(Optional.of(new UiAction(UiAction.Type.RUN_NEW, "eu-central-1")),
                 UiAction.decode(runNew.encode()));
    assertEquals(Optional.empty(), runNew.nodeRef());
  }

  @Test
  @DisplayName("2b AC-1: USE encodes to \"USE:<regionId>:<taskId>\" and decodes back with its node reference")
  void useRoundTrip() {
    assertNodeActionRoundTrip(UiAction.use(NODE), UiAction.Type.USE, "USE:");
  }

  @Test
  @DisplayName("2b AC-1: STOP encodes to \"STOP:<regionId>:<taskId>\" and decodes back with its node reference")
  void stopRoundTrip() {
    assertNodeActionRoundTrip(UiAction.stop(NODE), UiAction.Type.STOP, "STOP:");
  }

  @Test
  @DisplayName("2b AC-1: STOP_CONFIRM encodes to \"STOP_CONFIRM:<regionId>:<taskId>\" and decodes back with its node reference")
  void confirmStopRoundTrip() {
    assertNodeActionRoundTrip(UiAction.confirmStop(NODE), UiAction.Type.STOP_CONFIRM, "STOP_CONFIRM:");
  }

  @Test
  @DisplayName("2b AC-1: STOP with the {{TASK_ID}} placeholder decodes (the node agent fills it in)")
  void stopWithPlaceholderDecodes() {
    Optional<UiAction> action = UiAction.decode("STOP:eu-central-1:{{TASK_ID}}");

    assertEquals(Optional.of(new NodeRef("eu-central-1", "{{TASK_ID}}")),
                 action.flatMap(UiAction::nodeRef));
  }

  @Test
  @DisplayName("2b AC-1: STOP_CONFIRM with a 14-char region and a 32-char task id fits in 64 bytes")
  void confirmStopFitsTelegramLimit() {
    NodeRef longest = new NodeRef("ap-southeast-7", TASK_ID);

    String data = UiAction.confirmStop(longest).encode();

    assertEquals("STOP_CONFIRM:ap-southeast-7:" + TASK_ID, data);
    assertEquals(60, data.getBytes(StandardCharsets.UTF_8).length);
  }

  @Test
  @DisplayName("2b AC-1: encode keeps enforcing the 64-byte limit for node actions")
  void nodeActionOverLimitRejected() {
    UiAction action = UiAction.confirmStop(new NodeRef("ap-southeast-7", "t".repeat(40)));

    assertThrows(IllegalArgumentException.class, action::encode);
  }

  @Test
  @DisplayName("2b AC-1: RUN, HOME and HELP have no node reference")
  void otherActionsHaveNoNodeRef() {
    assertAll(
        () -> assertEquals(Optional.empty(), UiAction.run("eu-central-1").nodeRef()),
        () -> assertEquals(Optional.empty(), UiAction.home().nodeRef()),
        () -> assertEquals(Optional.empty(), UiAction.help().nodeRef()));
  }

  @Test
  @DisplayName("2b AC-1: decode rejects new types with missing or invalid arguments")
  void decodeRejectsInvalidNewTypes() {
    String[] invalid = {"RUN_NEW", "RUN_NEW:", "RUN_NEW: ", "USE", "USE:", "USE:eu-central-1",
                        "STOP", "STOP:eu-central-1", "STOP:eu-central-1:", "STOP::" + TASK_ID,
                        "STOP:a:b:c", "STOP_CONFIRM", "STOP_CONFIRM:eu-central-1",
                        "STOP_CONFIRM: :" + TASK_ID, "stop:eu-central-1:" + TASK_ID};
    assertAll(Arrays.stream(invalid)
                  .map(data -> (Executable) () -> assertEquals(Optional.empty(), UiAction.decode(data),
                                                  "decode(" + data + ")")));
  }

  @Test
  @DisplayName("2b AC-1: the constructor rejects new types with missing or invalid arguments")
  void constructorRejectsInvalidNewTypes() {
    assertAll(
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction(UiAction.Type.RUN_NEW, null)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction(UiAction.Type.RUN_NEW, " ")),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction(UiAction.Type.USE, null)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction(UiAction.Type.STOP, "eu-central-1")),
        () -> assertThrows(IllegalArgumentException.class,
                           () -> new UiAction(UiAction.Type.STOP_CONFIRM, "eu-central-1:")));
  }

  private static void assertNodeActionRoundTrip(UiAction action, UiAction.Type type, String prefix) {
    String data = action.encode();

    assertEquals(prefix + "eu-central-1:" + TASK_ID, data);
    Optional<UiAction> decoded = UiAction.decode(data);
    assertEquals(Optional.of(new UiAction(type, "eu-central-1:" + TASK_ID)), decoded);
    assertEquals(Optional.of(NODE), decoded.flatMap(UiAction::nodeRef));
    assertEquals(Optional.of(NODE), action.nodeRef());
  }

}
