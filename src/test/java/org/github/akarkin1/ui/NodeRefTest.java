package org.github.akarkin1.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NodeRefTest {

  private static final String TASK_ID = "0123456789abcdef0123456789abcdef";

  @Test
  @DisplayName("2b AC-1: encode is \"<regionId>:<taskId>\" and parse reads it back")
  void roundTrip() {
    NodeRef node = new NodeRef("eu-central-1", TASK_ID);

    assertEquals("eu-central-1:" + TASK_ID, node.encode());
    assertEquals(Optional.of(node), NodeRef.parse(node.encode()));
  }

  @Test
  @DisplayName("2b AC-1: parse accepts the {{TASK_ID}} placeholder as task id")
  void parseAcceptsPlaceholder() {
    assertEquals(Optional.of(new NodeRef("eu-central-1", "{{TASK_ID}}")),
                 NodeRef.parse("eu-central-1:{{TASK_ID}}"));
  }

  @Test
  @DisplayName("2b AC-1: parse rejects null, blank, missing or extra parts")
  void parseRejectsInvalidValues() {
    String[] invalid = {null, "", "  ", "eu-central-1", "eu-central-1:", ":" + TASK_ID, ":",
                        " :" + TASK_ID, "eu-central-1: ", "eu-central-1:a:b"};
    assertAll(Arrays.stream(invalid)
                  .map(value -> (Executable) () -> assertEquals(Optional.empty(), NodeRef.parse(value),
                                                                "parse(" + value + ")")));
  }

  @Test
  @DisplayName("2b AC-1: the constructor rejects null, blank and ':'-containing parts")
  void constructorRejectsInvalidParts() {
    assertAll(
        () -> assertThrows(IllegalArgumentException.class, () -> new NodeRef(null, TASK_ID)),
        () -> assertThrows(IllegalArgumentException.class, () -> new NodeRef(" ", TASK_ID)),
        () -> assertThrows(IllegalArgumentException.class, () -> new NodeRef("eu-central-1", null)),
        () -> assertThrows(IllegalArgumentException.class, () -> new NodeRef("eu-central-1", "")),
        () -> assertThrows(IllegalArgumentException.class, () -> new NodeRef("eu:central", TASK_ID)),
        () -> assertThrows(IllegalArgumentException.class, () -> new NodeRef("eu-central-1", "a:b")));
  }

}
