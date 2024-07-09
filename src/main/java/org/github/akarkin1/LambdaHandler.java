package org.github.akarkin1;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.deduplication.UpdateEventsRegistry;
import org.github.akarkin1.deduplication.FSUpdateEventsRegistry;
import org.github.akarkin1.dispatcher.CommandDispatcher;
import org.github.akarkin1.dispatcher.command.ListInstancesCommand;
import org.github.akarkin1.dispatcher.command.RebootServerCommand;
import org.github.akarkin1.dispatcher.command.RestartServerCommand;
import org.github.akarkin1.dispatcher.command.StartInstanceCommand;
import org.github.akarkin1.dispatcher.command.StartServerCommandV2;
import org.github.akarkin1.dispatcher.command.StopInstanceCommand;
import org.github.akarkin1.dispatcher.command.StopServerCommandV2;
import org.github.akarkin1.dispatcher.command.TextCommandResponse;
import org.github.akarkin1.dispatcher.command.VersionCommand;
import org.github.akarkin1.ec2.Ec2ClientPool;
import org.github.akarkin1.tg.BotCommunicator;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.bots.AbsSender;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.lang.System.getenv;
import static org.github.akarkin1.tg.TelegramBotFactory.sender;

@Log4j2
public class LambdaHandler implements
    RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final CommandDispatcher COMMAND_DISPATCHER;
  private static final BotCommunicator COMMUNICATOR;
  private static final UpdateEventsRegistry EVENTS_REGISTRY;
  private static final String BOT_SERVER_ERROR = "The request finished with an error. Please reach "
      + "out @karkin_ai or check CW logs, if you are an admin";

  static {
    String envRegEventExpirationTime = getenv("REGISTERED_EVENT_EXPIRATION_TIME_SEC");
    long eventExpirationTimeMs = TimeUnit.SECONDS.toMillis(Long.parseLong(
        envRegEventExpirationTime));
    EVENTS_REGISTRY = new FSUpdateEventsRegistry(eventExpirationTimeMs);

    final AbsSender sender = sender(getenv("BOT_TOKEN"), getenv("BOT_USERNAME"));

    val ec2ClientProvider = new Ec2ClientPool();
    COMMUNICATOR = new BotCommunicator(sender);
    COMMAND_DISPATCHER = new CommandDispatcher(COMMUNICATOR);

    COMMAND_DISPATCHER.registerCommand("/version", new VersionCommand());
    COMMAND_DISPATCHER.registerCommand("/servers", new ListInstancesCommand(ec2ClientProvider));
    COMMAND_DISPATCHER.registerCommand("/startServer",
                                       new StartServerCommandV2(ec2ClientProvider,
                                                                COMMUNICATOR::sendMessageToTheBot));
    COMMAND_DISPATCHER.registerCommand("/stopServer",
                                       new StopServerCommandV2(ec2ClientProvider,
                                                               COMMUNICATOR::sendMessageToTheBot));
    COMMAND_DISPATCHER.registerCommand("/rebootServer",
                                       new RebootServerCommand(ec2ClientProvider));
    COMMAND_DISPATCHER.registerCommand("/restartServer",
                                       new RestartServerCommand(ec2ClientProvider,
                                                                COMMUNICATOR::sendMessageToTheBot));
    // Legacy version of the commands
//    COMMAND_DISPATCHER.registerCommand("/startServer",
//                                       new StartServerCommand(ec2ClientProvider));
//    COMMAND_DISPATCHER.registerCommand("/stopServer",
//                                       new StopServerCommand(ec2ClientProvider));
    COMMAND_DISPATCHER.registerCommand("/startInstance",
                                       new StartInstanceCommand(ec2ClientProvider));
    COMMAND_DISPATCHER.registerCommand("/stopInstance", new StopInstanceCommand(ec2ClientProvider));
  }

  @Override
  public APIGatewayProxyResponseEvent handleRequest(
      APIGatewayProxyRequestEvent gwEvent,
      Context context) {
    Update update;
    try {
      String receivedPayload = gwEvent.getBody();
      log.debug("Received payload: {}", receivedPayload);
      if (StringUtils.isBlank(receivedPayload)) {
        return new APIGatewayProxyResponseEvent()
            .withBody("Open VPN Configurer performs normally. %s ".formatted(getAppVersion()))
            .withStatusCode(200);
      }

      update = MAPPER.readValue(receivedPayload, Update.class);
    } catch (Exception e) {
      log.error("Failed to parse update: ", e);
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

  private void handleUpdate(Update update) {
    if (EVENTS_REGISTRY.hasAlreadyProcessed(update)) {
      log.info("Skipping duplicated event: {}", update);
      return;
    }

    log.info("Saving event to the registry (deduplication logic). Update: {}", update);
    EVENTS_REGISTRY.registerEvent(update);

    Message message = update.getMessage();
    if (message == null) {
      log.warn("Empty message received from a user. Update Event: {}", update);
      return;
    }

    if(message.getChatId() == null) {
      log.warn("Chat ID is missing. The bot cannot sent response back to user. Update Event: {}", update);
      return;
    }

    COMMUNICATOR.setChatId(message.getChatId());

    String userName = Optional.ofNullable(message.getFrom())
        .map(User::getUserName)
        .orElse("<Unknown>");
    log.info("User {} has started communication with the bot", userName);
    COMMAND_DISPATCHER.handle(update);
  }

  private static String getAppVersion() {
    VersionCommand versionCommand = new VersionCommand();
    TextCommandResponse response = versionCommand.run();
    return response.text();
  }
}
