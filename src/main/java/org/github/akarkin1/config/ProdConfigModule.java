package org.github.akarkin1.config;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.RequestAuthenticator;
import org.github.akarkin1.auth.SecretManagerRequestAuthenticator;
import org.github.akarkin1.auth.WhiteListAuthorizer;
import org.github.akarkin1.auth.s3.CachingEntitlementsService;
import org.github.akarkin1.auth.s3.EntitlementsService;
import org.github.akarkin1.auth.s3.S3EntitlementsService;
import org.github.akarkin1.config.YamlApplicationConfiguration.AuthConfiguration;
import org.github.akarkin1.config.YamlApplicationConfiguration.S3Configuration;
import org.github.akarkin1.deduplication.FSUpdateEventsRegistry;
import org.github.akarkin1.deduplication.UpdateEventsRegistry;
import org.github.akarkin1.dispatcher.CommandDispatcher;
import org.github.akarkin1.dispatcher.GuiceCommandDispatcherProvider;
import org.github.akarkin1.ec2.Ec2ClientPool;
import org.github.akarkin1.ecs.EcsClientPool;
import org.github.akarkin1.ecs.EcsManager;
import org.github.akarkin1.ecs.EcsManagerImpl;
import org.github.akarkin1.s3.S3ConfigManager;
import org.github.akarkin1.service.EcsNodeService;
import org.github.akarkin1.service.NodeService;
import org.github.akarkin1.translation.ResourceBasedTranslator;
import org.github.akarkin1.translation.Translator;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import javax.inject.Singleton;

public class ProdConfigModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ConfigConstants.class).in(Scopes.SINGLETON);
        bind(YamlApplicationConfiguration.class)
            .toProvider(YamlApplicationConfigurationProvider.class)
            .in(Scopes.SINGLETON);
        bind(ConfigManager.class).to(ProdConfigManager.class).in(Scopes.SINGLETON);
        bind(Translator.class).to(ResourceBasedTranslator.class).in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    S3Client provideS3Client() {
        return S3Client.create();
    }

    @Provides
    @Singleton
    S3ConfigManager provideS3ConfigManager(S3Configuration s3Config, S3Client s3Client, ConfigManager configManager) {
        return S3ConfigManager.create(s3Config, s3Client, configManager);
    }

    @Provides
    @Singleton
    S3Configuration provideS3Configuration(YamlApplicationConfiguration yamlConfig) {
        return yamlConfig.getS3();
    }

    @Provides
    @Singleton
    EntitlementsService provideEntitlementsService(S3ConfigManager s3ConfigManager, S3Configuration s3Config) {
        return new CachingEntitlementsService(new S3EntitlementsService(s3ConfigManager, s3Config));
    }

    @Provides
    @Singleton
    NodeService provideNodeService(YamlApplicationConfiguration yamlConfig, S3Client s3Client, ConfigManager configManager) {
        var s3Config = yamlConfig.getS3();
        S3TaskConfigService s3TaskConfigService = S3TaskConfigService.create(s3Config, s3Client, configManager);
        TaskConfigService cachedConfigService = new CachedS3TaskConfigService(s3TaskConfigService, s3Config);
        EcsClientPool ecsClientPool = new EcsClientPool();
        Ec2ClientPool ec2ClientPool = new Ec2ClientPool();
        EcsManager ecsManager = new EcsManagerImpl(cachedConfigService, ecsClientPool, ec2ClientPool, yamlConfig.getEcs(), yamlConfig.getAws().getRegionCities());
        return new EcsNodeService(ecsManager, yamlConfig.getEcs(), yamlConfig.getAws(), s3Config);
    }

    @Provides
    @Singleton
    AuthConfiguration provideAuthConfiguration(YamlApplicationConfiguration yamlConfig) {
        return yamlConfig.getAuth();
    }

    @Provides
    @Singleton
    Authorizer provideAuthorizer(AuthConfiguration authConfig, EntitlementsService entitlementsService, ConfigManager configManager) {
        return new WhiteListAuthorizer(authConfig, entitlementsService, configManager);
    }

    @Provides
    @Singleton
    SecretsManagerClient provideSecretsManagerClient() {
        return SecretsManagerClient.create();
    }

    @Provides
    @Singleton
    RequestAuthenticator provideRequestAuthenticator(SecretsManagerClient secretsManagerClient, ConfigManager configManager) {
        return new SecretManagerRequestAuthenticator(secretsManagerClient, configManager.getSecretTokenId());
    }

    @Provides
    @Singleton
    CommandDispatcher provideCommandDispatcher(GuiceCommandDispatcherProvider provider) {
        return provider.get();
    }

    @Provides
    @Singleton
    UpdateEventsRegistry provideUpdateEventsRegistry(ConfigManager configManager) {
        return new FSUpdateEventsRegistry(configManager.getEventTtlMillis(), configManager.getEventRootDir());
    }
}
