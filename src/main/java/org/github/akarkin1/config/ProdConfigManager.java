package org.github.akarkin1.config;

import com.google.inject.Inject;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import static java.lang.System.getenv;

public class ProdConfigManager implements ConfigManager {
    private final YamlApplicationConfiguration yamlConfig;
    private final ConfigConstants constants;

    @Inject
    public ProdConfigManager(YamlApplicationConfiguration yamlConfig, ConfigConstants constants) {
        this.yamlConfig = yamlConfig;
        this.constants = constants;
    }

    @Override
    public String getBotToken() {
        return envOrThrow(constants.BOT_TOKEN_ENV, () -> new IllegalStateException("BOT_TOKEN is required"));
    }

    @Override
    public String getBotUsername() {
        return envOrThrow(constants.BOT_USERNAME_ENV, () -> new IllegalStateException("BOT_USERNAME is required"));
    }

    @Override
    public String getEventRootDir() {
        return constants.EVENT_ROOT_DIR;
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
        String envVarVal = envOrDefault(constants.REGISTERED_EVENT_EXPIRATION_TIME_SEC_ENV, "360");
        long longValSec = Long.parseLong(envVarVal);
        return TimeUnit.SECONDS.toMillis(longValSec);
    }

    @Override
    public String getS3ConfigBucket() {
        return envOrThrow(constants.S3_CONFIG_BUCKET_ENV, () -> new IllegalStateException("S3_CONFIG_BUCKET is required"));
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
        return envOrThrow(constants.BOT_SECRET_TOKEN_ID_ENV, () -> new IllegalStateException("BOT_SECRET_TOKEN_ID is required"));
    }

    @Override
    public String getBotApiBaseUrl() {
        return System.getProperty(constants.BOT_API_BASE_URL_PROP);
    }

    private String envOrDefault(String envVarName, String defaultValue) {
        return Optional.ofNullable(getenv(envVarName)).orElse(defaultValue);
    }

    private String envOrThrow(String envVarName, Supplier<RuntimeException> exceptionSupplier) {
        return Optional.ofNullable(getenv(envVarName)).orElseThrow(exceptionSupplier);
    }
}

