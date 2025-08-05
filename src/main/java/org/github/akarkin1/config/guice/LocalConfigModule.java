package org.github.akarkin1.config.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.config.LocalConfigManager;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import javax.inject.Singleton;

import static com.google.inject.Scopes.SINGLETON;

public class LocalConfigModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ConfigManager.class).to(LocalConfigManager.class).in(SINGLETON);
    }

    @Provides
    @Singleton
    S3Client provideS3Client(ConfigManager configManager) {
        return S3Client.builder()
            .endpointOverride(configManager.getLocalStackS3Endpoint())
            .forcePathStyle(true)
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")))
            .build();
    }

    @Provides
    @Singleton
    SecretsManagerClient provideSecretsManagerClient(ConfigManager configManager) {
        return SecretsManagerClient.builder()
            .endpointOverride(configManager.getLocalStackSMEndpoint())
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")))
            .build();
    }

}
