package org.github.akarkin1.config.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.config.ProdConfigManager;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import javax.inject.Singleton;

public class ProdConfigModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ConfigManager.class).to(ProdConfigManager.class).in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    S3Client provideS3Client() {
        return S3Client.create();
    }

    @Provides
    @Singleton
    SecretsManagerClient provideSecretsManagerClient() {
        return SecretsManagerClient.create();
    }

}
