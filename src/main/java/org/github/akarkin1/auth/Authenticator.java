package org.github.akarkin1.auth;

public interface Authenticator {

  boolean isAllowed(String tgUsername, UserAction action);

}
