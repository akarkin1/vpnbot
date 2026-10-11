package org.github.akarkin1.auth;

import java.util.Optional;

/** Keeps the pending access requests, at most one per user. */
public interface AccessRequestService {

  /** The user's pending request; empty when there is none, or it has expired. */
  Optional<AccessRequest> find(long userId);

  /** Records the request; it expires after a week if no admin decides. */
  void create(AccessRequest request);

  void delete(long userId);

}
