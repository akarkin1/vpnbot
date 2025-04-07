package org.github.akarkin1.auth;

import lombok.Getter;

@Getter
public class UnauthorizedRequestException extends RuntimeException {

  private final Permission requiredPermission;

  public UnauthorizedRequestException(Permission requiredPermission) {
    super("User doesn't have required permission: %s".formatted(requiredPermission));
    this.requiredPermission = requiredPermission;
  }

}
