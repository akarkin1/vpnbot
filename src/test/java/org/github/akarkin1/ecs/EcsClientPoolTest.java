package org.github.akarkin1.ecs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;

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
import static org.mockito.Mockito.mock;

class EcsClientPoolTest {

  private static final List<Region> REGIONS = List.of(Region.EU_CENTRAL_1, Region.US_EAST_1,
                                                      Region.EU_WEST_2, Region.AP_NORTHEAST_1);
  private static final int THREADS = 8;

  @Test
  @Timeout(30)
  @DisplayName("2a D-7: concurrent get(region) from several threads creates one client per region and returns it to every thread")
  void concurrentGet() throws Exception {
    SlowProvider provider = new SlowProvider();
    EcsClientPool pool = new EcsClientPool(provider);
    CyclicBarrier start = new CyclicBarrier(THREADS);
    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    try {
      List<Future<Map<Region, EcsClient>>> futures = new ArrayList<>();
      for (int i = 0; i < THREADS; i++) {
        List<Region> order = new ArrayList<>(REGIONS);
        Collections.rotate(order, i);
        futures.add(executor.submit(() -> {
          start.await(10, TimeUnit.SECONDS);
          Map<Region, EcsClient> clients = new ConcurrentHashMap<>();
          for (Region region : order) {
            clients.put(region, pool.get(region));
          }
          return clients;
        }));
      }

      List<Map<Region, EcsClient>> results = new ArrayList<>();
      for (Future<Map<Region, EcsClient>> future : futures) {
        results.add(future.get(20, TimeUnit.SECONDS));
      }

      for (Region region : REGIONS) {
        EcsClient expected = pool.get(region);
        for (Map<Region, EcsClient> result : results) {
          assertEquals(expected, result.get(region), "another client for " + region);
        }
        assertEquals(1, provider.calls(region), "clients created for " + region);
      }
      assertEquals(REGIONS.size(),
                   new HashSet<>(REGIONS.stream().map(pool::get).toList()).size(),
                   "regions share a client");
    } finally {
      executor.shutdownNow();
    }
  }

  /** Creates a new mock client per call, slowly, so concurrent first calls overlap. */
  private static final class SlowProvider implements EcsClientProvider {

    private final Map<Region, AtomicInteger> calls = new ConcurrentHashMap<>();

    @Override
    public EcsClient get() {
      return mock(EcsClient.class);
    }

    @Override
    public EcsClient get(Region region) {
      calls.computeIfAbsent(region, ignored -> new AtomicInteger()).incrementAndGet();
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
      return mock(EcsClient.class);
    }

    int calls(Region region) {
      return calls.getOrDefault(region, new AtomicInteger()).get();
    }

  }

}
