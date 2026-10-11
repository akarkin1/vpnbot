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
import org.github.akarkin1.auth.PendingUserActivator;
import org.github.akarkin1.auth.RequestAuthenticator;
import org.github.akarkin1.auth.RequestAuthenticatorConfigurer;
import org.github.akarkin1.auth.PermissionsService;
import org.github.akarkin1.auth.PermissionsServiceConfigurer;
import org.github.akarkin1.config.BotTokenResolver;
import org.github.akarkin1.deduplication.DynamoDbUpdateEventsRegistry;
import org.github.akarkin1.deduplication.UpdateEventsRegistry;
import org.github.akarkin1.dispatcher.CommandDispatcher;
import org.github.akarkin1.dispatcher.command.DescribeRolesCommand;
import org.github.akarkin1.dispatcher.command.SupportedRegionCommand;
import org.github.akarkin1.dispatcher.command.VersionCommand;
import org.github.akarkin1.dynamodb.ConfigTables;
import org.github.akarkin1.metrics.EmfRequestMetrics;
import org.github.akarkin1.metrics.RequestMetrics;
import org.github.akarkin1.startup.SnapStartPrimer;
import org.github.akarkin1.tailscale.NodeServices;
import org.github.akarkin1.tailscale.TailscaleEcsNodeServiceConfigurer;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.translation.ResourceBasedTranslator;
import org.github.akarkin1.translation.Translator;
import org.github.akarkin1.ui.UiComponents;
import org.github.akarkin1.ui.UiConfigurer;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.UiRouter;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.ErrorScreen;
import org.github.akarkin1.util.LambdaHandler;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.time.Clock;

import static org.github.akarkin1.config.ConfigManager.getAppVersion;
import static org.github.akarkin1.config.ConfigManager.getBotTokenSecretId;
import static org.github.akarkin1.config.ConfigManager.getBotUsernameEnv;
import static org.github.akarkin1.config.ConfigManager.getConfigTableName;
import static org.github.akarkin1.config.ConfigManager.isMetricsEnabled;
import static org.github.akarkin1.tg.TelegramBotFactory.sender;

@Log4j2
@LambdaHandler
public class TailscaleVpnLambdaHandler implements
    RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final CommandDispatcher SYSTEM_COMMANDS_DISPATCHER;
  private static final UpdateEventsRegistry EVENTS_REGISTRY;
  private static final RequestAuthenticator REQUEST_AUTHENTICATOR;
  private static final UiRouter UI_ROUTER;
  private static final UiMessenger UI_MESSENGER;
  private static final ErrorScreen ERROR_SCREEN = new ErrorScreen();
  private static final RequestMetrics METRICS;
  private static final PendingUserActivator PENDING_USER_ACTIVATOR;

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
    final NodeServices nodeServices = new TailscaleEcsNodeServiceConfigurer()
        .configure(configTables.regions(), METRICS);
    final TailscaleNodeService nodeService = nodeServices.nodeService();
    final PermissionsService permissionsService = new PermissionsServiceConfigurer()
        .configure(configTables.users(), configTables.pendingUsers(), METRICS);
    final Authorizer authorizer = new AuthorizerConfigurer().configure(permissionsService);
    PENDING_USER_ACTIVATOR = new PendingUserActivator(permissionsService);

    final Translator translator = new ResourceBasedTranslator();

    final UiComponents ui = new UiConfigurer().configure(sender, translator, nodeService,
                                                         authorizer, permissionsService,
                                                         configTables.accessRequests(), METRICS);
    UI_ROUTER = ui.router();
    UI_MESSENGER = ui.messenger();

    SYSTEM_COMMANDS_DISPATCHER = new CommandDispatcher(UI_MESSENGER, authorizer);

    SYSTEM_COMMANDS_DISPATCHER.registerCommand("/version", new VersionCommand(getAppVersion()));
    SYSTEM_COMMANDS_DISPATCHER.registerCommand("/supportedRegions",
                                       new SupportedRegionCommand(nodeService));
    SYSTEM_COMMANDS_DISPATCHER.registerCommand("/describeRoles", new DescribeRolesCommand());

    new SnapStartPrimer(nodeService, nodeServices.ec2ClientPool(), permissionsService,
                        EVENTS_REGISTRY, sender).prime();
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
      // no chat to answer before the update is read (D-5): logged only
      log.error("Failed to process request: ", e);
      return new APIGatewayProxyResponseEvent()
          .withBody("{}")
          .withStatusCode(201);
    }

    METRICS.start(updateKind(update));
    try {
      handleUpdate(update);
    } catch (Exception e) {
      log.error("Failed to handle update: ", e);
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

    UiContext context;
    try {
      context = UiContext.fromUpdate(update);
    } catch (IllegalArgumentException e) {
      log.warn("Dropping an update with neither a message nor a callback query: {}", update);
      return;
    }

    try {
      // before any permission check: a pre-registered user gets their USER record (D-24)
      PENDING_USER_ACTIVATOR.activate(context.user());

      if (UI_ROUTER.canHandle(update)) {
        UI_ROUTER.handle(update);
        return;
      }

      log.info("User {} sent a system command", context.user());
      SYSTEM_COMMANDS_DISPATCHER.handle(context, update.getMessage().getText());
    } catch (Exception e) {
      log.error("Failed to handle update: {}", update, e);
      sendGenericError(context);
    }
  }

  private static void sendGenericError(UiContext context) {
    try {
      UI_MESSENGER.send(context, ERROR_SCREEN.generic());
    } catch (Exception e) {
      log.error("Failed to send the error message to chat {}", context.chatId(), e);
    }
  }

}
