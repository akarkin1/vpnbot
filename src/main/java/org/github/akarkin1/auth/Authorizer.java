package org.github.akarkin1.auth;

public interface Authorizer {

  boolean isAllowed(String tgUsername, UserAction action);

}
