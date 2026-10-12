package org.github.akarkin1.ec2;

import org.github.akarkin1.metrics.MetricComponent;
import org.github.akarkin1.metrics.RecordingRequestMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeRegionsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeRegionsResponse;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The client is a {@code CALLS_REAL_METHODS} mock: the SDK's no-argument and
 * Consumer-builder overloads delegate to {@code describeRegions(DescribeRegionsRequest)}, so the
 * stub answers whichever overload the catalog uses.
 */
class Ec2RegionCatalogTest {

  /** Not the default region, so the test sees that the configured id is used. */
  private static final String LAMBDA_REGION = "eu-west-1";

  private final RecordingRequestMetrics metrics = new RecordingRequestMetrics();
  /** One entry per DescribeRegions call: whether it ran inside an ECS timing. */
  private final List<Boolean> timedCalls = new CopyOnWriteArrayList<>();

  private Ec2Client client;
  private Function<Region, Ec2Client> clients;
  private Ec2RegionCatalog catalog;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    client = mock(Ec2Client.class, CALLS_REAL_METHODS);
    clients = mock(Function.class);
    when(clients.apply(any(Region.class))).thenReturn(client);
    catalog = new Ec2RegionCatalog(new Ec2ClientPool(clients), LAMBDA_REGION, metrics);
  }

  @Test
  @DisplayName("5 AC-6, §4.5: enabledRegions() is the set of region names of DescribeRegions")
  void mapsRegionNames() {
    givenRegions("eu-central-1", "us-east-1");

    assertEquals(Set.of("eu-central-1", "us-east-1"), catalog.enabledRegions());
  }

  @Test
  @DisplayName("5 AC-6: an empty DescribeRegions answer is an empty set")
  void noRegions() {
    givenRegions();

    assertEquals(Set.of(), catalog.enabledRegions());
  }

  @Test
  @DisplayName("5 §4.5, D-4: one DescribeRegions call without allRegions = true (only the regions enabled for the account)")
  void describesEnabledRegionsOnly() {
    givenRegions("eu-central-1");

    catalog.enabledRegions();

    ArgumentCaptor<DescribeRegionsRequest> request =
        ArgumentCaptor.forClass(DescribeRegionsRequest.class);
    verify(client, times(1)).describeRegions(request.capture());
    assertNotEquals(Boolean.TRUE, request.getValue().allRegions(), request.getValue().toString());
  }

  @Test
  @DisplayName("5 §4.5: the call goes to the EC2 client of the configured region (Settings.awsRegion), taken from the pool")
  void usesTheConfiguredRegion() {
    givenRegions("eu-central-1");

    catalog.enabledRegions();
    catalog.enabledRegions();

    verify(clients, times(1)).apply(Region.of(LAMBDA_REGION));
    verifyNoMoreInteractions(clients);
  }

  @Test
  @DisplayName("5 AC-6, D-6: DescribeRegions runs inside an ECS timing, and only ECS is timed")
  void timedAsEcs() {
    givenRegions("eu-central-1", "us-east-1");

    catalog.enabledRegions();

    assertEquals(List.of(true), timedCalls);
    assertEquals(List.of(MetricComponent.ECS), metrics.timed());
  }

  @Test
  @DisplayName("5 §4.5: an EC2 failure propagates (the router's generic error path answers)")
  void failurePropagates() {
    Ec2Exception failure = (Ec2Exception) Ec2Exception.builder().message("UnauthorizedOperation")
        .build();
    doAnswer(invocation -> {
      throw failure;
    }).when(client).describeRegions(any(DescribeRegionsRequest.class));

    assertSame(failure, assertThrows(Ec2Exception.class, catalog::enabledRegions));
  }

  private void givenRegions(String... names) {
    DescribeRegionsResponse response = DescribeRegionsResponse.builder()
        .regions(Arrays.stream(names)
                     .map(name -> software.amazon.awssdk.services.ec2.model.Region.builder()
                         .regionName(name)
                         .endpoint("ec2." + name + ".amazonaws.com")
                         .optInStatus("opt-in-not-required")
                         .build())
                     .toList())
        .build();
    doAnswer(invocation -> {
      timedCalls.add(metrics.isTiming(MetricComponent.ECS));
      return response;
    }).when(client).describeRegions(any(DescribeRegionsRequest.class));
  }

}
