package org.github.akarkin1.it.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.ServiceRole.Role;
import org.github.akarkin1.auth.UserEntitlements;
import org.github.akarkin1.util.JsonUtils;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.github.akarkin1.auth.Permission.LIST_NODES;
import static org.github.akarkin1.auth.Permission.ROOT_ACCESS;
import static org.github.akarkin1.auth.Permission.RUN_NODES;
import static org.github.akarkin1.auth.Permission.SUPPORTED_REGIONS;
import static org.github.akarkin1.auth.Permission.USER_MANAGEMENT;
import static org.github.akarkin1.it.init.S3Initializer.TEST_CONFIG_BUCKET;
import static software.amazon.awssdk.core.sync.RequestBody.fromString;

public class OnboardingSteps extends BaseInfraSteps {

    @Given("the system is initialized with a root user")
    public void systemInitializedWithRootUser() {
        Map<String, List<UserEntitlements.Entitlement>> userEntitlements = new HashMap<>();
        List<UserEntitlements.Entitlement> rootEntitlements = List.of(
            new UserEntitlements.Entitlement(null, Permission.ROOT_ACCESS)
        );
        userEntitlements.put("root", rootEntitlements);
        UserEntitlements ue = new UserEntitlements();
        ue.setUserEntitlements(userEntitlements);
        String json = JsonUtils.toJson(ue);
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(TEST_CONFIG_BUCKET)
                .key("config/user-permissions.json")
                .build(),
                fromString(json, StandardCharsets.UTF_8));
    }

    @When("the user {string} calls {string}")
    public void userCallsListRegisteredUsers(String username, String command) {
        sendCommand(username, command);
    }

    @When("the user {string} assigns role {word} to user {word}")
    public void userAssignsRoleToUser(String root, String role, String username) {
        sendCommand(root, String.format("/assignRoles %s vpn:%s", username, role));
    }

    @Then("the registered users list contains {string} with role {string}")
    public void registeredUsersListContainsNewUserWithRole(String username, String role) {
        Stream<Permission> permissions = switch (Role.valueOf(role)) {
            case READ_ONLY -> Stream.of(SUPPORTED_REGIONS, LIST_NODES);
            case USER_ADMIN -> Stream.of(USER_MANAGEMENT);
            case NODE_ADMIN -> Stream.of(SUPPORTED_REGIONS, LIST_NODES, RUN_NODES);
        };

        String permListVal = permissions.map(perm -> "vpn:%s".formatted(perm.name()))
          .collect(Collectors.joining(", "));

        String expectedResponse = """
        List of registered users:
         - root (user permissions: [ROOT_ACCESS])
         - %s (user permissions: [%s])
        """.formatted(username, permListVal);
        theBotReceivesTheResponseMatchingLinesInAnyOrder(expectedResponse);
    }

    @Then("the registered users list contains {string} with root permission")
    public void registeredUsersListContainsNewUserWithPermission(String username) {
        String expectedResponse = """
        List of registered users:
         - %s (user permissions: [%s])
        """.formatted(username, ROOT_ACCESS);
        theBotReceivesTheResponseMatchingLinesInAnyOrder(expectedResponse);
    }

    @When("the user {word} authenticates and calls \\/help")
    public void userAuthenticatesAndCallsHelp(String username) {
        sendCommand(username, "/help");
    }

    @Then("the help command output contains only allowed commands for role {word}")
    public void helpCommandOutputContainsAllowedCommands(String role) {
      // surrogate role, just to simplify testing - there is no such role defined in actual implementation
        String allowedCommands;
        if ("ROOT_ACCESS".equals(role)) {
          allowedCommands = """
           /help – *
           /version – *
           /listRunningNodes – *
           /runNode – *
           /supportedRegions – *
           /assignRoles – *
           /describeRoles – *
           /deleteUsers – *
           /listRegisteredUsers – *""";
        } else {
          allowedCommands = switch (Role.valueOf(role)) {
            case USER_ADMIN -> """
             /help – *
             /assignRoles – *
             /describeRoles – *
             /listRegisteredUsers – *""";
            case READ_ONLY -> """
             /help – *
             /listRunningNodes – *
             /supportedRegions – *
             /listServices – *""";
            case NODE_ADMIN -> """
             /help – *
             /listRunningNodes – *
             /supportedRegions – *
             /runNode – *
             /listServices – *""";
          };
        }
        String searchAfter = "The list of supported commands:";
        theBotReceivesTheResponseMatchingLineByLineExactlyAfter(searchAfter, allowedCommands, "^/\\w+\\s+–\\s+.*$");

    }

}
