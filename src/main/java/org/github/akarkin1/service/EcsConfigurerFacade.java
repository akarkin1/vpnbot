package org.github.akarkin1.service;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.auth.RequestAuthenticator;
import org.github.akarkin1.auth.UnauthenticatedRequestException;
import org.github.akarkin1.deduplication.UpdateEventsRegistry;
import org.github.akarkin1.dispatcher.CommandDispatcher;
import org.github.akarkin1.tg.BotCommunicator;
import org.github.akarkin1.tg.TgRequestContext;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Optional;

@Log4j2
public class EcsConfigurerFacade {

  private static final String BOT_INTERNAL_ERROR = "${bot.internal.error}";

  private final ObjectMapper mapper;
  private final CommandDispatcher commandDispatcher;
  private final BotCommunicator communicator;
  private final UpdateEventsRegistry eventsRegistry;
  private final RequestAuthenticator requestAuthenticator;

  @Inject
  public EcsConfigurerFacade(ObjectMapper mapper, CommandDispatcher commandDispatcher,
                             BotCommunicator communicator, UpdateEventsRegistry eventsRegistry,
                             RequestAuthenticator requestAuthenticator) {
    this.mapper = mapper;
    this.commandDispatcher = commandDispatcher;
    this.communicator = communicator;
    this.eventsRegistry = eventsRegistry;
    this.requestAuthenticator = requestAuthenticator;
  }

  public void processGatewayEvent(APIGatewayProxyRequestEvent gwEvent) {

    Update update;
    try {
      log.debug("Got request: {}", serializeObject(gwEvent));
      String receivedPayload = gwEvent.getBody();
      log.debug("Received payload: {}", receivedPayload);
      if (StringUtils.isBlank(receivedPayload)) {
        log.warn("Received empty payload");
        return;
      }

      // Parse update first to initialize context, so we can respond even if unauthorized
      update = mapper.readValue(receivedPayload, Update.class);
      TgRequestContext.initContext(update);

      // Authenticate request after we have a chat context for responses
      requestAuthenticator.authenticate(gwEvent);

    } catch (UnauthenticatedRequestException e) {
      log.warn("Unauthenticated request: {}", e.getMessage());
      // Inform the user explicitly that the request is not authorized
      communicator.sendMessageToTheBot("The request is not authorized");
      return;
    } catch (Exception e) {
      log.error("Failed to process request: ", e);
      communicator.sendMessageToTheBot(BOT_INTERNAL_ERROR);
      return;
    }

    try {
      handleUpdate(update);
    } catch (Exception e) {
      log.error("Failed to handle update: ", e);
      communicator.sendMessageToTheBot(BOT_INTERNAL_ERROR);
    }
  }

  private String serializeObject(APIGatewayProxyRequestEvent gwEvent) {
    try {
      return mapper.writeValueAsString(gwEvent);
    } catch (Exception e) {
      return gwEvent.toString();
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
