package org.github.akarkin1.deduplication;

import lombok.val;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class FSUpdateEventsRegistryTest {

  public static final int DEFAULT_TTL = 60_000;
  public static final int TINY_TTL = 300;
  private static final String ROOT_DIR = "src/test/resources/eventIds";
  public static final int EVENT_ID_1 = 123;
  public static final int EVENT_ID_2 = 123;

  @AfterAll
  static void deleteTheDir() throws IOException {
    FileUtils.deleteDirectory(new File(ROOT_DIR));
  }

  @AfterEach
  void cleanUpTheDir() throws IOException {
    FileUtils.cleanDirectory(new File(ROOT_DIR));
  }

  @Test
  void testHasAlreadyRegistered_false() {
    val registry = new FSUpdateEventsRegistry(DEFAULT_TTL, ROOT_DIR);
    final var event = createEvent(EVENT_ID_1);

    assertFalse(registry.hasAlreadyProcessed(event));
  }

  private static Update createEvent(int updateId) {
    val event = new Update();
    event.setUpdateId(updateId);
    return event;
  }

  @Test
  void testHasAlreadyRegistered_true() {
    val registry = new FSUpdateEventsRegistry(DEFAULT_TTL, ROOT_DIR);
    final var event = createEvent(EVENT_ID_1);

    registry.registerEvent(event);

    assertTrue(registry.hasAlreadyProcessed(event));
  }
  @Test
  void registerEvent_happyPath() {
    val registry = new FSUpdateEventsRegistry(DEFAULT_TTL, ROOT_DIR);
    final var event = createEvent(EVENT_ID_1);

    registry.registerEvent(event);

    assertTrue(Files.exists(Paths.get(ROOT_DIR, String.valueOf(event.getUpdateId()))));
  }

  @Test
  void registerEvent_expiredFilesAreCleanedUp() throws InterruptedException {
    val registry = new FSUpdateEventsRegistry(TINY_TTL, ROOT_DIR);
    final var event1 = createEvent(EVENT_ID_1);

    registry.registerEvent(event1);

    Thread.sleep(400);

    final var event2 = createEvent(EVENT_ID_2);
    registry.registerEvent(event2);

    assertFalse(Files.exists(Paths.get(ROOT_DIR, String.valueOf(event1.getUpdateId()))));
    assertFalse(Files.exists(Paths.get(ROOT_DIR, String.valueOf(event2.getUpdateId()))));
  }

}