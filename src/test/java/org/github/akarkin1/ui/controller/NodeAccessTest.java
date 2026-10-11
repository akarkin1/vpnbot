package org.github.akarkin1.ui.controller;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.ecs.EcsNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

/** Nodes are owned by user id ({@code RunBy} = id) only. */
@ExtendWith(MockitoExtension.class)
class NodeAccessTest {

  private static final TgUser ALEX = new TgUser(7L, "alex");
  private static final TgUser BOB = new TgUser(8L, "bob");
  private static final TgUser READER = new TgUser(9L, "reader");
  private static final TgUser ROOT = new TgUser(1L, "root");

  private static final EcsNode ALEX_NODE = node("7", "alex");
  private static final EcsNode BOB_NODE = node("8", "bob");
  private static final EcsNode ROOT_NODE = node("1", "root");

  @Mock
  private Authorizer authorizer;

  private NodeAccess access;

  @BeforeEach
  void setUp() {
    access = new NodeAccess(authorizer);
    grant(ALEX, Permission.LIST_NODES, Permission.RUN_NODES);
    grant(BOB, Permission.LIST_NODES, Permission.RUN_NODES);
    grant(READER, Permission.LIST_NODES);
    grant(ROOT, Permission.ROOT_ACCESS, Permission.LIST_NODES, Permission.RUN_NODES);
  }

  @Test
  @DisplayName("2b AC-2, 4 AC-9: owner (RunBy = user id) with RUN_NODES may stop and view without confirmation")
  void ownerWithRunNodes() {
    assertAll(
        () -> assertTrue(access.canManageNodes(ALEX)),
        () -> assertTrue(access.canStop(ALEX, ALEX_NODE)),
        () -> assertFalse(access.needsConfirmation(ALEX, ALEX_NODE)),
        () -> assertTrue(access.canView(ALEX, ALEX_NODE)));
  }

  @Test
  @DisplayName("4 AC-9 (goal 1): the owner is matched by id, so a renamed owner keeps the node")
  void renamedOwnerMatchedById() {
    TgUser renamed = new TgUser(7L, "alex_renamed");
    grant(renamed, Permission.RUN_NODES);

    assertAll(
        () -> assertTrue(access.canStop(renamed, ALEX_NODE)),
        () -> assertFalse(access.needsConfirmation(renamed, ALEX_NODE)),
        () -> assertTrue(access.canView(renamed, ALEX_NODE)));
  }

  @Test
  @DisplayName("4 AC-9: an owner without a username is matched by id")
  void ownerWithoutUsername() {
    TgUser anna = new TgUser(11L, null);
    grant(anna, Permission.RUN_NODES);
    EcsNode annasNode = node("11", null);

    assertAll(
        () -> assertTrue(access.canManageNodes(anna)),
        () -> assertTrue(access.canStop(anna, annasNode)),
        () -> assertFalse(access.needsConfirmation(anna, annasNode)),
        () -> assertTrue(access.canView(anna, annasNode)),
        () -> assertFalse(access.canView(anna, ALEX_NODE)));
  }

  @Test
  @DisplayName("4 AC-9: RunByName alone does not make an owner: someone with that username but another id is not the owner")
  void runByNameIsNotOwnership() {
    TgUser impostor = new TgUser(99L, "alex_new");
    grant(impostor, Permission.RUN_NODES);
    EcsNode node = node("7", "alex_new");

    assertAll(
        () -> assertFalse(access.canStop(impostor, node)),
        () -> assertFalse(access.canView(impostor, node)));
  }

  @Test
  @DisplayName("2b AC-2: owner without RUN_NODES may not stop but may view")
  void ownerWithoutRunNodes() {
    EcsNode readerNode = node("9", "reader");

    assertAll(
        () -> assertFalse(access.canManageNodes(READER)),
        () -> assertFalse(access.canStop(READER, readerNode)),
        () -> assertFalse(access.needsConfirmation(READER, readerNode)),
        () -> assertTrue(access.canView(READER, readerNode)));
  }

  @Test
  @DisplayName("2b AC-2: another user with RUN_NODES may neither stop nor view")
  void otherUser() {
    assertAll(
        () -> assertFalse(access.canStop(ALEX, BOB_NODE)),
        () -> assertFalse(access.needsConfirmation(ALEX, BOB_NODE)),
        () -> assertFalse(access.canView(ALEX, BOB_NODE)));
  }

  @Test
  @DisplayName("2b AC-2: root on its own node may stop without confirmation and view")
  void rootOnOwnNode() {
    assertAll(
        () -> assertTrue(access.canManageNodes(ROOT)),
        () -> assertTrue(access.canStop(ROOT, ROOT_NODE)),
        () -> assertFalse(access.needsConfirmation(ROOT, ROOT_NODE)),
        () -> assertTrue(access.canView(ROOT, ROOT_NODE)));
  }

  @Test
  @DisplayName("2b AC-2: root on someone else's node may stop after confirmation and view")
  void rootOnOthersNode() {
    assertAll(
        () -> assertTrue(access.canStop(ROOT, ALEX_NODE)),
        () -> assertTrue(access.needsConfirmation(ROOT, ALEX_NODE)),
        () -> assertTrue(access.canView(ROOT, ALEX_NODE)));
  }

  @Test
  @DisplayName("2b AC-2: root with ROOT_ACCESS only (no other permission) may stop someone else's node after confirmation")
  void rootAccessAlone() {
    TgUser admin = new TgUser(2L, "admin");
    grant(admin, Permission.ROOT_ACCESS);

    assertAll(
        () -> assertTrue(access.canManageNodes(admin)),
        () -> assertTrue(access.canStop(admin, ALEX_NODE)),
        () -> assertTrue(access.needsConfirmation(admin, ALEX_NODE)),
        () -> assertTrue(access.canView(admin, ALEX_NODE)));
  }

  @Test
  @DisplayName("2b AC-2: read-only user may neither stop nor view someone else's node")
  void readOnlyUser() {
    assertAll(
        () -> assertFalse(access.canStop(READER, ALEX_NODE)),
        () -> assertFalse(access.needsConfirmation(READER, ALEX_NODE)),
        () -> assertFalse(access.canView(READER, ALEX_NODE)));
  }

  @Test
  @DisplayName("2b AC-2: a node without RunBy tag is nobody's own node")
  void nodeWithoutRunBy() {
    EcsNode untagged = node(null, null);

    assertAll(
        () -> assertFalse(access.canStop(ALEX, untagged)),
        () -> assertFalse(access.canView(ALEX, untagged)),
        () -> assertTrue(access.canStop(ROOT, untagged)),
        () -> assertTrue(access.needsConfirmation(ROOT, untagged)));
  }

  @Test
  @DisplayName("4 AC-9: a user without a username does not own a node without RunBy")
  void noUsernameDoesNotMatchUntaggedNode() {
    TgUser anna = new TgUser(11L, null);
    grant(anna, Permission.RUN_NODES);

    assertAll(
        () -> assertFalse(access.canStop(anna, node(null, null))),
        () -> assertFalse(access.canView(anna, node(null, null))));
  }

  private void grant(TgUser user, Permission... permissions) {
    for (Permission permission : permissions) {
      lenient().when(authorizer.hasPermission(user, permission)).thenReturn(true);
    }
  }

  private static EcsNode node(String runBy, String runByName) {
    return EcsNode.builder()
        .id("0123456789abcdef0123456789abcdef")
        .hostName("node-1")
        .runBy(runBy)
        .runByName(runByName)
        .build();
  }

}
