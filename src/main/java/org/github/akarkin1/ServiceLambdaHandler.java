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
import org.github.akarkin1.auth.s3.PermissionsService;
import org.github.akarkin1.auth.s3.PermissionsServiceConfigurer;
import org.github.akarkin1.deduplication.FSUpdateEventsRegistry;
import org.github.akarkin1.deduplication.UpdateEventsRegistry;
import org.github.akarkin1.dispatcher.command.AssignRolesCommand;
import org.github.akarkin1.dispatcher.CommandDispatcher;
import org.github.akarkin1.dispatcher.command.DeleteUsersCommand;
import org.github.akarkin1.dispatcher.command.DescribeRolesCommand;
import org.github.akarkin1.dispatcher.command.ListNodesCommand;
import org.github.akarkin1.dispatcher.command.ListServicesCommand;
import org.github.akarkin1.dispatcher.command.ListUsersCommand;
import org.github.akarkin1.dispatcher.command.RunNodeCommand;
import org.github.akarkin1.dispatcher.command.SupportedRegionCommand;
import org.github.akarkin1.dispatcher.command.VersionCommand;
import org.github.akarkin1.service.EcsNodeServiceConfigurer;
import org.github.akarkin1.service.NodeService;
import org.github.akarkin1.tg.BotCommunicator;
import org.github.akarkin1.tg.TgRequestContext;
import org.github.akarkin1.translation.ResourceBasedTranslator;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.bots.AbsSender;

import java.util.Optional;

import static org.github.akarkin1.config.ConfigManager.getAppVersion;
import static org.github.akarkin1.config.ConfigManager.getBotToken;
import static org.github.akarkin1.config.ConfigManager.getBotUsernameEnv;
import static org.github.akarkin1.config.ConfigManager.getEventRootDir;
import static org.github.akarkin1.config.ConfigManager.getEventTtlSec;
import static org.github.akarkin1.tg.TelegramBotFactory.sender;

@Log4j2
public class ServiceLambdaHandler implements
    RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final CommandDispatcher COMMAND_DISPATCHER;
  private static final BotCommunicator COMMUNICATOR;
  private static final UpdateEventsRegistry EVENTS_REGISTRY;
  private static final String BOT_SERVER_ERROR = "${bot.internal.error}";
  private static final RequestAuthenticator REQUEST_AUTHENTICATOR;

  static {
    REQUEST_AUTHENTICATOR = new RequestAuthenticatorConfigurer().configure();

    EVENTS_REGISTRY = new FSUpdateEventsRegistry(getEventTtlSec(), getEventRootDir());

    final AbsSender sender = sender(getBotToken(), getBotUsernameEnv());
    final NodeService nodeService = new EcsNodeServiceConfigurer().configure();
    final PermissionsService permissionsService = new PermissionsServiceConfigurer().configure();
    final Authorizer authorizer = new AuthorizerConfigurer().configure(permissionsService);

    COMMUNICATOR = new BotCommunicator(sender, new ResourceBasedTranslator());
    COMMAND_DISPATCHER = new CommandDispatcher(COMMUNICATOR, authorizer);

    COMMAND_DISPATCHER.registerCommand("/version", new VersionCommand());
    COMMAND_DISPATCHER.registerCommand("/listRunningNodes", new ListNodesCommand(
        nodeService, authorizer));
    COMMAND_DISPATCHER.registerCommand("/runNode",
                                       new RunNodeCommand(nodeService,
                                                          COMMUNICATOR::sendMessageToTheBot));
    COMMAND_DISPATCHER.registerCommand("/supportedRegions",
                                       new SupportedRegionCommand(nodeService));
    COMMAND_DISPATCHER.registerCommand("/listServices",
                                       new ListServicesCommand(nodeService));
    COMMAND_DISPATCHER.registerCommand("/assignRoles",
                                       new AssignRolesCommand(permissionsService));
    COMMAND_DISPATCHER.registerCommand("/describeRoles",
                                       new DescribeRolesCommand(permissionsService));
    COMMAND_DISPATCHER.registerCommand("/deleteUsers",
                                       new DeleteUsersCommand(permissionsService,
                                                              COMMUNICATOR::sendMessageToTheBot));
    COMMAND_DISPATCHER.registerCommand("/listRegisteredUsers",
                                       new ListUsersCommand(permissionsService));
  }

  @Override
  public APIGatewayProxyResponseEvent handleRequest(
      APIGatewayProxyRequestEvent gwEvent,
      Context context) {

    Update update;
    try {
      log.debug("Got request: {}", serializeObject(gwEvent));
      REQUEST_AUTHENTICATOR.authenticate(gwEvent);
      String receivedPayload = gwEvent.getBody();
      log.debug("Received payload: {}", receivedPayload);
      if (StringUtils.isBlank(receivedPayload)) {
        return new APIGatewayProxyResponseEvent()
            .withBody("Service Bot Lambda performs normally. Application version: %s "
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

  private static String serializeObject(APIGatewayProxyRequestEvent gwEvent) {
    try {
      return MAPPER.writeValueAsString(gwEvent);
    } catch (Exception e) {
      return gwEvent.toString();
    }
  }

  private void handleUpdate(Update update) {
    if (EVENTS_REGISTRY.hasAlreadyProcessed(update)) {
      log.info("Skipping duplicated event: {}", update);
      return;
    }

    TgRequestContext.initContext(update);
    log.info("Saving event to the registry (deduplication logic). Update: {}", update);
    EVENTS_REGISTRY.registerEvent(update);

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
