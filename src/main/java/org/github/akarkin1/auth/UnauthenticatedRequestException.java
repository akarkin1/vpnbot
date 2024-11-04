package org.github.akarkin1.auth;

public class UnauthenticatedRequestException extends RuntimeException {

  public UnauthenticatedRequestException() {
    super("Unauthenticated request! Telegram Secret Token is incorrect or missing!");
  }

}
