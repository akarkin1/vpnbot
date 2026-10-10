package org.github.akarkin1.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.akarkin1.util.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmfRequestMetricsTest {

  private static final Instant START = Instant.parse("2026-10-09T10:00:00Z");

  private final ObjectMapper mapper = new ObjectMapper();
  private final MutableClock clock = new MutableClock(START);
  private final List<String> lines = new CopyOnWriteArrayList<>();

  @Test
  @DisplayName("2a AC-7: finish writes one EMF JSON line with the namespace, the dimension and the 4 metrics")
  void emfLine() throws Exception {
    EmfRequestMetrics metrics = new EmfRequestMetrics(true, clock, lines::add);

    metrics.start("Callback");
    metrics.time(MetricComponent.DYNAMODB, () -> clock.advance(Duration.ofMillis(5)));
    String ecsResult = metrics.time(MetricComponent.ECS, () -> {
      clock.advance(Duration.ofMillis(7));
      return "tasks";
    });
    metrics.time(MetricComponent.TELEGRAM, () -> clock.advance(Duration.ofMillis(3)));
    clock.advance(Duration.ofMillis(10));
    metrics.finish();

    assertEquals("tasks", ecsResult);
    assertEquals(1, lines.size(), lines.toString());
    String line = lines.getFirst();
    assertFalse(line.contains("\n"), "not a single line: " + line);

    JsonNode json = mapper.readTree(line);
    JsonNode aws = json.get("_aws");
    assertTrue(aws.get("Timestamp").isIntegralNumber(), "Timestamp: " + aws.get("Timestamp"));
    long timestamp = aws.get("Timestamp").asLong();
    assertTrue(timestamp >= START.toEpochMilli() && timestamp <= clock.millis(),
               "Timestamp is not an epoch ms of this request: " + timestamp);

    JsonNode cloudWatchMetrics = aws.get("CloudWatchMetrics");
    assertEquals(1, cloudWatchMetrics.size());
    JsonNode directive = cloudWatchMetrics.get(0);
    assertEquals("vpnbot", directive.get("Namespace").asText());
    assertEquals(mapper.readTree("[[\"UpdateKind\"]]"), directive.get("Dimensions"));
    assertEquals(mapper.readTree("""
        [{"Name":"TotalMs","Unit":"Milliseconds"},{"Name":"DynamoDbMs","Unit":"Milliseconds"},
         {"Name":"EcsMs","Unit":"Milliseconds"},{"Name":"TelegramMs","Unit":"Milliseconds"}]"""),
                 directive.get("Metrics"));

    assertEquals("Callback", json.get("UpdateKind").asText());
    assertEquals(25, json.get("TotalMs").asLong());
    assertEquals(5, json.get("DynamoDbMs").asLong());
    assertEquals(7, json.get("EcsMs").asLong());
    assertEquals(3, json.get("TelegramMs").asLong());
  }

  @Test
  @DisplayName("2a AC-7: components without timings are reported as 0")
  void untimedComponentsAreZero() throws Exception {
    EmfRequestMetrics metrics = new EmfRequestMetrics(true, clock, lines::add);

    metrics.start("Message");
    metrics.finish();

    JsonNode json = mapper.readTree(lines.getFirst());
    assertEquals("Message", json.get("UpdateKind").asText());
    assertEquals(0, json.get("TotalMs").asLong());
    assertEquals(0, json.get("DynamoDbMs").asLong());
    assertEquals(0, json.get("EcsMs").asLong());
    assertEquals(0, json.get("TelegramMs").asLong());
  }

  @Test
  @DisplayName("2a AC-7: repeated timings of a component are accumulated")
  void accumulates() throws Exception {
    EmfRequestMetrics metrics = new EmfRequestMetrics(true, clock, lines::add);

    metrics.start("Command");
    metrics.time(MetricComponent.TELEGRAM, () -> clock.advance(Duration.ofMillis(4)));
    metrics.time(MetricComponent.TELEGRAM, () -> clock.advance(Duration.ofMillis(6)));
    metrics.time(MetricComponent.ECS, () -> clock.advance(Duration.ofMillis(1)));
    metrics.time(MetricComponent.ECS, () -> {
      clock.advance(Duration.ofMillis(2));
      return null;
    });
    metrics.finish();

    JsonNode json = mapper.readTree(lines.getFirst());
    assertEquals("Command", json.get("UpdateKind").asText());
    assertEquals(10, json.get("TelegramMs").asLong());
    assertEquals(3, json.get("EcsMs").asLong());
    assertEquals(0, json.get("DynamoDbMs").asLong());
    assertEquals(13, json.get("TotalMs").asLong());
  }

  @Test
  @DisplayName("2a AC-7: disabled metrics print nothing and still run the actions")
  void disabled() {
    EmfRequestMetrics metrics = new EmfRequestMetrics(false, clock, lines::add);
    AtomicBoolean ran = new AtomicBoolean();

    metrics.start("Callback");
    metrics.time(MetricComponent.DYNAMODB, () -> ran.set(true));
    String result = metrics.time(MetricComponent.ECS, () -> "value");
    metrics.finish();

    assertTrue(ran.get());
    assertEquals("value", result);
    assertEquals(List.of(), lines);
  }

  @Test
  @DisplayName("2a AC-7: finish without start prints nothing")
  void finishWithoutStart() {
    EmfRequestMetrics metrics = new EmfRequestMetrics(true, clock, lines::add);

    metrics.time(MetricComponent.DYNAMODB, () -> clock.advance(Duration.ofMillis(5)));
    metrics.finish();

    assertEquals(List.of(), lines);
  }

  @Test
  @DisplayName("2a AC-7: exceptions of a timed action pass through unchanged")
  void exceptionsPassThrough() {
    EmfRequestMetrics metrics = new EmfRequestMetrics(true, clock, lines::add);
    IllegalStateException failure = new IllegalStateException("boom");

    metrics.start("Callback");
    IllegalStateException thrown = assertThrows(IllegalStateException.class,
        () -> metrics.time(MetricComponent.DYNAMODB, (Runnable) () -> {
          throw failure;
        }));

    assertSame(failure, thrown);
  }

  @Test
  @Timeout(30)
  @DisplayName("2a AC-7: timings from many threads are accumulated without losses")
  void threadSafeAccumulation() throws Exception {
    PerThreadClock perThreadClock = new PerThreadClock(START);
    EmfRequestMetrics metrics = new EmfRequestMetrics(true, perThreadClock, lines::add);
    int threads = 8;
    int timingsPerThread = 500;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    try {
      CountDownLatch ready = new CountDownLatch(threads);
      metrics.start("Callback");
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        futures.add(executor.submit(() -> {
          ready.countDown();
          ready.await();
          for (int j = 0; j < timingsPerThread; j++) {
            metrics.time(MetricComponent.DYNAMODB, () -> perThreadClock.advance(Duration.ofMillis(1)));
          }
          return null;
        }));
      }
      for (Future<?> future : futures) {
        future.get(20, TimeUnit.SECONDS);
      }
      metrics.finish();
    } finally {
      executor.shutdownNow();
    }

    JsonNode json = mapper.readTree(lines.getFirst());
    assertEquals(threads * timingsPerThread, json.get("DynamoDbMs").asLong());
  }

  /** Each thread sees its own time, so every timed action lasts exactly what it advances. */
  private static final class PerThreadClock extends Clock {

    private final ThreadLocal<Long> epochMillis;

    PerThreadClock(Instant start) {
      this.epochMillis = ThreadLocal.withInitial(start::toEpochMilli);
    }

    void advance(Duration duration) {
      epochMillis.set(epochMillis.get() + duration.toMillis());
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public long millis() {
      return epochMillis.get();
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(millis());
    }

  }

}
