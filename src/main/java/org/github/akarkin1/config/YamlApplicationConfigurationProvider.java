package org.github.akarkin1.config;

import com.google.inject.Inject;
import com.google.inject.Provider;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;

public class YamlApplicationConfigurationProvider implements Provider<YamlApplicationConfiguration> {
    private final ConfigConstants constants;

    @Inject
    public YamlApplicationConfigurationProvider(ConfigConstants constants) {
        this.constants = constants;
    }

    @Override
    public YamlApplicationConfiguration get() {
        return YamlApplicationConfiguration.load(constants.APP_CONFIG_YAML);
    }
}

