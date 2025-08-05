package org.github.akarkin1.e2e.steps;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.github.akarkin1.EcsConfigurerLambdaHandler;
import org.github.akarkin1.e2e.BaseFeatureStep;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.util.Map;

public class InitFeatureStep extends BaseFeatureStep {
    private APIGatewayProxyResponseEvent response;

    @Given("the S3 bucket is available")
    public void the_s3_bucket_is_available() {
        // Bucket is created in LocalstackS3TestBase
        Assertions.assertTrue(s3Client.listBuckets().buckets().stream()
                .anyMatch(b -> b.name().equals(BUCKET_NAME)));
    }

    @When("I invoke the lambda with a basic request")
    public void i_invoke_the_lambda_with_a_basic_request() {
        EcsConfigurerLambdaHandler handler = new EcsConfigurerLambdaHandler();
        APIGatewayProxyRequestEvent event = createEmptyEvent();
        response = handler.handleRequest(event, null);
    }

    private static @NotNull APIGatewayProxyRequestEvent createEmptyEvent() {
        APIGatewayProxyRequestEvent gwEvent = new APIGatewayProxyRequestEvent();

        gwEvent.setHeaders(Map.of("x-telegram-bot-api-secret-token", TEST_SECRET_TOKEN_VALUE));

        return gwEvent;
    }

    @Then("the response is successful")
    public void the_response_is_successful() {
        Assertions.assertNotNull(response);
        Assertions.assertEquals(200, response.getStatusCode());
    }
}

