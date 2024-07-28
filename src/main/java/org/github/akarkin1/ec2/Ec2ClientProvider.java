package org.github.akarkin1.ec2;

import software.amazon.awssdk.services.ec2.Ec2Client;

public interface Ec2ClientProvider {

  Ec2Client getForRegion(String regionName);

  Ec2Client getForGlobal();
}
