package org.github.akarkin1.region;

import org.github.akarkin1.auth.TgUser;

/**
 * A user's request for a region that has no node infrastructure yet; at most one per region.
 *
 * @param regionId     the AWS region id, e.g. {@code eu-central-1}
 * @param username     nullable
 * @param lastName     nullable
 * @param languageCode the requester's language, used to notify them of the decision
 * @param requestedAt  epoch millis
 */
public record RegionRequest(String regionId, long userId, String username, String firstName,
                            String lastName, String languageCode, long requestedAt) {

  public TgUser user() {
    return new TgUser(userId, username);
  }

}
