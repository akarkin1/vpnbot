package org.github.akarkin1;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.dispatcher.CommandDispatcher;
import org.github.akarkin1.dispatcher.command.ListInstancesCommand;
import org.github.akarkin1.dispatcher.command.RebootServerCommand;
import org.github.akarkin1.dispatcher.command.RestartServerCommand;
import org.github.akarkin1.dispatcher.command.StartInstanceCommand;
import org.github.akarkin1.dispatcher.command.StartServerCommand;
import org.github.akarkin1.dispatcher.command.StartServerCommandV2;
import org.github.akarkin1.dispatcher.command.StopInstanceCommand;
import org.github.akarkin1.dispatcher.command.StopServerCommand;
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

import static java.lang.System.getenv;
import static org.github.akarkin1.tg.TelegramBotFactory.sender;

@Log4j2
public class LambdaHandler implements
    RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final CommandDispatcher COMMAND_DISPATCHER;
  private static final BotCommunicator COMMUNICATOR;

  static {
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
      return new APIGatewayProxyResponseEvent()
          .withBody("Failed to parse event: " + gwEvent)
          .withStatusCode(400);
    }

    try {
      handleUpdate(update);
    } catch (Exception e) {
      log.error("Failed to handle update: ", e);
      return new APIGatewayProxyResponseEvent()
          .withBody("Internal Server Error")
          .withStatusCode(500);
    }

    return new APIGatewayProxyResponseEvent()
        .withBody("{}")
        .withStatusCode(201);
  }

  private void handleUpdate(Update update) throws Exception {
    COMMUNICATOR.setChatId(update.getMessage().getChatId());
    if (update.getMessage() == null) {
      log.warn("Update message is missing. Update={}", update);
      return;
    }

    String userName = Optional.ofNullable(update.getMessage())
        .map(Message::getFrom)
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
