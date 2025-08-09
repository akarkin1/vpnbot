package org.github.akarkin1.it.steps;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.github.akarkin1.EcsConfigurerLambdaHandler;
import org.github.akarkin1.it.init.WireMockInitializer;
import org.github.akarkin1.util.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.github.akarkin1.it.init.SMInitializer.TEST_TG_SECRET_TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuthenticationSteps extends BaseInfraSteps {

  private String currentUsername;
  private boolean includeHeader;
  private String headerValue;
  private APIGatewayProxyResponseEvent lambdaResponse;

  @Given("a user {word} with a valid x-telegram-bot-api-secret-token")
  public void aUserWithValidToken(String username) {
    this.currentUsername = username;
    this.includeHeader = true;
    this.headerValue = TEST_TG_SECRET_TOKEN;
  }

  @Given("a user {word} with {word} x-telegram-bot-api-secret-token")
  public void aUserWithTokenType(String username, String tokenType) {
    this.currentUsername = username;
    switch (tokenType.toLowerCase()) {
      case "missing" -> {
        this.includeHeader = false;
        this.headerValue = null;
      }
      case "invalid" -> {
        this.includeHeader = true;
        this.headerValue = "invalid-token";
      }
      default -> throw new IllegalArgumentException("Unsupported token type: " + tokenType);
    }
  }

  @When("^the user calls (.+)$")
  public void theUserCallsCommand(String command) {
    EcsConfigurerLambdaHandler handler = new EcsConfigurerLambdaHandler();
    APIGatewayProxyRequestEvent event = createEvent(currentUsername, command, includeHeader, headerValue);
    this.lambdaResponse = handler.handleRequest(event, null);
  }

  @Then("the command is executed successfully")
  public void theCommandIsExecutedSuccessfully() {
    assertNotNull(lambdaResponse);
    assertEquals(201, lambdaResponse.getStatusCode());

    List<LoggedRequest> requests = WireMock.findAll(postRequestedFor(urlMatching("/bot.*/sendmessage")));
    assertFalse(requests.isEmpty(),
                "Expected at least one message sent to Telegram, but none were found");
  }

  @Then("access is denied")
  public void accessIsDenied() {
    assertNotNull(lambdaResponse);
    assertEquals(201, lambdaResponse.getStatusCode());

    List<LoggedRequest> requests = WireMock.findAll(postRequestedFor(urlMatching("/bot.*/sendmessage")));
    assertTrue(requests.stream().anyMatch(r -> r.getBodyAsString().contains("The request is not authorized")),
      "Expected a denial message to be sent to Telegram, but it was not found");
  }

  private static @NotNull APIGatewayProxyRequestEvent createEvent(String username, String command,
                                                                  boolean includeHeader, String headerValue) {
    APIGatewayProxyRequestEvent gwEvent = new APIGatewayProxyRequestEvent();
    if (includeHeader) {
      gwEvent.setHeaders(Map.of("x-telegram-bot-api-secret-token", headerValue));
    } else {
      gwEvent.setHeaders(Map.of());
    }

    Update update = new Update();
    update.setUpdateId(Math.abs(new Random().nextInt() / 2 - 1));
    Message userMsg = new Message();
    userMsg.setMessageId(111);
    Chat chat = new Chat();
    chat.setId(Long.parseLong(WireMockInitializer.TG_TEST_CHAT_ID));
    userMsg.setChat(chat);
    userMsg.setText(command);

    User fromUser = new User();
    fromUser.setUserName(username);
    fromUser.setId(1L);
    fromUser.setLanguageCode("en-US");
    userMsg.setFrom(fromUser);
    update.setMessage(userMsg);

    gwEvent.setBody(JsonUtils.toJson(update));
    return gwEvent;
  }
}
