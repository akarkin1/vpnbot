package org.github.akarkin1.config.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.config.ProdConfigManager;
import org.github.akarkin1.ec2.Ec2ClientPool;
import org.github.akarkin1.ec2.Ec2ClientProvider;
import org.github.akarkin1.ecs.EcsClientPool;
import org.github.akarkin1.ecs.EcsClientProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import javax.inject.Singleton;

import static com.google.inject.Scopes.SINGLETON;

public class ProdConfigModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ConfigManager.class).to(ProdConfigManager.class).in(Scopes.SINGLETON);
        bind(EcsClientProvider.class).to(EcsClientPool.class).in(SINGLETON);
        bind(Ec2ClientProvider.class).to(Ec2ClientPool.class).in(SINGLETON);
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
