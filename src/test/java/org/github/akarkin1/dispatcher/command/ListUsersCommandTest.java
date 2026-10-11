package org.github.akarkin1.dispatcher.command;

import org.github.akarkin1.auth.FakePermissionsService;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.dispatcher.response.TextCommandResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code /listRegisteredUsers} prints {@code @username (id): roles}, or {@code @username (pending):
 * roles} for a pending user (§4.6). The response is checked after applying its params, so it does
 * not matter whether the values are formatted in or passed as params.
 */
class ListUsersCommandTest {

  private final FakePermissionsService permissions = new FakePermissionsService();
  private final ListUsersCommand command = new ListUsersCommand(permissions);

  @Test
  @DisplayName("§4.6 (unchanged): no users → the 'no users registered' message")
  void noUsers() {
    TextCommandResponse response = command.run(List.of());

    assertEquals("${command.list-users.no-users-registered.message}", render(response));
  }

  @Test
  @DisplayName("4 AC-10: a user with an id is listed as @username (id): permissions")
  void userWithId() {
    permissions.withUser(42L, "alice", Permission.LIST_NODES, Permission.RUN_NODES);

    String output = render(command.run(List.of()));

    assertTrue(output.startsWith("${command.list-users.list-of-registered-users.message}"), output);
    String line = lineOf(output, "@alice");
    assertTrue(line.contains("@alice (42):"), line);
    assertTrue(line.contains("LIST_NODES") && line.contains("RUN_NODES"), line);
  }

  @Test
  @DisplayName("4 AC-10: a pending user is marked as pending instead of an id")
  void pendingUser() {
    permissions.withPending("bob", Permission.USER_MANAGEMENT);

    String line = lineOf(render(command.run(List.of())), "@bob");

    assertTrue(line.contains("@bob ("), line);
    assertTrue(line.contains("pending"), line);
    assertTrue(line.contains("):"), line);
    assertTrue(line.contains("USER_MANAGEMENT"), line);
  }

  @Test
  @DisplayName("4 AC-10: every known user (USER and PENDING_USER) is listed once")
  void everyUserListed() {
    permissions.withUser(42L, "alice", Permission.LIST_NODES)
        .withUser(1L, "root", Permission.ROOT_ACCESS)
        .withPending("bob", Permission.LIST_NODES);

    String output = render(command.run(List.of()));

    assertEquals(1, count(output, "@alice"), output);
    assertEquals(1, count(output, "@root"), output);
    assertEquals(1, count(output, "@bob"), output);
    assertTrue(lineOf(output, "@root").contains("(1):"), output);
    assertFalse(lineOf(output, "@alice").contains("pending"), output);
  }

  @Test
  @DisplayName("4 AC-10: a user without a username is listed by the id")
  void userWithoutUsername() {
    permissions.withUser(43L, null, Permission.LIST_NODES);

    String output = render(command.run(List.of()));

    assertTrue(output.contains("43"), output);
    assertFalse(output.contains("@null"), output);
    assertFalse(output.contains("pending"), output);
  }

  private static String render(TextCommandResponse response) {
    Object[] params = response.params();
    return params == null || params.length == 0 ? response.text() : response.text().formatted(params);
  }

  private static String lineOf(String output, String marker) {
    return output.lines()
        .filter(line -> line.contains(marker))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no line with " + marker + " in: " + output));
  }

  private static long count(String output, String marker) {
    return output.lines().filter(line -> line.contains(marker)).count();
  }

}
