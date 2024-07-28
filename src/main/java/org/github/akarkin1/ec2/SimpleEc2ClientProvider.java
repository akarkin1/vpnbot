package org.github.akarkin1.ec2;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;

public class SimpleEc2ClientProvider implements Ec2ClientProvider {

  @Override
  public Ec2Client getForRegion(String regionName) {
    return Ec2Client.builder()
        .region(Region.of(regionName))
        .build();
  }

  @Override
  public Ec2Client getForGlobal() {
    return Ec2Client.builder()
        .region(Region.AWS_GLOBAL)
        .build();
  }

}
