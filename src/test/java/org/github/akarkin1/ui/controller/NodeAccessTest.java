package org.github.akarkin1.ui.controller;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
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
    grant("alex", Permission.LIST_NODES, Permission.RUN_NODES);
    grant("reader", Permission.LIST_NODES);
    grant("root", Permission.ROOT_ACCESS, Permission.LIST_NODES, Permission.RUN_NODES);
  }

  @Test
  @DisplayName("2b AC-2: owner with RUN_NODES may stop and view without confirmation")
  void ownerWithRunNodes() {
    assertAll(
        () -> assertTrue(access.canStop("alex", ALEX_NODE)),
        () -> assertFalse(access.needsConfirmation("alex", ALEX_NODE)),
        () -> assertTrue(access.canView("alex", ALEX_NODE)));
  }

  @Test
  @DisplayName("2b AC-2: owner without RUN_NODES may not stop but may view")
  void ownerWithoutRunNodes() {
    TaskInfo readerNode = node("reader");

    assertAll(
        () -> assertFalse(access.canStop("reader", readerNode)),
        () -> assertFalse(access.needsConfirmation("reader", readerNode)),
        () -> assertTrue(access.canView("reader", readerNode)));
  }

  @Test
  @DisplayName("2b AC-2: another user with RUN_NODES may neither stop nor view")
  void otherUser() {
    assertAll(
        () -> assertFalse(access.canStop("alex", BOB_NODE)),
        () -> assertFalse(access.needsConfirmation("alex", BOB_NODE)),
        () -> assertFalse(access.canView("alex", BOB_NODE)));
  }

  @Test
  @DisplayName("2b AC-2: root on its own node may stop without confirmation and view")
  void rootOnOwnNode() {
    assertAll(
        () -> assertTrue(access.canStop("root", ROOT_NODE)),
        () -> assertFalse(access.needsConfirmation("root", ROOT_NODE)),
        () -> assertTrue(access.canView("root", ROOT_NODE)));
  }

  @Test
  @DisplayName("2b AC-2: root on someone else's node may stop after confirmation and view")
  void rootOnOthersNode() {
    assertAll(
        () -> assertTrue(access.canStop("root", ALEX_NODE)),
        () -> assertTrue(access.needsConfirmation("root", ALEX_NODE)),
        () -> assertTrue(access.canView("root", ALEX_NODE)));
  }

  @Test
  @DisplayName("2b AC-2: root with ROOT_ACCESS only (no other permission) may stop someone else's node after confirmation")
  void rootAccessAlone() {
    grant("admin", Permission.ROOT_ACCESS);

    assertAll(
        () -> assertTrue(access.canStop("admin", ALEX_NODE)),
        () -> assertTrue(access.needsConfirmation("admin", ALEX_NODE)),
        () -> assertTrue(access.canView("admin", ALEX_NODE)));
  }

  @Test
  @DisplayName("2b AC-2: read-only user may neither stop nor view someone else's node")
  void readOnlyUser() {
    assertAll(
        () -> assertFalse(access.canStop("reader", ALEX_NODE)),
        () -> assertFalse(access.needsConfirmation("reader", ALEX_NODE)),
        () -> assertFalse(access.canView("reader", ALEX_NODE)));
  }

  @Test
  @DisplayName("2b AC-2: a node without RunBy tag is nobody's own node")
  void nodeWithoutRunBy() {
    TaskInfo untagged = node(null);

    assertAll(
        () -> assertFalse(access.canStop("alex", untagged)),
        () -> assertFalse(access.canView("alex", untagged)),
        () -> assertTrue(access.canStop("root", untagged)),
        () -> assertTrue(access.needsConfirmation("root", untagged)));
  }

  private void grant(String username, Permission... permissions) {
    for (Permission permission : permissions) {
      lenient().when(authorizer.hasPermission(username, permission)).thenReturn(true);
    }
  }

  private static TaskInfo node(String runBy) {
    return TaskInfo.builder()
        .id("0123456789abcdef0123456789abcdef")
        .hostName("node-1")
        .runBy(runBy)
        .build();
  }

}
