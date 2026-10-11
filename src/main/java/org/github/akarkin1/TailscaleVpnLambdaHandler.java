package org.github.akarkin1;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.ErrorScreen;
import org.github.akarkin1.util.LambdaHandler;
import org.telegram.telegrambots.meta.api.objects.Update;

@Log4j2
@LambdaHandler
public class TailscaleVpnLambdaHandler implements
    RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final ErrorScreen ERROR_SCREEN = new ErrorScreen();
  /** Built at class init, so SnapStart snapshots the primed graph. */
  private static final VpnBot BOT = VpnBot.create();

  @Override
  public APIGatewayProxyResponseEvent handleRequest(
      APIGatewayProxyRequestEvent gwEvent,
      Context context) {
    try {
      return processRequest(gwEvent);
    } finally {
      BOT.metrics().finish();
    }
  }

  private APIGatewayProxyResponseEvent processRequest(APIGatewayProxyRequestEvent gwEvent) {
    Update update;
    try {
      log.debug("Got request: {}", serializeObject(gwEvent));
      BOT.requestAuthenticator().authenticate(gwEvent);
      String receivedPayload = gwEvent.getBody();
      log.debug("Received payload: {}", receivedPayload);
      if (StringUtils.isBlank(receivedPayload)) {
        return new APIGatewayProxyResponseEvent()
            .withBody("VPN Bot Lambda performs noramlly. Application version: %s "
                          .formatted(BOT.settings().appVersion()))
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

    BOT.metrics().start(updateKind(update));
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
    return isCommand && !BOT.router().canHandle(update) ? "Command" : "Message";
  }

  private static String serializeObject(APIGatewayProxyRequestEvent gwEvent) {
    try {
      return MAPPER.writeValueAsString(gwEvent);
    } catch (Exception e) {
      return gwEvent.toString();
    }
  }

  private void handleUpdate(Update update) {
    if (!BOT.updateEvents().register(update)) {
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
      BOT.pendingUserActivator().activate(context.user());

      if (BOT.router().canHandle(update)) {
        BOT.router().handle(update);
        return;
      }

      log.info("User {} sent a system command", context.user());
      BOT.systemCommands().handle(context, update.getMessage().getText());
    } catch (Exception e) {
      log.error("Failed to handle update: {}", update, e);
      sendGenericError(context);
    }
  }

  private static void sendGenericError(UiContext context) {
    try {
      BOT.messenger().send(context, ERROR_SCREEN.generic());
    } catch (Exception e) {
      log.error("Failed to send the error message to chat {}", context.chatId(), e);
    }
  }

}
