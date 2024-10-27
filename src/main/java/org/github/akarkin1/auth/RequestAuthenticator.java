package org.github.akarkin1.auth;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;

public interface RequestAuthenticator {

  void authenticate(APIGatewayProxyRequestEvent request) throws UnauthorizedRequestException;

}
