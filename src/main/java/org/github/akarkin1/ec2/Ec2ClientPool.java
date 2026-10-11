package org.github.akarkin1.ec2;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesRequest;
import software.amazon.awssdk.services.ec2.model.Filter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** One EC2 client per region, created by {@code clients} on the region's first use. */
@RequiredArgsConstructor
public class Ec2ClientPool {

  /** A well-formed network interface id that no interface has: the lookup matches nothing. */
  static final String PRIMER_NETWORK_INTERFACE_ID = "eni-00000000000000000";

  private final Function<Region, Ec2Client> clients;
  private final Map<String, Ec2Client> pool = new ConcurrentHashMap<>();

  public Ec2Client getForRegion(String regionName) {
    return pool.computeIfAbsent(regionName, name -> clients.apply(Region.of(name)));
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
