package org.github.akarkin1.ec2;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesRequest;
import software.amazon.awssdk.services.ec2.model.Filter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Ec2ClientPool implements Ec2ClientProvider {

  /** A well-formed network interface id that no interface has: the lookup matches nothing. */
  static final String PRIMER_NETWORK_INTERFACE_ID = "eni-00000000000000000";

  private final Ec2ClientProvider delegate;
  private final Map<String, Ec2Client> pool = new ConcurrentHashMap<>();

  public Ec2ClientPool() {
    this(new SimpleEc2ClientProvider());
  }

  public Ec2ClientPool(Ec2ClientProvider delegate) {
    this.delegate = delegate;
  }

  @Override
  public Ec2Client getForRegion(String regionName) {
    return pool.computeIfAbsent(regionName, delegate::getForRegion);
  }

  @Override
  public Ec2Client getForGlobal() {
    return pool.computeIfAbsent("GLOBAL", delegate::getForRegion);
  }

  /**
   * Creates (or gets) the region's client and runs a network interface lookup that matches
   * nothing, so the EC2 request path is loaded before the SnapStart snapshot. The result is
   * discarded.
   */
  public void prime(String regionId) {
    DescribeNetworkInterfacesRequest request = DescribeNetworkInterfacesRequest.builder()
        .filters(Filter.builder()
                     .name("network-interface-id")
                     .values(PRIMER_NETWORK_INTERFACE_ID)
                     .build())
        .build();
    getForRegion(regionId).describeNetworkInterfaces(request);
  }
}
