package org.github.akarkin1.it.steps;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.commons.io.FileUtils;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.ServiceRole;
import org.github.akarkin1.auth.UserEntitlements;
import org.junit.jupiter.api.Assertions;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.github.akarkin1.auth.Permission.LIST_NODES;
import static org.github.akarkin1.auth.Permission.RUN_NODES;
import static org.github.akarkin1.auth.Permission.SUPPORTED_REGIONS;
import static org.github.akarkin1.auth.Permission.USER_MANAGEMENT;
import static org.github.akarkin1.it.init.S3Initializer.TEST_CONFIG_BUCKET;
import static software.amazon.awssdk.core.sync.RequestBody.fromString;

public class CommandsFeatureStep extends BaseInfraSteps {

  private static final String DEFAULT_TEST_USER = "test-user";
  private APIGatewayProxyResponseEvent lambdaResponse;

  @Given("the config bucket contains file {string} with content:")
  public void theConfigBucketContainsFileWithContent(String key, String content) {
    s3Client.putObject(PutObjectRequest.builder()
                         .bucket(TEST_CONFIG_BUCKET)
                         .key(key)
                         .build(),
                       fromString(content,
                                  StandardCharsets.UTF_8));
  }

  @When("{string} command is sent to the bot")
  public void iSendSupportedRegionsCommandToTheBot(String command) {
    lambdaResponse = sendCommand(DEFAULT_TEST_USER, command);
  }

  @When("user is authorized with role {string}")
  public void userIsAuthorizedWithRole(String roleName) throws Exception {
    ServiceRole.Role role = ServiceRole.Role.valueOf(roleName);
    ServiceRole serviceRole = new ServiceRole("vpn", role);
    Set<ServiceRole> serviceRoles = Set.of(serviceRole);
    // Use UserSignupService logic to get entitlements
    List<UserEntitlements.Entitlement> entitlements = new ArrayList<>();
    for (ServiceRole sr : serviceRoles) {
      Stream<Permission> perms = switch (sr.getRole()) {
        case READ_ONLY -> Stream.of(SUPPORTED_REGIONS, LIST_NODES);
        case USER_ADMIN -> Stream.of(USER_MANAGEMENT);
        case NODE_ADMIN -> Stream.of(SUPPORTED_REGIONS, LIST_NODES, RUN_NODES);
      };
      perms.forEach(perm ->
        entitlements.add(new UserEntitlements.Entitlement(sr.getServiceName(), perm)));
    }
    Map<String, List<UserEntitlements.Entitlement>> userEntitlements = Map.of(DEFAULT_TEST_USER,
                                                                              entitlements);
    UserEntitlements ue = new UserEntitlements();
    ue.setUserEntitlements(userEntitlements);
    ObjectMapper mapper = new ObjectMapper();
    String json = mapper.writeValueAsString(ue);
    s3Client.putObject(PutObjectRequest.builder()
                         .bucket(TEST_CONFIG_BUCKET)
                         .key("config/user-permissions.json")
                         .build(),
                       fromString(json, StandardCharsets.UTF_8));
  }

  @Then("the lambda should return valid response")
  public void theLambdaShouldReturnValidResponse() {
    Assertions.assertNotNull(lambdaResponse);
    Assertions.assertNotNull(lambdaResponse.getBody());
    Assertions.assertEquals(201, lambdaResponse.getStatusCode());
  }

  @Given("no events sent to lambda")
  public void noEventsSentToLambda() {
    cleanUpDirectory(".test-data/ecs/");
  }

  @Given("no ecs task run")
  public void noEcsTaskRun() {
    cleanUpDirectory(".test-data/test-eventIds/");
  }

  @Given("wiremock requests are reset")
  public void resetWireMockRequests() {
    WireMock.resetAllRequests();
  }

  public static void cleanUpDirectory(String directoryName) {
    try {
      FileUtils.deleteDirectory(new File(directoryName));
    } catch (IOException e) {
      System.out.println("Failed to delete test-eventIds directory: " + e.getMessage());
    }
  }

  @Then("^the bot should reply with response:$")
  public void theBotShouldReplyWith(String multilineResponse) {
    theBotReceivesTheResponseMatchingLineByLine(multilineResponse);
  }
}

