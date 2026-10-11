package org.github.akarkin1.dispatcher.command;

import org.github.akarkin1.auth.FakePermissionsService;
import org.github.akarkin1.auth.FakePermissionsService.Write;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.dispatcher.response.EmptyResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /deleteUsers} keeps its syntax (§4.6); usernames are resolved with findByUsername. */
class DeleteUsersCommandTest {

  private static final String DELETED = "${command.delete-users.user-is-deleted.message}";
  private static final String NOT_FOUND = "${command.delete-users.user-not-found.error}";
  private static final String ROOT = "${command.delete-users.root-cannot-be-deleted.error}";
  private static final String NONE_DELETED = "${command.delete-users.no-user-to-delete.message}";

  private record Sent(String message, List<Object> params) {}

  private final List<Sent> sent = new ArrayList<>();
  private final FakePermissionsService permissions = new FakePermissionsService()
      .withUser(1L, "root", Permission.ROOT_ACCESS)
      .withUser(42L, "alice", Permission.LIST_NODES)
      .withPending("bob", Permission.LIST_NODES);
  private final DeleteUsersCommand command = new DeleteUsersCommand(
      permissions, (message, params) -> sent.add(new Sent(message, List.of(params))));

  @Test
  @DisplayName("4 AC-10: @username of a user with an id deletes the USER record of that id")
  void deletesUserById() {
    assertSame(EmptyResponse.NONE, command.run(List.of("@alice")));

    assertEquals(List.of(Write.deleteUser(42L)), permissions.writes());
    assertEquals(Optional.empty(), permissions.storedUser(42L));
    assertEquals(List.of(DELETED), messages());
  }

  @Test
  @DisplayName("4 AC-10: the username is resolved case-insensitively")
  void deletesUserIgnoringCase() {
    command.run(List.of("@ALICE"));

    assertEquals(List.of(Write.deleteUser(42L)), permissions.writes());
  }

  @Test
  @DisplayName("4 AC-10: a pending user is deleted (PENDING_USER record)")
  void deletesPendingUser() {
    command.run(List.of("bob"));

    assertEquals(List.of(Write.deletePending("bob")), permissions.writes());
    assertFalse(permissions.hasPending("bob"));
    assertEquals(List.of(DELETED), messages());
  }

  @Test
  @DisplayName("4 AC-10, §4.6: a numeric id is accepted")
  void deletesByNumericId() {
    command.run(List.of("42"));

    assertEquals(List.of(Write.deleteUser(42L)), permissions.writes());
    assertEquals(List.of(DELETED), messages());
  }

  @Test
  @DisplayName("§4.6 (unchanged): an unknown user is reported, nothing is written, and 'no users to delete' follows")
  void unknownUser() {
    command.run(List.of("@ghost"));

    assertEquals(List.of(), permissions.writes());
    assertEquals(2, sent.size(), sent.toString());
    assertTrue(sent.get(0).message().startsWith(NOT_FOUND), sent.toString());
    assertTrue(sent.get(0).message().contains("ghost"), sent.toString());
    assertEquals(NONE_DELETED, sent.get(1).message());
  }

  @Test
  @DisplayName("§4.6 (unchanged): the root user cannot be deleted")
  void rootCannotBeDeleted() {
    command.run(List.of("@root"));

    assertEquals(List.of(), permissions.writes());
    assertTrue(sent.get(0).message().startsWith(ROOT), sent.toString());
    assertEquals(NONE_DELETED, sent.get(1).message());
  }

  @Test
  @DisplayName("§4.6 (unchanged): several users: known ones are deleted, unknown ones reported")
  void severalUsers() {
    command.run(List.of("@alice", "@ghost", "@bob"));

    assertEquals(List.of(Write.deleteUser(42L), Write.deletePending("bob")), permissions.writes());
    assertEquals(3, sent.size(), sent.toString());
    assertFalse(messages().contains(NONE_DELETED));
  }

  @Test
  @DisplayName("§4.6 (unchanged): no user given → error, nothing is written")
  void noUserGiven() {
    command.run(List.of());

    assertEquals(List.of("${command.delete-users.no-user-specified.error}"), messages());
    assertEquals(List.of(), permissions.writes());
  }

  private List<String> messages() {
    return sent.stream().map(Sent::message).toList();
  }

}
