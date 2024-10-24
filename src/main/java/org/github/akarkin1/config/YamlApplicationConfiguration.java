package org.github.akarkin1.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.yaml.snakeyaml.Yaml;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

@Getter
@Setter
public class YamlApplicationConfiguration {

  private S3Configuration s3;

  @Getter
  @Setter
  @RequiredArgsConstructor
  public static class S3Configuration {

    private String configBucket;
    private String configRootDir;
    private String regionsKey;
    private String stackOutputParametersKey;
  }

  public static YamlApplicationConfiguration load(String applicationYaml) {
    Yaml yaml = new Yaml();
    try (InputStream inputStream = YamlApplicationConfiguration.class.getClassLoader()
        .getResourceAsStream(applicationYaml)) {

      if (inputStream == null) {
        throw new FileNotFoundException("No application yaml found: " + applicationYaml);
      }
      return yaml.loadAs(inputStream, YamlApplicationConfiguration.class);
    } catch (IOException e) {
      throw new RuntimeException("Error loading config file", e);
    }
  }
}
