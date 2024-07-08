package org.github.akarkin1.ec2;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.model.Instance;

public record RegionInstance(Region region, Instance instance) {

}
