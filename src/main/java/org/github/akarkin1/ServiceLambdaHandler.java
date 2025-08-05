package org.github.akarkin1;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.github.akarkin1.auth.RequestAuthenticator;
import org.github.akarkin1.config.LocalConfigModule;
import org.github.akarkin1.config.ProdConfigModule;
import org.github.akarkin1.deduplication.UpdateEventsRegistry;
import org.github.akarkin1.dispatcher.CommandDispatcher;
import org.github.akarkin1.tg.BotCommunicator;
import org.github.akarkin1.tg.TgRequestContext;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Optional;

@Singleton
public class ServiceLambdaHandler implements
    RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  private static final Logger log = LogManager.getLogger(ServiceLambdaHandler.class);

  private static final ServiceLambdaHandler INSTANCE;

  static {
    String profile = System.getProperty("lambda.profile", "prod");
    Injector injector = "local".equalsIgnoreCase(profile)
        ? Guice.createInjector(new LocalConfigModule())
        : Guice.createInjector(new ProdConfigModule());
    INSTANCE = injector.getInstance(ServiceLambdaHandler.class);
  }

  // Default constructor required by AWS Lambda
  public ServiceLambdaHandler() {
    // No-op: all logic is handled by static INSTANCE
    this.mapper = null;
    this.commandDispatcher = null;
    this.communicator = null;
    this.eventsRegistry = null;
    this.requestAuthenticator = null;
    this.botServerError = null;
  }

  private final ObjectMapper mapper;
  private final CommandDispatcher commandDispatcher;
  private final BotCommunicator communicator;
  private final UpdateEventsRegistry eventsRegistry;
  private final RequestAuthenticator requestAuthenticator;
  private final String botServerError;

  @Inject
  public ServiceLambdaHandler(
      ObjectMapper mapper,
      CommandDispatcher commandDispatcher,
      BotCommunicator communicator,
      UpdateEventsRegistry eventsRegistry,
      RequestAuthenticator requestAuthenticator) {
    this.mapper = mapper;
    this.commandDispatcher = commandDispatcher;
    this.communicator = communicator;
    this.eventsRegistry = eventsRegistry;
    this.requestAuthenticator = requestAuthenticator;
    this.botServerError = "${bot.internal.error}";
  }

  @Override
  public APIGatewayProxyResponseEvent handleRequest(
      APIGatewayProxyRequestEvent gwEvent,
      Context context) {
    return INSTANCE.handleRequestInternal(gwEvent, context);
  }

  private APIGatewayProxyResponseEvent handleRequestInternal(
      APIGatewayProxyRequestEvent gwEvent,
      Context context) {

    Update update;
    try {
      log.debug("Got request: {}", serializeObject(gwEvent));
      requestAuthenticator.authenticate(gwEvent);
      String receivedPayload = gwEvent.getBody();
      log.debug("Received payload: {}", receivedPayload);
      if (StringUtils.isBlank(receivedPayload)) {
        return new APIGatewayProxyResponseEvent()
            .withBody("ECS Bot Lambda performs normally. Application version: %s "
                          .formatted(getAppVersionSafe()))
            .withStatusCode(200);
      }

      update = mapper.readValue(receivedPayload, Update.class);
    } catch (Exception e) {
      log.error("Failed to process request: ", e);
      communicator.sendMessageToTheBot(botServerError);
      return new APIGatewayProxyResponseEvent()
          .withBody("{}")
          .withStatusCode(201);
    }

    try {
      handleUpdate(update);
    } catch (Exception e) {
      log.error("Failed to handle update: ", e);
      communicator.sendMessageToTheBot(botServerError);
      return new APIGatewayProxyResponseEvent()
          .withBody("{}")
          .withStatusCode(201);
    }

    return new APIGatewayProxyResponseEvent()
        .withBody("{}")
        .withStatusCode(201);
  }

  private String serializeObject(APIGatewayProxyRequestEvent gwEvent) {
    try {
      return mapper.writeValueAsString(gwEvent);
    } catch (Exception e) {
      return gwEvent.toString();
    }
  }

  private String getAppVersionSafe() {
    try {
      // Try to get version from injected commandDispatcher or another injected bean
      // (assuming VersionCommand or similar is registered)
      return commandDispatcher != null ? commandDispatcher.getClass().getPackage().getImplementationVersion() : "unknown";
    } catch (Exception e) {
      return "unknown";
    }
  }

  private void handleUpdate(Update update) {
    if (eventsRegistry.hasAlreadyProcessed(update)) {
      log.info("Skipping duplicated event: {}", update);
      return;
    }

    TgRequestContext.initContext(update);
    log.info("Saving event to the registry (deduplication logic). Update: {}", update);
    eventsRegistry.registerEvent(update);

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
    commandDispatcher.handle(update);
  }

}
