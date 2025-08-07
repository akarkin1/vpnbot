package org.github.akarkin1.config.guice;

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
import org.github.akarkin1.config.CachedS3TaskConfigService;
import org.github.akarkin1.config.ConfigConstants;
import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.config.S3TaskConfigService;
import org.github.akarkin1.config.TaskConfigService;
import org.github.akarkin1.config.YamlApplicationConfiguration;
import org.github.akarkin1.config.YamlApplicationConfiguration.AuthConfiguration;
import org.github.akarkin1.config.YamlApplicationConfiguration.S3Configuration;
import org.github.akarkin1.deduplication.FSUpdateEventsRegistry;
import org.github.akarkin1.deduplication.UpdateEventsRegistry;
import org.github.akarkin1.dispatcher.CommandDispatcher;
import org.github.akarkin1.ec2.Ec2ClientProvider;
import org.github.akarkin1.ecs.EcsClientProvider;
import org.github.akarkin1.ecs.EcsManager;
import org.github.akarkin1.ecs.EcsManagerImpl;
import org.github.akarkin1.s3.S3ConfigManager;
import org.github.akarkin1.service.EcsNodeService;
import org.github.akarkin1.service.NodeService;
import org.github.akarkin1.tg.TelegramBotFactory;
import org.github.akarkin1.translation.ResourceBasedTranslator;
import org.github.akarkin1.translation.Translator;
import org.telegram.telegrambots.meta.bots.AbsSender;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import javax.inject.Singleton;

public class CommonConfigModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(ConfigConstants.class).in(Scopes.SINGLETON);
    bind(YamlApplicationConfiguration.class)
      .toProvider(YamlApplicationConfigurationProvider.class)
      .in(Scopes.SINGLETON);
    bind(CommandDispatcher.class)
      .toProvider(GuiceCommandDispatcherProvider.class)
      .in(Scopes.SINGLETON);
    bind(Translator.class).to(ResourceBasedTranslator.class).in(Scopes.SINGLETON);
  }

  @Provides
  @Singleton
  S3ConfigManager provideS3ConfigManager(S3Configuration s3Config, S3Client s3Client,
                                         ConfigManager configManager) {
    return S3ConfigManager.create(s3Config, s3Client, configManager);
  }

  @Provides
  @Singleton
  S3Configuration provideS3Configuration(YamlApplicationConfiguration yamlConfig) {
    return yamlConfig.getS3();
  }

  @Provides
  @Singleton
  EntitlementsService provideEntitlementsService(S3ConfigManager s3ConfigManager,
                                                 S3Configuration s3Config,
                                                 AuthConfiguration authConfiguration) {
    return new CachingEntitlementsService(authConfiguration, new S3EntitlementsService(s3ConfigManager, s3Config));
  }

  @Provides
  @Singleton
  AuthConfiguration provideAuthConfiguration(YamlApplicationConfiguration yamlConfig) {
    return yamlConfig.getAuth();
  }

  @Provides
  @Singleton
  Authorizer provideAuthorizer(AuthConfiguration authConfig,
                               EntitlementsService entitlementsService,
                               ConfigManager configManager) {
    return new WhiteListAuthorizer(authConfig, entitlementsService, configManager);
  }

  @Provides
  @Singleton
  RequestAuthenticator provideRequestAuthenticator(SecretsManagerClient secretsManagerClient,
                                                   ConfigManager configManager) {
    return new SecretManagerRequestAuthenticator(secretsManagerClient,
                                                 configManager.getSecretTokenId());
  }

  @Provides
  @Singleton
  AbsSender provideAbsSender(ConfigManager configManager) {
    return TelegramBotFactory.sender(configManager.getBotToken(),
                                     configManager.getBotUsername(),
                                     configManager);
  }

  @Provides
  @Singleton
  NodeService provideNodeService(YamlApplicationConfiguration yamlConfig,
                                 S3Client s3Client,
                                 ConfigManager configManager,
                                 EcsClientProvider ecsClientProvider,
                                 Ec2ClientProvider ec2ClientProvider) {
    var s3Config = yamlConfig.getS3();
    S3TaskConfigService s3TaskConfigService = S3TaskConfigService.create(s3Config, s3Client,
                                                                         configManager);
    TaskConfigService cachedConfigService = new CachedS3TaskConfigService(
      s3TaskConfigService, s3Config);
    EcsManager ecsManager = new EcsManagerImpl(cachedConfigService, ecsClientProvider, ec2ClientProvider,
                                               yamlConfig.getEcs(),
                                               yamlConfig.getAws().getRegionCities());
    return new EcsNodeService(ecsManager, yamlConfig.getEcs(), yamlConfig.getAws(), s3Config);
  }

  @Provides
  @Singleton
  UpdateEventsRegistry provideUpdateEventsRegistry(ConfigManager configManager) {
    return new FSUpdateEventsRegistry(configManager.getEventTtlMillis(),
                                      configManager.getEventRootDir());
  }
}

