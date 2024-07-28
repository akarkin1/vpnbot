package org.github.akarkin1.ec2;

import software.amazon.awssdk.services.ec2.Ec2Client;

import java.util.HashMap;
import java.util.Map;

public class Ec2ClientPool implements Ec2ClientProvider {
  private final Ec2ClientProvider delegate;
  private final Map<String, Ec2Client> pool = new HashMap<>();

  public Ec2ClientPool() {
    this.delegate = new SimpleEc2ClientProvider();
  }


  @Override
  public Ec2Client getForRegion(String regionName) {
    return pool.computeIfAbsent(regionName, delegate::getForRegion);
  }

  @Override
  public Ec2Client getForGlobal() {
    return pool.computeIfAbsent("GLOBAL", delegate::getForRegion);
  }
}
