package org.github.akarkin1.config;

import com.google.inject.Inject;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class LocalConfigManager implements ConfigManager {
    private final YamlApplicationConfiguration yamlConfig;
    private final ConfigConstants constants;

    @Inject
    public LocalConfigManager(YamlApplicationConfiguration yamlConfig, ConfigConstants constants) {
        this.yamlConfig = yamlConfig;
        this.constants = constants;
    }

    @Override
    public String getBotToken() {
        return System.getProperty(constants.BOT_TOKEN_PROP, "local-bot-token");
    }

    @Override
    public String getBotUsername() {
        return System.getProperty(constants.BOT_USERNAME_ENV, "local-bot-username");
    }

    @Override
    public String getEventRootDir() {
        return "test-eventIds";
    }

    @Override
    public URI getLocalStackS3Endpoint() {
        return Optional.ofNullable(System.getProperty("localstack.s3.endpoint")).map(URI::create).orElse(null);
    }

    @Override
    public URI getLocalStackSMEndpoint() {
        return Optional.ofNullable(System.getProperty("localstack.secretmanager.endpoint")).map(URI::create).orElse(null);
    }

    @Override
    public Long getEventTtlMillis() {
        String val = System.getProperty(constants.REGISTERED_EVENT_EXPIRATION_TIME_SEC_ENV, "360");
        long longValSec = Long.parseLong(val);
        return TimeUnit.SECONDS.toMillis(longValSec);
    }

    @Override
    public String getS3ConfigBucket() {
        return System.getProperty(constants.S3_CONFIG_BUCKET_PROP, "local-s3-bucket");
    }

    @Override
    public Set<String> getSupportedServices() {
        return yamlConfig.getS3().getServiceConfigs().keySet();
    }

    @Override
    public String getAppVersion() {
        return yamlConfig.getVersion();
    }

    @Override
    public String getSecretTokenId() {
        return System.getProperty(constants.BOT_SECRET_TOKEN_ID_PROP, "local-secret-token-id");
    }

    @Override
    public String getBotApiBaseUrl() {
        return System.getProperty(constants.BOT_API_BASE_URL_PROP, "http://localhost:8081");
    }
}

