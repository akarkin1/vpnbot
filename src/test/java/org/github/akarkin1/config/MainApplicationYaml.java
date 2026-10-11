package org.github.akarkin1.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The real {@code src/main/resources/application.yml} parsed the way the application parses it
 * ({@link SnakeYamlCustomFactory}); the test classpath has its own {@code application.yml}. The
 * file is read unfiltered, so {@code version} is the {@code ${project.version}} placeholder.
 */
public final class MainApplicationYaml {

  public static final Path FILE = Path.of("src/main/resources/application.yml");

  private MainApplicationYaml() {
  }

  public static YamlApplicationConfiguration load() {
    try (InputStream in = Files.newInputStream(FILE)) {
      return SnakeYamlCustomFactory.createYaml().loadAs(in, YamlApplicationConfiguration.class);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

}
