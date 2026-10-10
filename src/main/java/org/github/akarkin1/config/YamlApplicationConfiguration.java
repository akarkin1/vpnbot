package org.github.akarkin1.config;

import lombok.Getter;
import lombok.Setter;
import org.yaml.snakeyaml.Yaml;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Getter
@Setter
public class YamlApplicationConfiguration {

  private String version;

  private EcsConfiguration ecs;

  private AWSConfiguration aws;

  private AuthConfiguration auth;

  @Getter
  @Setter
  public static class EcsConfiguration {

    private String essentialContainerName;
    private String serviceName;
    private String hostNameEnv;
    private String hostNameTag;
    private String runByTag;
    private String serviceNameTag;
    private String chatIdTag;
    private String languageTag;

  }

  @Getter
  @Setter
  public static class AWSConfiguration {

    private Map<String, String> regionCities;
    private Map<String, String> regionCountries;

  }

  @Getter
  @Setter
  public static class AuthConfiguration {

    private boolean enabled;

  }


  public static YamlApplicationConfiguration load(String applicationYaml) {
    Yaml yaml = SnakeYamlCustomFactory.createYaml();
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
