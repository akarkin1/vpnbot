package org.github.akarkin1.config;

import lombok.Data;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Data
public class AppConfig {

  private static AppConfig INSTANCE;

  static {
    Path configPath = Paths.get("./config.yaml");
    Constructor constructor = new Constructor(AppConfig.class, new LoaderOptions());
    Yaml yaml = new Yaml(constructor);
    try {
      INSTANCE = yaml.load(new FileInputStream(configPath.toFile()));
    } catch (FileNotFoundException e) {
      throw new IllegalStateException("Failed to load config.yaml", e);
    }
  }

  private App app;

  public static AppConfig getInstance() {
    return INSTANCE;
  }

  @Data
  public static class App {

    private List<String> supportedRegions;
  }
}
