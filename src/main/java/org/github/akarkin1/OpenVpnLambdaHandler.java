package org.github.akarkin1;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.deduplication.FSUpdateEventsRegistry;
import org.github.akarkin1.deduplication.UpdateEventsRegistry;
import org.github.akarkin1.dispatcher.CommandDispatcher;
import org.github.akarkin1.dispatcher.command.ListInstancesCommand;
import org.github.akarkin1.dispatcher.command.RebootServerCommand;
import org.github.akarkin1.dispatcher.command.RestartServerCommand;
import org.github.akarkin1.dispatcher.command.StartInstanceCommand;
import org.github.akarkin1.dispatcher.command.StartServerCommandV2;
import org.github.akarkin1.dispatcher.command.StopInstanceCommand;
import org.github.akarkin1.dispatcher.command.StopServerCommandV2;
import org.github.akarkin1.dispatcher.command.VersionCommand;
import org.github.akarkin1.ec2.Ec2ClientPool;
import org.github.akarkin1.ec2.Ec2Manager;
import org.github.akarkin1.ec2.EnvBasedRegionService;
import org.github.akarkin1.tg.BotCommunicator;
import org.github.akarkin1.tg.TgRequestContext;
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
public class OpenVpnLambdaHandler implements
    RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final CommandDispatcher COMMAND_DISPATCHER;
  private static final BotCommunicator COMMUNICATOR;
  private static final UpdateEventsRegistry EVENTS_REGISTRY;
  private static final String BOT_SERVER_ERROR = "The request finished with an error. Please reach "
      + "out @karkin_ai or check CW logs, if you are an admin";

  static {
    EVENTS_REGISTRY = new FSUpdateEventsRegistry(getEventTtlSec(), getEventRootDir());

    final AbsSender sender = sender(getBotToken(), getBotUsernameEnv());

    val ec2ClientProvider = new Ec2ClientPool();
    val ec2Manager = new Ec2Manager(ec2ClientProvider, EnvBasedRegionService.INSTANCE);
    COMMUNICATOR = new BotCommunicator(sender);
    COMMAND_DISPATCHER = new CommandDispatcher(COMMUNICATOR);

    COMMAND_DISPATCHER.registerCommand("/version", new VersionCommand());
    COMMAND_DISPATCHER.registerCommand("/servers", new ListInstancesCommand(ec2Manager));
    COMMAND_DISPATCHER.registerCommand("/startServer",
                                       new StartServerCommandV2(ec2Manager,
                                                                COMMUNICATOR::sendMessageToTheBot));
    COMMAND_DISPATCHER.registerCommand("/stopServer",
                                       new StopServerCommandV2(ec2Manager,
                                                               COMMUNICATOR::sendMessageToTheBot));
    COMMAND_DISPATCHER.registerCommand("/rebootServer",
                                       new RebootServerCommand(ec2Manager));
    COMMAND_DISPATCHER.registerCommand("/restartServer",
                                       new RestartServerCommand(ec2Manager,
                                                                COMMUNICATOR::sendMessageToTheBot));
    COMMAND_DISPATCHER.registerCommand("/startInstance",
                                       new StartInstanceCommand(ec2Manager));
    COMMAND_DISPATCHER.registerCommand("/stopInstance", new StopInstanceCommand(ec2Manager));
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
            .withBody("Open VPN Configurer performs normally. Application version: %s "
                          .formatted(getAppVersion()))
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

    TgRequestContext.initContext(update);
    Message message = update.getMessage();
    if (message == null) {
      log.warn("Empty message received from a user. Update Event: {}", update);
      return;
    }

    String userName = Optional.ofNullable(message.getFrom())
        .map(User::getUserName)
        .orElse("<Unknown>");
    log.info("User {} has started communication with the bot", userName);
    COMMAND_DISPATCHER.handle(update);
  }

}
