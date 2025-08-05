package org.github.akarkin1;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import org.github.akarkin1.config.guice.CommonConfigModule;
import org.github.akarkin1.config.guice.LocalConfigModule;
import org.github.akarkin1.config.guice.ProdConfigModule;
import org.github.akarkin1.service.EcsConfigurerFacade;

@Singleton
public class EcsConfigurerLambdaHandler implements
  RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  private static final EcsConfigurerFacade LAMBDA_FACADE;

  static {
    String profile = System.getProperty("lambda.profile", "prod");
    Injector injector = "local".equalsIgnoreCase(profile)
      ? Guice.createInjector(new CommonConfigModule(), new LocalConfigModule())
      : Guice.createInjector(new CommonConfigModule(), new ProdConfigModule());
    LAMBDA_FACADE = injector.getInstance(EcsConfigurerFacade.class);
  }

  @Override
  public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent gwEvent,
                                                    Context context) {
    LAMBDA_FACADE.processGatewayEvent(gwEvent);

    // always return successful response to Telegram Bot API
    return new APIGatewayProxyResponseEvent()
      .withBody("{}")
      .withStatusCode(201);
  }

}
