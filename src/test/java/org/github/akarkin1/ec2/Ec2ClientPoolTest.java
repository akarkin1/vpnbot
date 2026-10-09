package org.github.akarkin1.ec2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.MockedConstruction;
import software.amazon.awssdk.services.ec2.Ec2Client;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/**
 * {@link Ec2ClientPool} creates its {@link SimpleEc2ClientProvider} itself, so the provider is
 * replaced with a slow mock through {@code mockConstruction}: no real AWS clients are built.
 */
class Ec2ClientPoolTest {

  private static final List<String> REGIONS = List.of("eu-central-1", "us-east-1", "eu-west-2",
                                                      "ap-northeast-1");
  private static final int THREADS = 8;

  @Test
  @Timeout(30)
  @DisplayName("2a D-7: concurrent getForRegion from several threads creates one client per region and returns it to every thread")
  void concurrentGetForRegion() throws Exception {
    Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
    try (MockedConstruction<SimpleEc2ClientProvider> ignored = mockConstruction(
        SimpleEc2ClientProvider.class,
        (provider, context) -> when(provider.getForRegion(anyString())).thenAnswer(invocation -> {
          calls.computeIfAbsent(invocation.getArgument(0), region -> new AtomicInteger())
              .incrementAndGet();
          Thread.sleep(50);
          return mock(Ec2Client.class);
        }))) {
      Ec2ClientPool pool = new Ec2ClientPool();
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
