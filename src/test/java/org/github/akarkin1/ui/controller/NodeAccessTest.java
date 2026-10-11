package org.github.akarkin1.ui.controller;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.ecs.TaskInfo;
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

@ExtendWith(MockitoExtension.class)
class NodeAccessTest {

  private static final TaskInfo ALEX_NODE = node("alex");
  private static final TaskInfo BOB_NODE = node("bob");
  private static final TaskInfo ROOT_NODE = node("root");

  @Mock
  private Authorizer authorizer;

  private NodeAccess access;

  @BeforeEach
  void setUp() {
    access = new NodeAccess(authorizer);
    grant(user("alex"), Permission.LIST_NODES, Permission.RUN_NODES);
    grant(user("reader"), Permission.LIST_NODES);
    grant(user("root"), Permission.ROOT_ACCESS, Permission.LIST_NODES, Permission.RUN_NODES);
  }

  @Test
  @DisplayName("2b AC-2: owner with RUN_NODES may stop and view without confirmation")
  void ownerWithRunNodes() {
    assertAll(
        () -> assertTrue(access.canStop(user("alex"), ALEX_NODE)),
        () -> assertFalse(access.needsConfirmation(user("alex"), ALEX_NODE)),
        () -> assertTrue(access.canView(user("alex"), ALEX_NODE)));
  }

  @Test
  @DisplayName("2b AC-2: owner without RUN_NODES may not stop but may view")
  void ownerWithoutRunNodes() {
    TaskInfo readerNode = node("reader");

    assertAll(
        () -> assertFalse(access.canStop(user("reader"), readerNode)),
        () -> assertFalse(access.needsConfirmation(user("reader"), readerNode)),
        () -> assertTrue(access.canView(user("reader"), readerNode)));
  }

  @Test
  @DisplayName("2b AC-2: another user with RUN_NODES may neither stop nor view")
  void otherUser() {
    assertAll(
        () -> assertFalse(access.canStop(user("alex"), BOB_NODE)),
        () -> assertFalse(access.needsConfirmation(user("alex"), BOB_NODE)),
        () -> assertFalse(access.canView(user("alex"), BOB_NODE)));
  }

  @Test
  @DisplayName("2b AC-2: root on its own node may stop without confirmation and view")
  void rootOnOwnNode() {
    assertAll(
        () -> assertTrue(access.canStop(user("root"), ROOT_NODE)),
        () -> assertFalse(access.needsConfirmation(user("root"), ROOT_NODE)),
        () -> assertTrue(access.canView(user("root"), ROOT_NODE)));
  }

  @Test
  @DisplayName("2b AC-2: root on someone else's node may stop after confirmation and view")
  void rootOnOthersNode() {
    assertAll(
        () -> assertTrue(access.canStop(user("root"), ALEX_NODE)),
        () -> assertTrue(access.needsConfirmation(user("root"), ALEX_NODE)),
        () -> assertTrue(access.canView(user("root"), ALEX_NODE)));
  }

  @Test
  @DisplayName("2b AC-2: root with ROOT_ACCESS only (no other permission) may stop someone else's node after confirmation")
  void rootAccessAlone() {
    grant(user("admin"), Permission.ROOT_ACCESS);

    assertAll(
        () -> assertTrue(access.canStop(user("admin"), ALEX_NODE)),
        () -> assertTrue(access.needsConfirmation(user("admin"), ALEX_NODE)),
        () -> assertTrue(access.canView(user("admin"), ALEX_NODE)));
  }

  @Test
  @DisplayName("2b AC-2: read-only user may neither stop nor view someone else's node")
  void readOnlyUser() {
    assertAll(
        () -> assertFalse(access.canStop(user("reader"), ALEX_NODE)),
        () -> assertFalse(access.needsConfirmation(user("reader"), ALEX_NODE)),
        () -> assertFalse(access.canView(user("reader"), ALEX_NODE)));
  }

  @Test
  @DisplayName("2b AC-2: a node without RunBy tag is nobody's own node")
  void nodeWithoutRunBy() {
    TaskInfo untagged = node(null);

    assertAll(
        () -> assertFalse(access.canStop(user("alex"), untagged)),
        () -> assertFalse(access.canView(user("alex"), untagged)),
        () -> assertTrue(access.canStop(user("root"), untagged)),
        () -> assertTrue(access.needsConfirmation(user("root"), untagged)));
  }

  private void grant(TgUser user, Permission... permissions) {
    for (Permission permission : permissions) {
      lenient().when(authorizer.hasPermission(user, permission)).thenReturn(true);
    }
  }

  /** Nodes in this test are tagged with the username (the pre-Phase-4 RunBy value). */
  private static TgUser user(String username) {
    return new TgUser((long) Math.abs(username.hashCode()), username);
  }

  private static TaskInfo node(String runBy) {
    return TaskInfo.builder()
        .id("0123456789abcdef0123456789abcdef")
        .hostName("node-1")
        .runBy(runBy)
        .build();
  }

}
