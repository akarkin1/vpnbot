package org.github.akarkin1.auth;

/**
 * A request for access by a user without permissions.
 *
 * @param username     nullable
 * @param lastName     nullable
 * @param languageCode the requester's language, used to notify them of the decision
 * @param requestedAt  epoch millis
 */
public record AccessRequest(long userId, String username, String firstName, String lastName,
                            String languageCode, long requestedAt) {

  public TgUser user() {
    return new TgUser(userId, username);
  }

}
