package org.github.akarkin1.e2e.steps;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.github.akarkin1.ServiceLambdaHandler;
import org.github.akarkin1.e2e.BaseFeatureStep;
import org.github.akarkin1.util.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

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
        ServiceLambdaHandler handler = new ServiceLambdaHandler();
        APIGatewayProxyRequestEvent event = createUpdateEvent("/version");
        Context context = null; // You can mock if needed
        response = handler.handleRequest(event, context);
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

    @Then("the response should contain status 200")
    public void the_response_should_contain_status_201() {
        Assertions.assertNotNull(response);
        Assertions.assertEquals(201, response.getStatusCode());
    }
}

