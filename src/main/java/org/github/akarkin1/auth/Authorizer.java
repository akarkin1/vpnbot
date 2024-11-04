package org.github.akarkin1.auth;

public interface Authorizer {

  boolean hasPermission(String tgUsername, Permission permission);

}
