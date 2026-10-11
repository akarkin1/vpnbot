package org.github.akarkin1.ui;

import org.github.akarkin1.auth.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    assertEquals(Optional.of(new UiAction.Home()), UiAction.decode(home.encode()));
  }

  @Test
  @DisplayName("AC-1: HELP encodes to \"HELP\" and decodes back")
  void helpRoundTrip() {
    UiAction help = UiAction.help();

    assertEquals("HELP", help.encode());
    assertEquals(Optional.of(new UiAction.Help()), UiAction.decode(help.encode()));
  }

  @Test
  @DisplayName("AC-1: RUN encodes to \"RUN:<regionId>\" and decodes back")
  void runRoundTrip() {
    UiAction run = UiAction.run("eu-central-1");

    assertEquals("RUN:eu-central-1", run.encode());
    assertEquals(Optional.of(new UiAction.Run("eu-central-1")), UiAction.decode(run.encode()));
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
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.Run(null)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.Run(" ")));
  }

  @Test
  @DisplayName("AC-1: HOME and HELP must not have an argument")
  void homeAndHelpRejectArgument() {
    assertAll(
        () -> assertEquals(Optional.empty(), UiAction.Home.parse("x")),
        () -> assertEquals(Optional.empty(), UiAction.Help.parse("x")));
  }

  @Test
  @DisplayName("2b AC-1: RUN_NEW encodes to \"RUN_NEW:<regionId>\" and decodes back")
  void runNewRoundTrip() {
    UiAction runNew = UiAction.runNew("eu-central-1");

    assertEquals("RUN_NEW:eu-central-1", runNew.encode());
    assertEquals(Optional.of(new UiAction.RunNew("eu-central-1")), UiAction.decode(runNew.encode()));
  }

  @Test
  @DisplayName("2b AC-1: USE encodes to \"USE:<regionId>:<taskId>\" and decodes back with its node reference")
  void useRoundTrip() {
    assertNodeActionRoundTrip(UiAction.use(NODE), "USE:");
  }

  @Test
  @DisplayName("2b AC-1: STOP encodes to \"STOP:<regionId>:<taskId>\" and decodes back with its node reference")
  void stopRoundTrip() {
    assertNodeActionRoundTrip(UiAction.stop(NODE), "STOP:");
  }

  @Test
  @DisplayName("2b AC-1: STOP_CONFIRM encodes to \"STOP_CONFIRM:<regionId>:<taskId>\" and decodes back with its node reference")
  void confirmStopRoundTrip() {
    assertNodeActionRoundTrip(UiAction.confirmStop(NODE), "STOP_CONFIRM:");
  }

  @Test
  @DisplayName("2b AC-1: STOP with the {{TASK_ID}} placeholder decodes (the node agent fills it in)")
  void stopWithPlaceholderDecodes() {
    Optional<UiAction> action = UiAction.decode("STOP:eu-central-1:{{TASK_ID}}");

    assertEquals(Optional.of(new UiAction.Stop(new NodeRef("eu-central-1", "{{TASK_ID}}"))), action);
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
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.RunNew(null)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.RunNew(" ")),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.Use(null)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.Stop(null)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.ConfirmStop(null)));
  }

  // --- Phase 4: access requests and the Users screen (§4.5) ----------------------------------

  private static final String USERNAME_32 = "u".repeat(32);
  private static final Set<UserRole> ALL_ROLES = EnumSet.allOf(UserRole.class);

  @Test
  @DisplayName("4 AC-8: REQUEST_ACCESS encodes to \"REQUEST_ACCESS\" and decodes back")
  void requestAccessRoundTrip() {
    UiAction action = UiAction.requestAccess();

    assertEquals("REQUEST_ACCESS", action.encode());
    assertEquals(Optional.of(new UiAction.RequestAccess()), UiAction.decode(action.encode()));
  }

  @Test
  @DisplayName("4 AC-8: ACCESS_TOGGLE encodes to \"ACCESS_TOGGLE:<id>:<mask>:<ROLE>\" and decodes back with id, roles and role")
  void accessToggleRoundTrip() {
    UiAction action = UiAction.accessToggle(123L, Set.of(UserRole.NODE_ADMIN, UserRole.USER_ADMIN),
                                            UserRole.READ_ONLY);

    assertEquals("ACCESS_TOGGLE:123:5:READ_ONLY", action.encode());
    Optional<UiAction> decoded = UiAction.decode(action.encode());
    assertEquals(Optional.of(action), decoded);
    UiAction.AccessToggle restored = (UiAction.AccessToggle) decoded.orElseThrow();
    assertEquals(123L, restored.userId());
    assertEquals(Set.of(UserRole.NODE_ADMIN, UserRole.USER_ADMIN), restored.selected());
    assertEquals(UserRole.READ_ONLY, restored.role());
  }

  @Test
  @DisplayName("4 AC-8: ACCESS_GRANT encodes to \"ACCESS_GRANT:<id>:<mask>\" and decodes back with id and roles")
  void accessGrantRoundTrip() {
    UiAction action = UiAction.accessGrant(123L, Set.of(UserRole.NODE_ADMIN, UserRole.READ_ONLY));

    assertEquals("ACCESS_GRANT:123:3", action.encode());
    UiAction.AccessGrant restored = (UiAction.AccessGrant) UiAction.decode(action.encode()).orElseThrow();
    assertEquals(action, restored);
    assertEquals(123L, restored.userId());
    assertEquals(Set.of(UserRole.NODE_ADMIN, UserRole.READ_ONLY), restored.selected());
  }

  @Test
  @DisplayName("4 AC-8: ACCESS_GRANT with no role round-trips as mask 0 (the router ignores it)")
  void accessGrantZeroMaskRoundTrip() {
    UiAction action = UiAction.accessGrant(123L, Set.of());

    assertEquals("ACCESS_GRANT:123:0", action.encode());
    assertEquals(Optional.of(new UiAction.AccessGrant(123L, Set.of())), UiAction.decode(action.encode()));
  }

  @Test
  @DisplayName("4 AC-8: ACCESS_DECLINE encodes to \"ACCESS_DECLINE:<id>\" and decodes back with the id")
  void accessDeclineRoundTrip() {
    UiAction action = UiAction.accessDecline(123L);

    assertEquals("ACCESS_DECLINE:123", action.encode());
    assertEquals(Optional.of(new UiAction.AccessDecline(123L)), UiAction.decode(action.encode()));
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
      assertEquals(Optional.of(new UiAction.ShowUser(key)), UiAction.decode(action.encode()));
    }
  }

  @Test
  @DisplayName("4 AC-8: USER_ROLE encodes to \"USER_ROLE:<key>:<ROLE>\" and decodes back with key and role")
  void toggleRoleRoundTrip() {
    for (String key : List.of("123", "@carol")) {
      for (UserRole role : UserRole.values()) {
        UiAction action = UiAction.toggleRole(key, role);

        assertEquals("USER_ROLE:" + key + ":" + role.name(), action.encode());
        assertEquals(Optional.of(new UiAction.ToggleRole(key, role)), UiAction.decode(action.encode()));
      }
    }
  }

  @Test
  @DisplayName("4 AC-8: USER_DEL encodes to \"USER_DEL:<key>\" and decodes back with the key")
  void deleteUserRoundTrip() {
    UiAction action = UiAction.deleteUser("@carol");

    assertEquals("USER_DEL:@carol", action.encode());
    assertEquals(Optional.of(new UiAction.DeleteUser("@carol")), UiAction.decode(action.encode()));
  }

  @Test
  @DisplayName("4 AC-8: USER_DEL_CONFIRM encodes to \"USER_DEL_CONFIRM:<key>\" and decodes back with the key")
  void confirmDeleteUserRoundTrip() {
    UiAction action = UiAction.confirmDeleteUser("123");

    assertEquals("USER_DEL_CONFIRM:123", action.encode());
    assertEquals(Optional.of(new UiAction.ConfirmDeleteUser("123")), UiAction.decode(action.encode()));
  }

  @Test
  @DisplayName("4 AC-7, §4.5: the longest new callbacks fit: USER_ROLE:@<32 chars>:NODE_ADMIN = 54, ACCESS_TOGGLE:9999999999:7:USER_ADMIN = 37 bytes")
  void longestNewCallbacksFit() {
    String toggleRole = UiAction.toggleRole("@" + USERNAME_32, UserRole.NODE_ADMIN).encode();
    String accessToggle = UiAction.accessToggle(9_999_999_999L, ALL_ROLES, UserRole.USER_ADMIN).encode();

    assertEquals(54, toggleRole.getBytes(StandardCharsets.UTF_8).length);
    assertEquals("ACCESS_TOGGLE:9999999999:7:USER_ADMIN", accessToggle);
    assertEquals(37, accessToggle.getBytes(StandardCharsets.UTF_8).length);
    assertEquals(Optional.of(new UiAction.AccessToggle(9_999_999_999L, ALL_ROLES, UserRole.USER_ADMIN)),
                 UiAction.decode(accessToggle));
  }

  @Test
  @DisplayName("4 AC-7: every new action with a 10-digit id or a 32-char username stays under 64 bytes and round-trips")
  void newActionsUnderLimit() {
    long id = 9_999_999_999L;
    String usernameKey = "@" + USERNAME_32;
    List<UiAction> actions = List.of(
        UiAction.requestAccess(),
        UiAction.accessToggle(id, ALL_ROLES, UserRole.NODE_ADMIN),
        UiAction.accessToggle(id, ALL_ROLES, UserRole.READ_ONLY),
        UiAction.accessToggle(id, ALL_ROLES, UserRole.USER_ADMIN),
        UiAction.accessGrant(id, ALL_ROLES),
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
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.ShowUser(null)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.ShowUser("alice")),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.AccessDecline(0)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.AccessGrant(123, null)),
        () -> assertThrows(IllegalArgumentException.class,
                           () -> new UiAction.ToggleRole("123", null)));
  }

  private static void assertNodeActionRoundTrip(UiAction action, String prefix) {
    String data = action.encode();

    assertEquals(prefix + "eu-central-1:" + TASK_ID, data);
    assertEquals(Optional.of(action), UiAction.decode(data));
  }

}
