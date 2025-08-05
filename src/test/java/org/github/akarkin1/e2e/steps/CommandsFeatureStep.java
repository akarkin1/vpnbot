package org.github.akarkin1.e2e.steps;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.commons.io.FileUtils;
import org.github.akarkin1.EcsConfigurerLambdaHandler;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.ServiceRole;
import org.github.akarkin1.auth.UserEntitlements;
import org.github.akarkin1.e2e.BaseFeatureStep;
import org.github.akarkin1.util.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.github.akarkin1.auth.Permission.LIST_NODES;
import static org.github.akarkin1.auth.Permission.RUN_NODES;
import static org.github.akarkin1.auth.Permission.SUPPORTED_REGIONS;
import static org.github.akarkin1.auth.Permission.USER_MANAGEMENT;
import static software.amazon.awssdk.core.sync.RequestBody.fromString;

public class CommandsFeatureStep extends BaseFeatureStep {

  private APIGatewayProxyResponseEvent lambdaResponse;

  @Given("the config bucket contains file {string} with content:")
  public void the_config_bucket_contains_file_with_content(String key, String content) {
    s3Client.putObject(PutObjectRequest.builder()
                         .bucket(BUCKET_NAME)
                         .key(key)
                         .build(),
                       fromString(content,
                                                                               StandardCharsets.UTF_8));
  }

  @When("{string} command is sent to the bot")
  public void i_send_supported_regions_command_to_the_bot(String command) {
    EcsConfigurerLambdaHandler handler = new EcsConfigurerLambdaHandler();
    APIGatewayProxyRequestEvent event = createUpdateEvent(command);
    Context context = null;
    lambdaResponse = handler.handleRequest(event, context);
  }

  @When("user is authorized with role {string}")
  public void user_is_authorized_with_role(String roleName) throws Exception {
    String username = "test-user";
    ServiceRole.Role role = ServiceRole.Role.valueOf(roleName);
    ServiceRole serviceRole = new ServiceRole("vpn", role);
    Set<ServiceRole> serviceRoles = Set.of(serviceRole);
    // Use UserSignupService logic to get entitlements
    List<UserEntitlements.Entitlement> entitlements = new ArrayList<>();
    for (ServiceRole sr : serviceRoles) {
      EnumSet<Permission> perms = switch (sr.getRole()) {
        case READ_ONLY -> EnumSet.of(SUPPORTED_REGIONS, LIST_NODES);
        case USER_ADMIN -> EnumSet.of(USER_MANAGEMENT);
        case NODE_ADMIN -> EnumSet.of(SUPPORTED_REGIONS, LIST_NODES, RUN_NODES);
      };
      for (Permission perm : perms) {
        entitlements.add(new UserEntitlements.Entitlement(sr.getServiceName(), perm));
      }
    }
    Map<String, List<UserEntitlements.Entitlement>> userEntitlements = Map.of(username, entitlements);
    UserEntitlements ue = new UserEntitlements();
    ue.setUserEntitlements(userEntitlements);
    ObjectMapper mapper = new ObjectMapper();
    String json = mapper.writeValueAsString(ue);
    s3Client.putObject(PutObjectRequest.builder()
            .bucket(BUCKET_NAME)
            .key("config/user-permissions.json")
            .build(),
        fromString(json, StandardCharsets.UTF_8));
  }

  private static @NotNull APIGatewayProxyRequestEvent createUpdateEvent(String command) {
    APIGatewayProxyRequestEvent gwEvent = new APIGatewayProxyRequestEvent();

    gwEvent.setHeaders(Map.of("x-telegram-bot-api-secret-token", TEST_SECRET_TOKEN_VALUE));

    Update update = new Update();
    update.setUpdateId(123);
    Message userMsg = new Message();
    userMsg.setMessageId(111);
    Chat chat = new Chat();
    chat.setId(2222L);
    userMsg.setChat(chat);
    userMsg.setText(command);

    User fromUser = new User();
    fromUser.setUserName("test-user");
    fromUser.setId(1L);
    fromUser.setLanguageCode("en-US");
    userMsg.setFrom(fromUser);
    update.setMessage(userMsg);

    gwEvent.setBody(JsonUtils.toJson(update));

    return gwEvent;
  }

  @Then("the lambda should return valid response")
  public void theLambdaShouldReturnValidResponse() {
    Assertions.assertNotNull(lambdaResponse);
    Assertions.assertNotNull(lambdaResponse.getBody());
    Assertions.assertEquals(201, lambdaResponse.getStatusCode());
  }

  @And("the lambda environment is cleaned up")
  public void lambdaEnvironmentIsCleanedUp() {
    cleanupLambdaEnv();
  }

//  @AfterAll
  public static void cleanupLambdaEnv() {
    try {
      FileUtils.deleteDirectory(new File("test-eventIds"));
    } catch (IOException e) {
      System.out.println("Failed to delete test-eventIds directory: " + e.getMessage());
    }
  }
}

