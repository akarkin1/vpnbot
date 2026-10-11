package org.github.akarkin1.auth;

public interface Authorizer {

  boolean hasPermission(TgUser user, Permission permission);

}
