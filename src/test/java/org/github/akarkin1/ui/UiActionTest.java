package org.github.akarkin1.ui;

import org.github.akarkin1.auth.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  // --- Phase 4: access requests and the Users screen (§4.5) ----------------------------------

  private static final String USERNAME_32 = "u".repeat(32);

  @Test
  @DisplayName("4 AC-8: REQUEST_ACCESS encodes to \"REQUEST_ACCESS\" and decodes back")
  void requestAccessRoundTrip() {
    UiAction action = UiAction.requestAccess();

    assertEquals("REQUEST_ACCESS", action.encode());
    assertEquals(UiAction.Type.REQUEST_ACCESS, action.type());
    assertEquals(Optional.of(action), UiAction.decode(action.encode()));
  }

  @Test
  @DisplayName("4 AC-8: ACCESS_TOGGLE encodes to \"ACCESS_TOGGLE:<id>:<mask>:<ROLE>\" and decodes back with id, mask and role")
  void accessToggleRoundTrip() {
    UiAction action = UiAction.accessToggle(123L, 5, UserRole.READ_ONLY);

    assertEquals("ACCESS_TOGGLE:123:5:READ_ONLY", action.encode());
    Optional<UiAction> decoded = UiAction.decode(action.encode());
    assertEquals(Optional.of(action), decoded);
    UiAction restored = decoded.orElseThrow();
    assertEquals(UiAction.Type.ACCESS_TOGGLE, restored.type());
    assertEquals(Optional.of(123L), restored.userId());
    assertEquals(OptionalInt.of(5), restored.roleMask());
    assertEquals(Optional.of(UserRole.READ_ONLY), restored.role());
  }

  @Test
  @DisplayName("4 AC-8: ACCESS_GRANT encodes to \"ACCESS_GRANT:<id>:<mask>\" and decodes back with id and mask, no role")
  void accessGrantRoundTrip() {
    UiAction action = UiAction.accessGrant(123L, 3);

    assertEquals("ACCESS_GRANT:123:3", action.encode());
    UiAction restored = UiAction.decode(action.encode()).orElseThrow();
    assertEquals(action, restored);
    assertEquals(Optional.of(123L), restored.userId());
    assertEquals(OptionalInt.of(3), restored.roleMask());
    assertEquals(Optional.empty(), restored.role());
  }

  @Test
  @DisplayName("4 AC-8: ACCESS_GRANT with mask 0 round-trips (the router ignores it)")
  void accessGrantZeroMaskRoundTrip() {
    UiAction action = UiAction.accessGrant(123L, 0);

    assertEquals("ACCESS_GRANT:123:0", action.encode());
    assertEquals(OptionalInt.of(0), UiAction.decode(action.encode()).orElseThrow().roleMask());
  }

  @Test
  @DisplayName("4 AC-8: ACCESS_DECLINE encodes to \"ACCESS_DECLINE:<id>\" and decodes back with the id, no mask")
  void accessDeclineRoundTrip() {
    UiAction action = UiAction.accessDecline(123L);

    assertEquals("ACCESS_DECLINE:123", action.encode());
    UiAction restored = UiAction.decode(action.encode()).orElseThrow();
    assertEquals(action, restored);
    assertEquals(Optional.of(123L), restored.userId());
    assertEquals(OptionalInt.empty(), restored.roleMask());
    assertEquals(Optional.empty(), restored.role());
  }

  @Test
  @DisplayName("4 AC-8: USERS encodes to \"USERS\" and decodes back")
  void usersRoundTrip() {
    UiAction action = UiAction.users();

    assertEquals("USERS", action.encode());
    assertEquals(Optional.of(action), UiAction.decode(action.encode()));
  }

  @Test
  @DisplayName("4 AC-8: USER encodes to \"USER:<key>\" for an id and for @username and decodes back with the key")
  void userRoundTrip() {
    for (String key : List.of("123", "@carol")) {
      UiAction action = UiAction.user(key);

      assertEquals("USER:" + key, action.encode());
      UiAction restored = UiAction.decode(action.encode()).orElseThrow();
      assertEquals(action, restored);
      assertEquals(UiAction.Type.USER, restored.type());
      assertEquals(Optional.of(key), restored.userKey());
    }
  }

  @Test
  @DisplayName("4 AC-8: USER_ROLE encodes to \"USER_ROLE:<key>:<ROLE>\" and decodes back with key and role")
  void toggleRoleRoundTrip() {
    for (String key : List.of("123", "@carol")) {
      for (UserRole role : UserRole.values()) {
        UiAction action = UiAction.toggleRole(key, role);

        assertEquals("USER_ROLE:" + key + ":" + role.name(), action.encode());
        UiAction restored = UiAction.decode(action.encode()).orElseThrow();
        assertEquals(action, restored);
        assertEquals(UiAction.Type.USER_ROLE, restored.type());
        assertEquals(Optional.of(key), restored.userKey());
        assertEquals(Optional.of(role), restored.role());
      }
    }
  }

  @Test
  @DisplayName("4 AC-8: USER_DEL encodes to \"USER_DEL:<key>\" and decodes back with the key")
  void deleteUserRoundTrip() {
    UiAction action = UiAction.deleteUser("@carol");

    assertEquals("USER_DEL:@carol", action.encode());
    UiAction restored = UiAction.decode(action.encode()).orElseThrow();
    assertEquals(action, restored);
    assertEquals(UiAction.Type.USER_DEL, restored.type());
    assertEquals(Optional.of("@carol"), restored.userKey());
  }

  @Test
  @DisplayName("4 AC-8: USER_DEL_CONFIRM encodes to \"USER_DEL_CONFIRM:<key>\" and decodes back with the key")
  void confirmDeleteUserRoundTrip() {
    UiAction action = UiAction.confirmDeleteUser("123");

    assertEquals("USER_DEL_CONFIRM:123", action.encode());
    UiAction restored = UiAction.decode(action.encode()).orElseThrow();
    assertEquals(action, restored);
    assertEquals(UiAction.Type.USER_DEL_CONFIRM, restored.type());
    assertEquals(Optional.of("123"), restored.userKey());
  }

  @Test
  @DisplayName("4 AC-7, §4.5: the longest new callbacks fit: USER_ROLE:@<32 chars>:NODE_ADMIN = 54, ACCESS_TOGGLE:9999999999:7:USER_ADMIN = 37 bytes")
  void longestNewCallbacksFit() {
    String toggleRole = UiAction.toggleRole("@" + USERNAME_32, UserRole.NODE_ADMIN).encode();
    String accessToggle = UiAction.accessToggle(9_999_999_999L, 7, UserRole.USER_ADMIN).encode();

    assertEquals(54, toggleRole.getBytes(StandardCharsets.UTF_8).length);
    assertEquals("ACCESS_TOGGLE:9999999999:7:USER_ADMIN", accessToggle);
    assertEquals(37, accessToggle.getBytes(StandardCharsets.UTF_8).length);
    assertEquals(Optional.of(9_999_999_999L),
                 UiAction.decode(accessToggle).flatMap(UiAction::userId));
  }

  @Test
  @DisplayName("4 AC-7: every new action with a 10-digit id or a 32-char username stays under 64 bytes and round-trips")
  void newActionsUnderLimit() {
    long id = 9_999_999_999L;
    String usernameKey = "@" + USERNAME_32;
    List<UiAction> actions = List.of(
        UiAction.requestAccess(),
        UiAction.accessToggle(id, 7, UserRole.NODE_ADMIN),
        UiAction.accessToggle(id, 7, UserRole.READ_ONLY),
        UiAction.accessToggle(id, 7, UserRole.USER_ADMIN),
        UiAction.accessGrant(id, 7),
        UiAction.accessDecline(id),
        UiAction.users(),
        UiAction.user(usernameKey),
        UiAction.user(String.valueOf(id)),
        UiAction.toggleRole(usernameKey, UserRole.NODE_ADMIN),
        UiAction.toggleRole(usernameKey, UserRole.READ_ONLY),
        UiAction.toggleRole(usernameKey, UserRole.USER_ADMIN),
        UiAction.deleteUser(usernameKey),
        UiAction.confirmDeleteUser(usernameKey));

    for (UiAction action : actions) {
      String data = action.encode();
      assertTrue(data.getBytes(StandardCharsets.UTF_8).length < 64, data);
      assertEquals(Optional.of(action), UiAction.decode(data), data);
    }
  }

  @Test
  @DisplayName("4 AC-8: the typed accessors are empty for actions without that part")
  void accessorsEmptyForOtherTypes() {
    assertAll(
        () -> assertEquals(Optional.empty(), UiAction.home().userId()),
        () -> assertEquals(OptionalInt.empty(), UiAction.home().roleMask()),
        () -> assertEquals(Optional.empty(), UiAction.home().role()),
        () -> assertEquals(Optional.empty(), UiAction.home().userKey()),
        () -> assertEquals(Optional.empty(), UiAction.run("eu-central-1").userId()),
        () -> assertEquals(Optional.empty(), UiAction.users().userKey()),
        () -> assertEquals(Optional.empty(), UiAction.requestAccess().userId()),
        () -> assertEquals(Optional.empty(), UiAction.user("123").role()),
        () -> assertEquals(OptionalInt.empty(), UiAction.toggleRole("123", UserRole.READ_ONLY).roleMask()),
        () -> assertEquals(Optional.empty(), UiAction.stop(NODE).userId()));
  }

  @Test
  @DisplayName("4 AC-8: decode rejects the new types with missing or invalid arguments")
  void decodeRejectsInvalidPhase4Types() {
    String[] invalid = {
        "REQUEST_ACCESS:x",
        "ACCESS_TOGGLE", "ACCESS_TOGGLE:", "ACCESS_TOGGLE:123", "ACCESS_TOGGLE:123:1",
        "ACCESS_TOGGLE:abc:1:NODE_ADMIN", "ACCESS_TOGGLE:123:x:NODE_ADMIN",
        "ACCESS_TOGGLE:123:1:SUPERUSER", "ACCESS_TOGGLE:123:1:node_admin",
        "ACCESS_TOGGLE:123:1:NODE_ADMIN:x",
        "ACCESS_GRANT", "ACCESS_GRANT:123", "ACCESS_GRANT:abc:1", "ACCESS_GRANT:123:x",
        "ACCESS_DECLINE", "ACCESS_DECLINE:", "ACCESS_DECLINE:abc",
        "USERS:x",
        "USER", "USER:", "USER: ",
        "USER_ROLE", "USER_ROLE:123", "USER_ROLE:123:", "USER_ROLE:123:ROOT", "USER_ROLE::NODE_ADMIN",
        "USER_DEL", "USER_DEL:",
        "USER_DEL_CONFIRM", "USER_DEL_CONFIRM:",
        "users", "user:123"};
    assertAll(Arrays.stream(invalid)
                  .map(data -> (Executable) () -> assertEquals(Optional.empty(), UiAction.decode(data),
                                                  "decode(" + data + ")")));
  }

  @Test
  @DisplayName("4 AC-8: the constructor rejects the new types with invalid arguments")
  void constructorRejectsInvalidPhase4Types() {
    assertAll(
        () -> assertThrows(IllegalArgumentException.class,
                           () -> new UiAction(UiAction.Type.REQUEST_ACCESS, "x")),
        () -> assertThrows(IllegalArgumentException.class,
                           () -> new UiAction(UiAction.Type.USERS, "x")),
        () -> assertThrows(IllegalArgumentException.class,
                           () -> new UiAction(UiAction.Type.USER, null)),
        () -> assertThrows(IllegalArgumentException.class,
                           () -> new UiAction(UiAction.Type.ACCESS_DECLINE, "abc")),
        () -> assertThrows(IllegalArgumentException.class,
                           () -> new UiAction(UiAction.Type.ACCESS_GRANT, "123")),
        () -> assertThrows(IllegalArgumentException.class,
                           () -> new UiAction(UiAction.Type.USER_ROLE, "123:ROOT")));
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
