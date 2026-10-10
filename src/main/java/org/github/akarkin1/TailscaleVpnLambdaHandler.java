package org.github.akarkin1;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.AuthorizerConfigurer;
import org.github.akarkin1.auth.RequestAuthenticator;
import org.github.akarkin1.auth.RequestAuthenticatorConfigurer;
import org.github.akarkin1.auth.PermissionsService;
import org.github.akarkin1.auth.PermissionsServiceConfigurer;
import org.github.akarkin1.config.BotTokenResolver;
import org.github.akarkin1.deduplication.DynamoDbUpdateEventsRegistry;
import org.github.akarkin1.deduplication.UpdateEventsRegistry;
import org.github.akarkin1.dispatcher.command.AssignRolesCommand;
import org.github.akarkin1.dispatcher.CommandDispatcher;
import org.github.akarkin1.dispatcher.command.DeleteUsersCommand;
import org.github.akarkin1.dispatcher.command.DescribeRolesCommand;
import org.github.akarkin1.dispatcher.command.ListNodesCommand;
import org.github.akarkin1.dispatcher.command.ListUsersCommand;
import org.github.akarkin1.dispatcher.command.RunNodeCommand;
import org.github.akarkin1.dispatcher.command.SupportedRegionCommand;
import org.github.akarkin1.dispatcher.command.VersionCommand;
import org.github.akarkin1.dynamodb.ConfigTables;
import org.github.akarkin1.metrics.EmfRequestMetrics;
import org.github.akarkin1.metrics.RequestMetrics;
import org.github.akarkin1.startup.SnapStartPrimer;
import org.github.akarkin1.tailscale.TailscaleEcsNodeServiceConfigurer;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.tg.BotCommunicator;
import org.github.akarkin1.tg.TgRequestContext;
import org.github.akarkin1.translation.ResourceBasedTranslator;
import org.github.akarkin1.translation.Translator;
import org.github.akarkin1.ui.UiComponents;
import org.github.akarkin1.ui.UiConfigurer;
import org.github.akarkin1.ui.UiRouter;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.bots.AbsSender;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.time.Clock;
import java.util.Optional;

import static org.github.akarkin1.config.ConfigManager.getAppVersion;
import static org.github.akarkin1.config.ConfigManager.getBotTokenSecretId;
import static org.github.akarkin1.config.ConfigManager.getBotUsernameEnv;
import static org.github.akarkin1.config.ConfigManager.getConfigTableName;
import static org.github.akarkin1.config.ConfigManager.isMetricsEnabled;
import static org.github.akarkin1.tg.TelegramBotFactory.sender;

@Log4j2
public class TailscaleVpnLambdaHandler implements
    RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final CommandDispatcher COMMAND_DISPATCHER;
  private static final BotCommunicator COMMUNICATOR;
  private static final UpdateEventsRegistry EVENTS_REGISTRY;
  private static final String BOT_SERVER_ERROR = "${bot.internal.error}";
  private static final RequestAuthenticator REQUEST_AUTHENTICATOR;
  private static final UiRouter UI_ROUTER;
  private static final RequestMetrics METRICS;

  static {
    METRICS = new EmfRequestMetrics(isMetricsEnabled(), Clock.systemUTC(), System.out::println);
    REQUEST_AUTHENTICATOR = new RequestAuthenticatorConfigurer().configure();

    final DynamoDbEnhancedClient dynamoDb = DynamoDbEnhancedClient.builder()
        .dynamoDbClient(DynamoDbClient.create())
        .build();
    final ConfigTables configTables = ConfigTables.create(dynamoDb, getConfigTableName());
    EVENTS_REGISTRY = new DynamoDbUpdateEventsRegistry(configTables.updateLocks(),
                                                       Clock.systemUTC(), METRICS);

    final String botToken = new BotTokenResolver(SecretsManagerClient.create())
        .resolve(getBotTokenSecretId());
    final AbsSender sender = sender(botToken, getBotUsernameEnv());
    final TailscaleNodeService nodeService = new TailscaleEcsNodeServiceConfigurer()
        .configure(configTables.regions(), METRICS);
    final PermissionsService permissionsService = new PermissionsServiceConfigurer()
        .configure(configTables.users(), METRICS);
    final Authorizer authorizer = new AuthorizerConfigurer().configure(permissionsService);

    final Translator translator = new ResourceBasedTranslator();

    final UiComponents ui = new UiConfigurer().configure(sender, translator, nodeService,
                                                         authorizer, METRICS);
    UI_ROUTER = ui.router();

    COMMUNICATOR = new BotCommunicator(sender, translator, METRICS);
    COMMAND_DISPATCHER = new CommandDispatcher(COMMUNICATOR, authorizer);

    COMMAND_DISPATCHER.registerCommand("/version", new VersionCommand());
    COMMAND_DISPATCHER.registerCommand("/listRunningNodes", new ListNodesCommand(
        nodeService, authorizer));
    COMMAND_DISPATCHER.registerCommand("/runNodeIn",
                                       new RunNodeCommand(nodeService,
                                                          COMMUNICATOR::sendMessageToTheBot,
                                                          ui.nodeLauncher()));
    COMMAND_DISPATCHER.registerCommand("/supportedRegions",
                                       new SupportedRegionCommand(nodeService));
    COMMAND_DISPATCHER.registerCommand("/assignRoles",
                                       new AssignRolesCommand(permissionsService));
    COMMAND_DISPATCHER.registerCommand("/describeRoles",
                                       new DescribeRolesCommand(permissionsService));
    COMMAND_DISPATCHER.registerCommand("/deleteUsers",
                                       new DeleteUsersCommand(permissionsService,
                                                              COMMUNICATOR::sendMessageToTheBot));
    COMMAND_DISPATCHER.registerCommand("/listRegisteredUsers",
                                       new ListUsersCommand(permissionsService));

    new SnapStartPrimer(nodeService, permissionsService, EVENTS_REGISTRY, sender).prime();
  }

  @Override
  public APIGatewayProxyResponseEvent handleRequest(
      APIGatewayProxyRequestEvent gwEvent,
      Context context) {
    try {
      return processRequest(gwEvent);
    } finally {
      METRICS.finish();
    }
  }

  private APIGatewayProxyResponseEvent processRequest(APIGatewayProxyRequestEvent gwEvent) {
    Update update;
    try {
      log.debug("Got request: {}", serializeObject(gwEvent));
      REQUEST_AUTHENTICATOR.authenticate(gwEvent);
      String receivedPayload = gwEvent.getBody();
      log.debug("Received payload: {}", receivedPayload);
      if (StringUtils.isBlank(receivedPayload)) {
        return new APIGatewayProxyResponseEvent()
            .withBody("VPN Bot Lambda performs noramlly. Application version: %s "
                          .formatted(getAppVersion()))
            .withStatusCode(200);
      }

      update = MAPPER.readValue(receivedPayload, Update.class);
    } catch (Exception e) {
      log.error("Failed to process request: ", e);
      COMMUNICATOR.sendMessageToTheBot(BOT_SERVER_ERROR);
      return new APIGatewayProxyResponseEvent()
          .withBody("{}")
          .withStatusCode(201);
    }

    METRICS.start(updateKind(update));
    try {
      handleUpdate(update);
    } catch (Exception e) {
      log.error("Failed to handle update: ", e);
      COMMUNICATOR.sendMessageToTheBot(BOT_SERVER_ERROR);
      return new APIGatewayProxyResponseEvent()
          .withBody("{}")
          .withStatusCode(201);
    }

    return new APIGatewayProxyResponseEvent()
        .withBody("{}")
        .withStatusCode(201);
  }

  private static String updateKind(Update update) {
    if (update.hasCallbackQuery()) {
      return "Callback";
    }
    boolean isCommand = update.hasMessage()
                        && update.getMessage().hasText()
                        && update.getMessage().getText().startsWith("/");
    return isCommand && !UI_ROUTER.canHandle(update) ? "Command" : "Message";
  }

  private static String serializeObject(APIGatewayProxyRequestEvent gwEvent) {
    try {
      return MAPPER.writeValueAsString(gwEvent);
    } catch (Exception e) {
      return gwEvent.toString();
    }
  }

  private void handleUpdate(Update update) {
    if (!EVENTS_REGISTRY.register(update)) {
      log.info("Skipping duplicated event: {}", update);
      return;
    }

    TgRequestContext.initContext(update);

    if (UI_ROUTER.canHandle(update)) {
      UI_ROUTER.handle(update);
      return;
    }

    Message message = update.getMessage();
    if (message == null) {
      log.warn("Empty message received from a user. Update Event: {}", update);
      return;
    }

    if (message.getChatId() == null) {
      log.warn("Chat ID is missing. The bot cannot sent response back to user. Update Event: {}",
               update);
      return;
    }

    String userName = Optional.ofNullable(message.getFrom())
        .map(User::getUserName)
        .orElse("<Unknown>");
    log.info("User {} has started communication with the bot", userName);
    COMMAND_DISPATCHER.handle(update);
  }

}
