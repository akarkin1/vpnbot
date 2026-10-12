package org.github.akarkin1.ec2;

import java.util.Set;

/** The AWS regions a node infrastructure could be deployed to. */
public interface RegionCatalog {

  /**
   * The ids of the regions enabled for the account (opt-in regions only once opted in), e.g.
   * {@code eu-central-1}; an unmodifiable set.
   */
  Set<String> enabledRegions();

}
