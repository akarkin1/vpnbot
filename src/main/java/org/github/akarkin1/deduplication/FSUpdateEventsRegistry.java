package org.github.akarkin1.deduplication;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@Log4j2
@RequiredArgsConstructor
public class FSUpdateEventsRegistry implements UpdateEventsRegistry {

  private static final String EVENT_IDS_ROOT_DIR = "/tmp/eventIds";
  private final long registeredEventExpirationTimeMs;

  @Override
  public boolean hasAlreadyProcessed(Update update) {
    String updateId = String.valueOf(update.getUpdateId());
    return Files.exists(Paths.get(EVENT_IDS_ROOT_DIR, updateId));
  }

  @Override
  public void registerEvent(Update update) {
    String updateId = String.valueOf(update.getUpdateId());

    Path rootPath = Paths.get(EVENT_IDS_ROOT_DIR);
    try {
      if (Files.notExists(rootPath)) {
        Files.createDirectories(rootPath);
      }

      Files.createFile(Paths.get(EVENT_IDS_ROOT_DIR, updateId));
    } catch (IOException e) {
      log.error("Failed to register event: {}", update, e);
    }

    deleteExpiredFiles();
  }

  private void deleteExpiredFiles() {
    try (Stream<Path> foundFiles = Files.list(Paths.get(EVENT_IDS_ROOT_DIR))) {
      foundFiles.forEach(path -> {
        val file = path.toFile();

        if (file.isDirectory()) {
          return;
        }

        long currentTimeMs = System.currentTimeMillis();
        long lastModified = file.lastModified();
        if (currentTimeMs - lastModified > registeredEventExpirationTimeMs) {
          deleteFileWithoutException(path);
        }
      });
    } catch (IOException e) {
      log.error("Failed to list directory: {}", EVENT_IDS_ROOT_DIR, e);
    }
  }

  private static void deleteFileWithoutException(Path path) {
    try {
      Files.delete(path);
    } catch (IOException e) {
      log.error("Failed to delete file during cleanup procedure: {}", path.getFileName(), e);
    }
  }
}
