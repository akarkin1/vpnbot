package org.github.akarkin1.auth;

import lombok.Getter;

@Getter
public class UnauthorizedRequestException extends RuntimeException {

  private final UserPermission requiredPermission;

  public UnauthorizedRequestException(UserPermission requiredPermission) {
    super("User doesn't have required permission: %s".formatted(requiredPermission));
    this.requiredPermission = requiredPermission;
  }

}
