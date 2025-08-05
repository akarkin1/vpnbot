package org.github.akarkin1.e2e.steps;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.github.akarkin1.ServiceLambdaHandler;
import org.github.akarkin1.e2e.BaseFeatureStep;
import org.junit.jupiter.api.Assertions;

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
        ServiceLambdaHandler handler = new ServiceLambdaHandler();
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setBody(""); // Simulate a health check or empty request
        Context context = null; // You can mock if needed
        response = handler.handleRequest(event, context);
    }

    @Then("the response should contain status 200")
    public void the_response_should_contain_status_201() {
        Assertions.assertNotNull(response);
        Assertions.assertEquals(201, response.getStatusCode());
    }
}

