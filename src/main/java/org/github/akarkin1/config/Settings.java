package org.github.akarkin1.config;

import org.github.akarkin1.config.YamlApplicationConfiguration.AWSConfiguration;
import org.github.akarkin1.config.YamlApplicationConfiguration.AuthConfiguration;
import org.github.akarkin1.config.YamlApplicationConfiguration.EcsConfiguration;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * Every configuration value the object graph needs, read once from the environment and
 * {@code application.yml}. Nothing else reads {@code System.getenv} or the YAML file
 * (docs/specs/cleanup-2026-10.md §4.7).
 *
 * @param configTableName      the DynamoDB config table ({@code CONFIG_TABLE_NAME}, default
 *                             {@code vpnbot})
 * @param botTokenSecretId     the Secrets Manager id of the bot token ({@code BOT_TOKEN_SECRET_ID},
 *                             may be {@code null}: resolving the token then fails)
 * @param botUsername          the bot's username ({@code BOT_USERNAME})
 * @param metricsEnabled       {@code METRICS_ENABLED}, default {@code true}
 * @param configCacheEnabled   {@code CONFIG_CACHE_ENABLED}, default {@code false}
 * @param configCacheTtl       {@code CONFIG_CACHE_TTL_SEC}, default 300 s; also the TTL of the
 *                             cached webhook secret token
 * @param appVersion           {@code version} from {@code application.yml}
 * @param auth                 the {@code auth} section of {@code application.yml}
 * @param ecs                  the {@code ecs} section of {@code application.yml}
 * @param aws                  the {@code aws} section of {@code application.yml}
 * @param webhookSecretTokenId the Secrets Manager id of the webhook secret token that
 *                             authenticates API Gateway requests ({@code BOT_SECRET_TOKEN_ID},
 *                             required)
 * @param awsRegion            the Lambda's own AWS region ({@code AWS_REGION}, set by Lambda,
 *                             default {@code eu-central-1}); the region catalog is read there
 */
public record Settings(String configTableName,
                       String botTokenSecretId,
                       String botUsername,
                       boolean metricsEnabled,
                       boolean configCacheEnabled,
                       Duration configCacheTtl,
                       String appVersion,
                       AuthConfiguration auth,
                       EcsConfiguration ecs,
                       AWSConfiguration aws,
                       String webhookSecretTokenId,
                       String awsRegion) {

  private static final String APP_CONFIG_YAML = "application.yml";

  private static final String BOT_USERNAME_ENV = "BOT_USERNAME";
  private static final String BOT_SECRET_TOKEN_ID_ENV = "BOT_SECRET_TOKEN_ID";
  private static final String CONFIG_CACHE_TTL_SEC_ENV = "CONFIG_CACHE_TTL_SEC";
  private static final String CONFIG_CACHE_ENABLED_ENV = "CONFIG_CACHE_ENABLED";
  private static final String CONFIG_TABLE_NAME_ENV = "CONFIG_TABLE_NAME";
  private static final String METRICS_ENABLED_ENV = "METRICS_ENABLED";
  private static final String BOT_TOKEN_SECRET_ID_ENV = "BOT_TOKEN_SECRET_ID";
  private static final String AWS_REGION_ENV = "AWS_REGION";

  /** Reads the process environment and {@code application.yml} from the classpath. */
  public static Settings fromEnvironment() {
    return from(System::getenv, YamlApplicationConfiguration.load(APP_CONFIG_YAML));
  }

  /** The settings for the given environment lookup and parsed {@code application.yml}. */
  static Settings from(Function<String, String> env, YamlApplicationConfiguration appConfig) {
    // read first: the request authenticator, built first, failed on it before the other values
    String webhookSecretTokenId = Optional.ofNullable(env.apply(BOT_SECRET_TOKEN_ID_ENV))
        .orElseThrow(() -> new IllegalStateException(
            "Environment variable 'BOT_SECRET_TOKEN_ID_ENV' is not set"));
    return new Settings(
        envOrDefault(env, CONFIG_TABLE_NAME_ENV, "vpnbot"),
        env.apply(BOT_TOKEN_SECRET_ID_ENV),
        env.apply(BOT_USERNAME_ENV),
        "true".equalsIgnoreCase(envOrDefault(env, METRICS_ENABLED_ENV, "true")),
        "true".equalsIgnoreCase(envOrDefault(env, CONFIG_CACHE_ENABLED_ENV, "false")),
        Duration.ofSeconds(Long.parseLong(envOrDefault(env, CONFIG_CACHE_TTL_SEC_ENV, "300"))),
        appConfig.getVersion(),
        appConfig.getAuth(),
        appConfig.getEcs(),
        appConfig.getAws(),
        webhookSecretTokenId,
        envOrDefault(env, AWS_REGION_ENV, "eu-central-1"));
  }

  private static String envOrDefault(Function<String, String> env, String name,
                                     String defaultValue) {
    return Optional.ofNullable(env.apply(name)).orElse(defaultValue);
  }

}
