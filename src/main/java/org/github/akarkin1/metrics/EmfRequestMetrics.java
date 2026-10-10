package org.github.akarkin1.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Accumulates per-request timings and writes them as one CloudWatch Embedded Metric Format line.
 * Timings of nested calls (e.g. S3 inside ECS) may overlap.
 */
@Log4j2
@RequiredArgsConstructor
public class EmfRequestMetrics implements RequestMetrics {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String NAMESPACE = "vpnbot";
  private static final String DIMENSION = "UpdateKind";
  private static final String TOTAL_METRIC = "TotalMs";
  private static final String UNIT = "Milliseconds";
  private static final Map<MetricComponent, String> METRIC_NAMES = new EnumMap<>(Map.of(
      MetricComponent.S3, "S3Ms",
      MetricComponent.ECS, "EcsMs",
      MetricComponent.TELEGRAM, "TelegramMs"));

  private final boolean enabled;
  private final Clock clock;
  private final Consumer<String> output;

  private final Map<MetricComponent, LongAdder> totals = createTotals();
  private volatile String updateKind;
  private volatile long startMs;

  @Override
  public void start(String updateKind) {
    if (!enabled) {
      return;
    }
    totals.values().forEach(LongAdder::reset);
    this.startMs = clock.millis();
    this.updateKind = updateKind;
  }

  @Override
  public <T> T time(MetricComponent component, Supplier<T> action) {
    if (!enabled) {
      return action.get();
    }
    long begin = clock.millis();
    try {
      return action.get();
    } finally {
      totals.get(component).add(clock.millis() - begin);
    }
  }

  @Override
  public void time(MetricComponent component, Runnable action) {
    time(component, () -> {
      action.run();
      return null;
    });
  }

  @Override
  public void finish() {
    String kind = updateKind;
    if (!enabled || kind == null) {
      return;
    }
    updateKind = null;
    long now = clock.millis();
    try {
      output.accept(MAPPER.writeValueAsString(emfDocument(kind, now)));
    } catch (JsonProcessingException e) {
      log.warn("Failed to write request metrics", e);
    }
  }

  private ObjectNode emfDocument(String kind, long now) {
    ObjectNode root = MAPPER.createObjectNode();
    ObjectNode aws = root.putObject("_aws");
    aws.put("Timestamp", now);
    ObjectNode directive = aws.putArray("CloudWatchMetrics").addObject();
    directive.put("Namespace", NAMESPACE);
    directive.putArray("Dimensions").addArray().add(DIMENSION);
    ArrayNode metrics = directive.putArray("Metrics");
    addMetricDefinition(metrics, TOTAL_METRIC);
    METRIC_NAMES.values().forEach(name -> addMetricDefinition(metrics, name));

    root.put(DIMENSION, kind);
    root.put(TOTAL_METRIC, now - startMs);
    METRIC_NAMES.forEach((component, name) -> root.put(name, totals.get(component).sum()));
    return root;
  }

  private static void addMetricDefinition(ArrayNode metrics, String name) {
    metrics.addObject()
        .put("Name", name)
        .put("Unit", UNIT);
  }

  private static Map<MetricComponent, LongAdder> createTotals() {
    Map<MetricComponent, LongAdder> totals = new EnumMap<>(MetricComponent.class);
    for (MetricComponent component : MetricComponent.values()) {
      totals.put(component, new LongAdder());
    }
    return totals;
  }

}
