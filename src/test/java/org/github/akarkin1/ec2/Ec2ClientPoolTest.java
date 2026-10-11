package org.github.akarkin1.ec2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesResponse;
import software.amazon.awssdk.services.ec2.model.Filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The concurrency case injects a slow client factory; the priming cases inject a mocked one.
 * No real AWS clients are built.
 */
class Ec2ClientPoolTest {

  private static final String REGION = "eu-central-1";
  private static final String NO_MATCH_ENI = "eni-00000000000000000";

  @Test
  @DisplayName("AC-P6: prime(region) calls describeNetworkInterfaces once on that region's client with only the no-match filter")
  void primeDescribesNoMatchInterface() {
    Ec2Client client = emptyResultClient();
    Function<Region, Ec2Client> provider = mockFactory();
    when(provider.apply(Region.of(REGION))).thenReturn(client);
    Ec2ClientPool pool = new Ec2ClientPool(provider);

    assertDoesNotThrow(() -> pool.prime(REGION));

    ArgumentCaptor<DescribeNetworkInterfacesRequest> request =
        ArgumentCaptor.forClass(DescribeNetworkInterfacesRequest.class);
    verify(client, times(1)).describeNetworkInterfaces(request.capture());
    List<Filter> filters = request.getValue().filters();
    assertEquals(1, filters.size(), "filters: " + filters);
    assertEquals("network-interface-id", filters.get(0).name());
    assertEquals(List.of(NO_MATCH_ENI), filters.get(0).values());
    // An explicit id would fail with InvalidNetworkInterfaceID.NotFound; the filter just matches nothing.
    assertFalse(request.getValue().hasNetworkInterfaceIds(), "networkInterfaceIds must not be set");
    verify(provider, times(1)).apply(Region.of(REGION));
  }

  @Test
  @DisplayName("AC-P6: prime(region) keeps the region's client in the pool: a later prime or getForRegion reuses it")
  void primeCreatesTheClientOnce() {
    Ec2Client client = emptyResultClient();
    Function<Region, Ec2Client> provider = mockFactory();
    when(provider.apply(Region.of(REGION))).thenReturn(client);
    Ec2ClientPool pool = new Ec2ClientPool(provider);

    pool.prime(REGION);
    pool.prime(REGION);

    assertSame(client, pool.getForRegion(REGION));
    verify(provider, times(1)).apply(Region.of(REGION));
    verify(client, times(2)).describeNetworkInterfaces(any(DescribeNetworkInterfacesRequest.class));
  }

  /**
   * The SDK's default methods (e.g. the Consumer-builder overload) delegate to the request overload
   * with real-method calls, so the stub below answers whichever overload the pool uses.
   */
  @SuppressWarnings("unchecked")
  private static Function<Region, Ec2Client> mockFactory() {
    return mock(Function.class);
  }

  private static Ec2Client emptyResultClient() {
    Ec2Client client = mock(Ec2Client.class, CALLS_REAL_METHODS);
    doReturn(DescribeNetworkInterfacesResponse.builder().build())
        .when(client).describeNetworkInterfaces(any(DescribeNetworkInterfacesRequest.class));
    return client;
  }

  private static final List<String> REGIONS = List.of("eu-central-1", "us-east-1", "eu-west-2",
                                                      "ap-northeast-1");
  private static final int THREADS = 8;

  @Test
  @Timeout(30)
  @DisplayName("2a D-7: concurrent getForRegion from several threads creates one client per region and returns it to every thread")
  void concurrentGetForRegion() throws Exception {
    Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
    Ec2ClientPool pool = new Ec2ClientPool(region -> {
      calls.computeIfAbsent(region.id(), id -> new AtomicInteger()).incrementAndGet();
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
      return mock(Ec2Client.class);
    });
    List<Map<String, Ec2Client>> results = getConcurrently(pool);

    for (String region : REGIONS) {
      Ec2Client expected = pool.getForRegion(region);
      for (Map<String, Ec2Client> result : results) {
        assertEquals(expected, result.get(region), "another client for " + region);
      }
      assertEquals(1, calls.get(region).get(), "clients created for " + region);
    }
    assertEquals(REGIONS.size(),
                 new HashSet<>(REGIONS.stream().map(pool::getForRegion).toList()).size(),
                 "regions share a client");
  }

  private static List<Map<String, Ec2Client>> getConcurrently(Ec2ClientPool pool) throws Exception {
    CyclicBarrier start = new CyclicBarrier(THREADS);
    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    try {
      List<Future<Map<String, Ec2Client>>> futures = new ArrayList<>();
      for (int i = 0; i < THREADS; i++) {
        List<String> order = new ArrayList<>(REGIONS);
        Collections.rotate(order, i);
        futures.add(executor.submit(() -> {
          start.await(10, TimeUnit.SECONDS);
          Map<String, Ec2Client> clients = new ConcurrentHashMap<>();
          for (String region : order) {
            clients.put(region, pool.getForRegion(region));
          }
          return clients;
        }));
      }
      List<Map<String, Ec2Client>> results = new ArrayList<>();
      for (Future<Map<String, Ec2Client>> future : futures) {
        results.add(future.get(20, TimeUnit.SECONDS));
      }
      return results;
    } finally {
      executor.shutdownNow();
    }
  }

}
