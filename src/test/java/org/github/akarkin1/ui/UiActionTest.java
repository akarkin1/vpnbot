package org.github.akarkin1.ui;

import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.auth.RoleSet;
import org.github.akarkin1.auth.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.github.akarkin1.auth.UserRole.NODE_ADMIN;
import static org.github.akarkin1.auth.UserRole.READ_ONLY;
import static org.github.akarkin1.auth.UserRole.USER_ADMIN;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * cleanup §4.4, AC-6, D-6: one record per button action, each with its own encoding; the wire
 * format (tag names included) is the Phase 4 one, so buttons sent before the deploy keep working.
 * JUnit's params module is not on the classpath, so the table tests run their rows in
 * {@code assertAll}.
 */
class UiActionTest {

  private static final String TASK_ID = "0123456789abcdef0123456789abcdef";
  private static final NodeRef NODE = new NodeRef("eu-central-1", TASK_ID);
  private static final String USERNAME_32 = "u".repeat(32);

  /** A record and its callback data, as Phase 4 encoded the same inputs. */
  private record Row(UiAction action, String data) {
  }

  /** At least one row per record of {@link UiAction}; {@link #tableCoversEveryRecord()} checks it. */
  private static final List<Row> WIRE_FORMAT = List.of(
      new Row(new UiAction.Home(), "HOME"),
      new Row(new UiAction.Help(), "HELP"),
      new Row(new UiAction.Run("eu-central-1"), "RUN:eu-central-1"),
      new Row(new UiAction.RunNew("eu-central-1"), "RUN_NEW:eu-central-1"),
      new Row(new UiAction.Use(NODE), "USE:eu-central-1:" + TASK_ID),
      new Row(new UiAction.Stop(NODE), "STOP:eu-central-1:" + TASK_ID),
      new Row(new UiAction.ConfirmStop(NODE), "STOP_CONFIRM:eu-central-1:" + TASK_ID),
      new Row(new UiAction.RequestAccess(), "REQUEST_ACCESS"),
      new Row(new UiAction.AccessToggle(123L, Set.of(NODE_ADMIN, USER_ADMIN), NODE_ADMIN),
              "ACCESS_TOGGLE:123:5:NODE_ADMIN"),
      new Row(new UiAction.AccessToggle(123L, Set.of(), READ_ONLY), "ACCESS_TOGGLE:123:0:READ_ONLY"),
      new Row(new UiAction.AccessToggle(123L, EnumSet.allOf(UserRole.class), USER_ADMIN),
              "ACCESS_TOGGLE:123:7:USER_ADMIN"),
      new Row(new UiAction.AccessGrant(123L, Set.of()), "ACCESS_GRANT:123:0"),
      new Row(new UiAction.AccessGrant(123L, Set.of(NODE_ADMIN, READ_ONLY)), "ACCESS_GRANT:123:3"),
      new Row(new UiAction.AccessDecline(123L), "ACCESS_DECLINE:123"),
      new Row(new UiAction.Users(), "USERS"),
      new Row(new UiAction.ShowUser("123"), "USER:123"),
      new Row(new UiAction.ShowUser("@alice"), "USER:@alice"),
      new Row(new UiAction.ToggleRole("123", READ_ONLY), "USER_ROLE:123:READ_ONLY"),
      new Row(new UiAction.ToggleRole("@alice", NODE_ADMIN), "USER_ROLE:@alice:NODE_ADMIN"),
      new Row(new UiAction.DeleteUser("@alice"), "USER_DEL:@alice"),
      new Row(new UiAction.DeleteUser("123"), "USER_DEL:123"),
      new Row(new UiAction.ConfirmDeleteUser("123"), "USER_DEL_CONFIRM:123"),
      new Row(new UiAction.ConfirmDeleteUser("@alice"), "USER_DEL_CONFIRM:@alice"),
      new Row(new UiAction.Regions(), "REGIONS"),
      new Row(new UiAction.RegionRequestAction("eu-central-1"), "REGION_REQ:eu-central-1"),
      new Row(new UiAction.RegionDeployed("eu-central-1"), "REGION_DONE:eu-central-1"),
      new Row(new UiAction.RegionDecline("eu-central-1"), "REGION_DECLINE:eu-central-1"));

  // --- the record set ------------------------------------------------------------------------

  @Test
  @DisplayName("cleanup §4.4: UiAction is a sealed interface whose permitted subclasses are records")
  void sealedInterfaceOfRecords() {
    assertTrue(UiAction.class.isInterface(), "UiAction is an interface");
    assertTrue(UiAction.class.isSealed(), "UiAction is sealed");
    assertAll(Arrays.stream(UiAction.class.getPermittedSubclasses())
                  .map(type -> (Executable) () -> assertTrue(type.isRecord(), type + " is a record")));
  }

  @Test
  @DisplayName("cleanup AC-6: the wire-format table has a row for every record of UiAction")
  void tableCoversEveryRecord() {
    Set<Class<?>> permitted = Set.of(UiAction.class.getPermittedSubclasses());
    Set<Class<?>> covered = WIRE_FORMAT.stream().map(row -> row.action().getClass())
        .collect(Collectors.toSet());

    assertEquals(20, permitted.size(), "the 16 records of §4.4 and the 4 of region requests");
    assertEquals(permitted, covered);
  }

  // --- encode / decode -----------------------------------------------------------------------

  @Test
  @DisplayName("cleanup AC-6, D-6: every record encodes to the Phase 4 string for the same inputs")
  void encodeMatchesPhase4WireFormat() {
    assertAll(WIRE_FORMAT.stream()
                  .map(row -> (Executable) () -> assertEquals(row.data(), row.action().encode(),
                                                              row.action().toString())));
  }

  @Test
  @DisplayName("cleanup AC-6: every record round-trips encode → decode to an equal record")
  void everyRecordRoundTrips() {
    assertAll(WIRE_FORMAT.stream()
                  .map(row -> (Executable) () -> assertEquals(Optional.of(row.action()),
                                                              UiAction.decode(row.action().encode()),
                                                              row.data())));
  }

  @Test
  @DisplayName("cleanup AC-6, D-6: the Phase 4 strings decode to the typed records (buttons sent before the deploy keep working)")
  void phase4StringsDecode() {
    assertAll(WIRE_FORMAT.stream()
                  .map(row -> (Executable) () -> assertEquals(Optional.of(row.action()),
                                                              UiAction.decode(row.data()), row.data())));
  }

  @Test
  @DisplayName("cleanup AC-6: decode yields the typed components (USE with a 32-hex task id, ACCESS_TOGGLE with the set of its mask, USER with a pending @alice key)")
  void decodeYieldsTypedComponents() {
    UiAction use = UiAction.decode("USE:eu-central-1:" + TASK_ID).orElseThrow();
    UiAction toggle = UiAction.decode("ACCESS_TOGGLE:123:5:READ_ONLY").orElseThrow();
    UiAction user = UiAction.decode("USER:@alice").orElseThrow();

    assertEquals(NODE, assertInstanceOf(UiAction.Use.class, use).node());
    UiAction.AccessToggle accessToggle = assertInstanceOf(UiAction.AccessToggle.class, toggle);
    assertEquals(123L, accessToggle.userId());
    assertEquals(Set.of(NODE_ADMIN, USER_ADMIN), accessToggle.selected());
    assertEquals(READ_ONLY, accessToggle.role());
    assertEquals("@alice", assertInstanceOf(UiAction.ShowUser.class, user).key());
  }

  @Test
  @DisplayName("cleanup AC-6: ACCESS_GRANT and ACCESS_TOGGLE round-trip every role set; the mask is RoleSet.encode(selected)")
  void everyRoleSetRoundTrips() {
    List<Executable> checks = new ArrayList<>();
    for (int mask = 0; mask <= RoleSet.ALL; mask++) {
      Set<UserRole> selected = RoleSet.decode(mask);
      UiAction grant = new UiAction.AccessGrant(123L, selected);
      String grantData = "ACCESS_GRANT:123:" + mask;
      checks.add(() -> assertEquals(grantData, grant.encode()));
      checks.add(() -> assertEquals(Optional.of(grant), UiAction.decode(grantData), grantData));
      for (UserRole role : UserRole.values()) {
        UiAction toggle = new UiAction.AccessToggle(123L, selected, role);
        String toggleData = "ACCESS_TOGGLE:123:" + mask + ":" + role.name();
        checks.add(() -> assertEquals(toggleData, toggle.encode()));
        checks.add(() -> assertEquals(Optional.of(toggle), UiAction.decode(toggleData), toggleData));
      }
    }
    assertAll(checks);
  }

  @Test
  @DisplayName("cleanup AC-6: USER_ROLE round-trips every role for an id and for a pending @username key")
  void toggleRoleRoundTripsEveryRole() {
    List<Executable> checks = new ArrayList<>();
    for (String key : List.of("123", "@alice")) {
      for (UserRole role : UserRole.values()) {
        UiAction action = new UiAction.ToggleRole(key, role);
        String data = "USER_ROLE:" + key + ":" + role.name();
        checks.add(() -> assertEquals(data, action.encode()));
        checks.add(() -> assertEquals(Optional.of(action), UiAction.decode(data), data));
      }
    }
    assertAll(checks);
  }

  @Test
  @DisplayName("2b AC-1, cleanup AC-6: STOP with the {{TASK_ID}} placeholder decodes (the node agent fills it in)")
  void stopWithPlaceholderDecodes() {
    assertEquals(Optional.of(new UiAction.Stop(new NodeRef("eu-central-1", "{{TASK_ID}}"))),
                 UiAction.decode("STOP:eu-central-1:{{TASK_ID}}"));
  }

  // --- static factories ----------------------------------------------------------------------

  @Test
  @DisplayName("cleanup §4.4: the static factories on the interface return the records")
  void factoriesReturnRecords() {
    assertAll(
        () -> assertEquals(new UiAction.Home(), UiAction.home()),
        () -> assertEquals(new UiAction.Help(), UiAction.help()),
        () -> assertEquals(new UiAction.Run("eu-central-1"), UiAction.run("eu-central-1")),
        () -> assertEquals(new UiAction.RunNew("eu-central-1"), UiAction.runNew("eu-central-1")),
        () -> assertEquals(new UiAction.Use(NODE), UiAction.use(NODE)),
        () -> assertEquals(new UiAction.Stop(NODE), UiAction.stop(NODE)),
        () -> assertEquals(new UiAction.ConfirmStop(NODE), UiAction.confirmStop(NODE)),
        () -> assertEquals(new UiAction.RequestAccess(), UiAction.requestAccess()),
        () -> assertEquals(new UiAction.AccessToggle(123L, Set.of(NODE_ADMIN, USER_ADMIN), NODE_ADMIN),
                           UiAction.accessToggle(123L, Set.of(NODE_ADMIN, USER_ADMIN), NODE_ADMIN)),
        () -> assertEquals(new UiAction.AccessGrant(123L, Set.of(READ_ONLY)),
                           UiAction.accessGrant(123L, Set.of(READ_ONLY))),
        () -> assertEquals(new UiAction.AccessDecline(123L), UiAction.accessDecline(123L)),
        () -> assertEquals(new UiAction.Users(), UiAction.users()),
        () -> assertEquals(new UiAction.ShowUser("@alice"), UiAction.user("@alice")),
        () -> assertEquals(new UiAction.ToggleRole("123", READ_ONLY), UiAction.toggleRole("123", READ_ONLY)),
        () -> assertEquals(new UiAction.DeleteUser("@alice"), UiAction.deleteUser("@alice")),
        () -> assertEquals(new UiAction.ConfirmDeleteUser("123"), UiAction.confirmDeleteUser("123")));
  }

  @Test
  @DisplayName("cleanup AC-6, 4 D-22: accessGrant(123, Set.of()) encodes to ACCESS_GRANT:123:0 (the router ignores it)")
  void accessGrantEmptySet() {
    assertEquals("ACCESS_GRANT:123:0", UiAction.accessGrant(123L, Set.of()).encode());
  }

  @Test
  @DisplayName("cleanup AC-6: the factories encode to the Phase 4 strings")
  void factoriesEncodeToPhase4Strings() {
    assertAll(
        () -> assertEquals("HOME", UiAction.home().encode()),
        () -> assertEquals("RUN:eu-central-1", UiAction.run("eu-central-1").encode()),
        () -> assertEquals("USE:" + NODE.encode(), UiAction.use(NODE).encode()),
        () -> assertEquals("ACCESS_TOGGLE:123:5:NODE_ADMIN",
                           UiAction.accessToggle(123L, Set.of(NODE_ADMIN, USER_ADMIN), NODE_ADMIN).encode()),
        () -> assertEquals("ACCESS_DECLINE:123", UiAction.accessDecline(123L).encode()),
        () -> assertEquals("USER:123", UiAction.user("123").encode()),
        () -> assertEquals("USER:@alice", UiAction.user("@alice").encode()),
        () -> assertEquals("USER_ROLE:123:READ_ONLY", UiAction.toggleRole("123", READ_ONLY).encode()),
        () -> assertEquals("USER_DEL:@alice", UiAction.deleteUser("@alice").encode()),
        () -> assertEquals("USER_DEL_CONFIRM:123", UiAction.confirmDeleteUser("123").encode()));
  }

  // --- invalid data --------------------------------------------------------------------------

  @Test
  @DisplayName("cleanup AC-6: decode is empty for invalid data (USER:alice, USER:@a:b, ACCESS_GRANT:123:9, RUN:, HOME:x, unknown tags, blank, null, …)")
  void decodeRejectsInvalidData() {
    String[] invalid = {
        null, "", "   ", "NOPE", "home", "users", "user:123", "stop:eu-central-1:" + TASK_ID,
        "HOME:x", "HOME:", "HELP:x", "REQUEST_ACCESS:x", "USERS:x",
        "RUN", "RUN:", "RUN: ", "RUN_NEW:", "RUN_NEW: ",
        "USE:eu-central-1", "STOP:eu-central-1:", "STOP::" + TASK_ID, "STOP:a:b:c",
        "STOP_CONFIRM: :" + TASK_ID,
        "ACCESS_TOGGLE:0:1:NODE_ADMIN", "ACCESS_TOGGLE:-1:1:NODE_ADMIN", "ACCESS_TOGGLE:1:1:ADMIN",
        "ACCESS_TOGGLE:123:1", "ACCESS_TOGGLE:abc:1:NODE_ADMIN", "ACCESS_TOGGLE:123:x:NODE_ADMIN",
        "ACCESS_TOGGLE:123:8:NODE_ADMIN", "ACCESS_TOGGLE:123:1:node_admin",
        "ACCESS_TOGGLE:123:1:NODE_ADMIN:x",
        "ACCESS_GRANT:123:9", "ACCESS_GRANT:123:-1", "ACCESS_GRANT:123", "ACCESS_GRANT:0:1",
        "ACCESS_GRANT:abc:1", "ACCESS_GRANT:123:x",
        "ACCESS_DECLINE:0", "ACCESS_DECLINE:abc", "ACCESS_DECLINE:99999999999999999999",
        "USER:alice", "USER:@a:b", "USER:@", "USER: ", "USER:@ ", "USER:@a b", "USER:0", "USER:-5",
        "USER_ROLE:123", "USER_ROLE:123:", "USER_ROLE:123:ROOT", "USER_ROLE::NODE_ADMIN",
        "USER_ROLE:alice:NODE_ADMIN", "USER_ROLE:@a:b:NODE_ADMIN",
        "USER_DEL:alice", "USER_DEL:@", "USER_DEL_CONFIRM:alice", "USER_DEL_CONFIRM:@a:b"};
    assertAll(Arrays.stream(invalid)
                  .map(data -> (Executable) () -> assertEquals(Optional.empty(), UiAction.decode(data),
                                                               "decode(" + data + ")")));
  }

  @Test
  @DisplayName("cleanup §4.4, AC-6: decode of each tag without its argument is empty, and a tag without argument rejects one")
  void decodeChecksArgumentPresence() {
    List<Executable> checks = new ArrayList<>();
    for (Row row : WIRE_FORMAT) {
      String tag = StringUtils.substringBefore(row.data(), ":");
      if (row.data().contains(":")) {
        checks.add(() -> assertEquals(Optional.empty(), UiAction.decode(tag), "decode(" + tag + ")"));
        checks.add(() -> assertEquals(Optional.empty(), UiAction.decode(tag + ":"), "decode(" + tag + ":)"));
      } else {
        checks.add(() -> assertEquals(Optional.empty(), UiAction.decode(tag + ":x"), "decode(" + tag + ":x)"));
      }
    }
    assertAll(checks);
  }

  @Test
  @DisplayName("cleanup §4.4, AC-6: the compact constructors reject invalid components with IllegalArgumentException")
  void constructorsRejectInvalidComponents() {
    assertAll(
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.Run("")),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.Run(" ")),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.RunNew("")),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.RunNew(" ")),
        () -> assertThrows(IllegalArgumentException.class,
                           () -> new UiAction.AccessToggle(0L, Set.of(), NODE_ADMIN)),
        () -> assertThrows(IllegalArgumentException.class,
                           () -> new UiAction.AccessToggle(-1L, Set.of(NODE_ADMIN), NODE_ADMIN)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.AccessGrant(0L, Set.of(READ_ONLY))),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.AccessGrant(-7L, Set.of())),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.AccessDecline(0L)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.AccessDecline(-1L)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.ShowUser("alice")),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.ShowUser("@")),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.ShowUser("@a:b")),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.ShowUser("@a b")),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.ShowUser("0")),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.ShowUser("")),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.ToggleRole("alice", READ_ONLY)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.ToggleRole("@a:b", READ_ONLY)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.DeleteUser("alice")),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.DeleteUser("-5")),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.ConfirmDeleteUser("@")),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.ConfirmDeleteUser("@a b")));
  }

  @Test
  @DisplayName("cleanup §4.4: a null region, key or node reference is invalid (IllegalArgumentException)")
  void constructorsRejectNullComponents() {
    assertAll(
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.Run(null)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.RunNew(null)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.Use(null)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.Stop(null)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.ConfirmStop(null)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.ShowUser(null)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.ToggleRole(null, READ_ONLY)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.DeleteUser(null)),
        () -> assertThrows(IllegalArgumentException.class, () -> new UiAction.ConfirmDeleteUser(null)));
  }

  @Test
  @DisplayName("cleanup §4.4: valid keys are a positive id or @ + a username without ':' or whitespace")
  void constructorsAcceptValidKeys() {
    assertAll(
        () -> assertDoesNotThrow(() -> new UiAction.ShowUser("1")),
        () -> assertDoesNotThrow(() -> new UiAction.ShowUser("9999999999")),
        () -> assertDoesNotThrow(() -> new UiAction.ShowUser("@a")),
        () -> assertDoesNotThrow(() -> new UiAction.ShowUser("@alice_99")),
        () -> assertDoesNotThrow(() -> new UiAction.ToggleRole("@" + USERNAME_32, USER_ADMIN)));
  }

  // --- 64-byte limit -------------------------------------------------------------------------

  @Test
  @DisplayName("cleanup AC-6: encode throws IllegalArgumentException above 64 bytes (70-char region)")
  void encodeRejectsSeventyCharRegion() {
    UiAction action = new UiAction.Run("x".repeat(70));

    assertThrows(IllegalArgumentException.class, action::encode);
  }

  @Test
  @DisplayName("cleanup AC-6: encode throws on 65-byte data and accepts exactly 64 bytes")
  void encodeLimitIsSixtyFourBytes() {
    UiAction sixtyFive = new UiAction.Run("r".repeat(61));
    String sixtyFour = "RUN:" + "r".repeat(60);

    assertThrows(IllegalArgumentException.class, sixtyFive::encode);
    assertEquals(sixtyFour, new UiAction.Run("r".repeat(60)).encode());
    assertEquals(64, sixtyFour.getBytes(StandardCharsets.UTF_8).length);
  }

  @Test
  @DisplayName("cleanup AC-6: encode counts UTF-8 bytes, not characters")
  void encodeCountsUtf8Bytes() {
    UiAction action = new UiAction.Run("é".repeat(31));

    assertThrows(IllegalArgumentException.class, action::encode);
  }

  @Test
  @DisplayName("2b AC-1, cleanup AC-6: STOP_CONFIRM with a 14-char region and a 32-char task id fits (60 bytes); a 40-char task id does not")
  void nodeActionsAgainstLimit() {
    String data = new UiAction.ConfirmStop(new NodeRef("ap-southeast-7", TASK_ID)).encode();
    UiAction tooLong = new UiAction.ConfirmStop(new NodeRef("ap-southeast-7", "t".repeat(40)));

    assertEquals("STOP_CONFIRM:ap-southeast-7:" + TASK_ID, data);
    assertEquals(60, data.getBytes(StandardCharsets.UTF_8).length);
    assertThrows(IllegalArgumentException.class, tooLong::encode);
  }

  @Test
  @DisplayName("4 AC-7, cleanup AC-6: every access and user action with a 10-digit id or a 32-char username stays under 64 bytes and round-trips")
  void accessAndUserActionsUnderLimit() {
    long id = 9_999_999_999L;
    String usernameKey = "@" + USERNAME_32;
    Set<UserRole> all = EnumSet.allOf(UserRole.class);
    List<UiAction> actions = new ArrayList<>(List.of(
        new UiAction.RequestAccess(),
        new UiAction.AccessGrant(id, all),
        new UiAction.AccessDecline(id),
        new UiAction.Users(),
        new UiAction.ShowUser(usernameKey),
        new UiAction.ShowUser(String.valueOf(id)),
        new UiAction.DeleteUser(usernameKey),
        new UiAction.ConfirmDeleteUser(usernameKey)));
    for (UserRole role : UserRole.values()) {
      actions.add(new UiAction.AccessToggle(id, all, role));
      actions.add(new UiAction.ToggleRole(usernameKey, role));
    }

    assertEquals("ACCESS_TOGGLE:9999999999:7:USER_ADMIN", new UiAction.AccessToggle(id, all, USER_ADMIN).encode());
    assertEquals(54, new UiAction.ToggleRole(usernameKey, NODE_ADMIN).encode()
        .getBytes(StandardCharsets.UTF_8).length);
    assertAll(actions.stream().map(action -> (Executable) () -> {
      String data = action.encode();
      assertTrue(data.getBytes(StandardCharsets.UTF_8).length < 64, data);
      assertEquals(Optional.of(action), UiAction.decode(data), data);
    }));
  }

}
