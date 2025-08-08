package org.github.akarkin1.it.steps;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.github.akarkin1.EcsConfigurerLambdaHandler;
import org.github.akarkin1.it.init.WireMockInitializer;
import org.github.akarkin1.util.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.and;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.apache.commons.io.FilenameUtils.wildcardMatch;
import static org.github.akarkin1.it.init.SMInitializer.TEST_TG_SECRET_TOKEN;
import static org.junit.Assert.fail;

public class BaseInfraSteps extends InitTestContainers {

  protected static @NotNull APIGatewayProxyRequestEvent createUpdateEvent(String runBy,
                                                                          String command) {
    APIGatewayProxyRequestEvent gwEvent = new APIGatewayProxyRequestEvent();

    gwEvent.setHeaders(Map.of("x-telegram-bot-api-secret-token", TEST_TG_SECRET_TOKEN));

    Update update = new Update();
    update.setUpdateId(Math.abs(new Random().nextInt() / 2 - 1));
    Message userMsg = new Message();
    userMsg.setMessageId(111);
    Chat chat = new Chat();
    chat.setId(Long.parseLong(WireMockInitializer.TG_TEST_CHAT_ID));
    userMsg.setChat(chat);
    userMsg.setText(command);

    User fromUser = new User();
    fromUser.setUserName(runBy);
    fromUser.setId(1L);
    fromUser.setLanguageCode("en-US");
    userMsg.setFrom(fromUser);
    update.setMessage(userMsg);

    gwEvent.setBody(JsonUtils.toJson(update));

    return gwEvent;
  }


  protected void theBotReceivesTheResponseMatchingLinesInAnyOrder(String multilineResponse) {
    theBotReceivesTheResponseMatching(multilineResponse, this::matchesLinesInAnyOrder);
  }

  private boolean matchesLinesInAnyOrder(String responseText, String[] expectedLines) {
    Set<String> responseLines = Stream.of(responseText.split(System.lineSeparator()))
      .map(String::trim)
      .collect(Collectors.toSet());

    for (String expectedLine : expectedLines) {
      String expectedLineSan = expectedLine.trim();
      if (responseLines.contains(expectedLineSan)) {
        continue;
      }

      boolean hasAnyMatchingLine = responseLines.stream()
        .anyMatch(actualLine -> wildcardMatch(actualLine, expectedLineSan));

      if (!hasAnyMatchingLine) {
        return false;
      }
    }

    return true;
  }

  protected void theBotReceivesTheResponseMatchingLineByLine(String multilineResponse) {
    theBotReceivesTheResponseMatching(multilineResponse, this::matchesLineByLine);
  }

  protected void theBotReceivesTheResponseMatching(String multilineResponse, BiPredicate<String, String[]> matcher) {
    String[] lines = multilineResponse.split(System.lineSeparator());
    List<String> sentResponses = catchWireMockRequestStartingWith(lines[0].trim());

    List<String> foundResponses = new ArrayList<>();
    for (String resp : sentResponses) {
      if (matcher.test(resp, lines)) {
        foundResponses.add(resp);
      }
    }

    if (foundResponses.isEmpty()) {
      fail("No response was sent to bot with provided content:%n[%s].%nThe most closest matches:%n%s".formatted(
        multilineResponse, sentResponses));
    } else if (foundResponses.size() > 1) {
      fail("More than one request was sent with provided content:%n%s.Matching responses:%n%s".formatted(
        multilineResponse, sentResponses));
    }
  }

  private List<String> catchWireMockRequestStartingWith(String startingWithText) {
    List<LoggedRequest> loggedRequests = WireMock.findAll(
      postRequestedFor(urlMatching("/bot.*/sendmessage"))
        .withRequestBody(
          and(
            matchingJsonPath("$.text", matching("^%s.*?$".formatted(startingWithText))),
            matchingJsonPath("$.chat_id", equalTo(WireMockInitializer.TG_TEST_CHAT_ID)
            )
          ))
    );

    return loggedRequests.stream()
      .map(LoggedRequest::getBodyAsString)
      .map(BaseInfraSteps::extractRequestText)
      .toList();
  }

  private static String extractRequestText(String request) {
    JsonNode tgRequest = JsonUtils.parseJson(request, JsonNode.class);
    return tgRequest.get("text").asText();
  }

  private boolean matchesLineByLine(String responseText, String[] expectedLines) {
    String[] responseLines = responseText.split(System.lineSeparator());

    if (responseLines.length != expectedLines.length) {
      return false;
    }

    for (int i = 0; i < expectedLines.length; i++) {
      String expectedLine = expectedLines[i].trim();
      String actualLine = responseLines[i].trim();

      if (!expectedLine.equals(actualLine) && !expectedLine.contains("*") && !wildcardMatch(actualLine, expectedLine)) {
        return false;
      }
    }

    return true;
  }

  protected APIGatewayProxyResponseEvent sendCommand(String username, String command) {
      EcsConfigurerLambdaHandler handler = new EcsConfigurerLambdaHandler();
      APIGatewayProxyRequestEvent event = createUpdateEvent(username, command);
      return handler.handleRequest(event, null);
  }

}
