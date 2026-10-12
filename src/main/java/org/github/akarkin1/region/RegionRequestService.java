package org.github.akarkin1.region;

import java.util.List;
import java.util.Optional;

/** Keeps the open region requests, at most one per region. */
public interface RegionRequestService {

  /** The region's open request; empty when there is none, or it has expired. */
  Optional<RegionRequest> find(String regionId);

  /** Every open (not expired) request. */
  List<RegionRequest> findAll();

  /** Records the request; it expires after 30 days if no root user decides. */
  void create(RegionRequest request);

  void delete(String regionId);

}
