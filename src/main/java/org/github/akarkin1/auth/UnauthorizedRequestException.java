package org.github.akarkin1.auth;

public class UnauthorizedRequestException extends RuntimeException {

  public UnauthorizedRequestException() {
    super("Unauthorized request! Telegram Secret Token is incorrect or missing!");
  }

}
