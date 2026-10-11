package org.github.akarkin1;

import org.github.akarkin1.config.Settings;
import org.telegram.telegrambots.meta.bots.AbsSender;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.time.Clock;
import java.util.function.Function;

/**
 * Everything {@link VpnBot} takes from outside the process: the clients, the clock and the
 * settings. {@link VpnBot#create()} passes the real ones; the wiring test passes mocks.
 *
 * @param dynamoDb      the client of the config table
 * @param secrets       reads the bot token and the webhook secret token
 * @param senderFactory bot token → Telegram sender (the bot username is bound by the factory)
 * @param clock         the clock of every TTL and timestamp
 * @param settings      the environment and {@code application.yml}
 * @param ecsClients    region → a new ECS client of that region (pooled by the ECS layer)
 * @param ec2Clients    region → a new EC2 client of that region (pooled by the ECS layer)
 */
public record Externals(DynamoDbEnhancedClient dynamoDb,
                        SecretsManagerClient secrets,
                        Function<String, AbsSender> senderFactory,
                        Clock clock,
                        Settings settings,
                        Function<Region, EcsClient> ecsClients,
                        Function<Region, Ec2Client> ec2Clients) {

}
