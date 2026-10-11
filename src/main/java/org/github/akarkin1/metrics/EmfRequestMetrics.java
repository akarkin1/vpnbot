package org.github.akarkin1.metrics;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Accumulates per-request timings and writes them as one CloudWatch Embedded Metric Format line.
 * Timings of nested calls (e.g. DynamoDB inside ECS) may overlap.
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
      MetricComponent.DYNAMODB, "DynamoDbMs",
      MetricComponent.ECS, "EcsMs",
      MetricComponent.TELEGRAM, "TelegramMs"));

  private final boolean enabled;
  private final Clock clock;
  private final Consumer<String> output;

  private final Map<MetricComponent, LongAdder> totals = createTotals();
  private volatile String updateKind;
  private volatile long startMs;

  private static Map<MetricComponent, LongAdder> createTotals() {
    Map<MetricComponent, LongAdder> totals = new EnumMap<>(MetricComponent.class);
    for (MetricComponent component : MetricComponent.values()) {
      totals.put(component, new LongAdder());
    }
    return totals;
  }

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

  private EmfDocument emfDocument(String kind, long now) {
    List<MetricDefinition> definitions = new ArrayList<>();
    definitions.add(new MetricDefinition(TOTAL_METRIC, UNIT));
    METRIC_NAMES.values().forEach(name -> definitions.add(new MetricDefinition(name, UNIT)));
    MetricDirective directive = new MetricDirective(NAMESPACE, List.of(List.of(DIMENSION)), definitions);

    Map<String, Long> componentMs = new LinkedHashMap<>();
    METRIC_NAMES.forEach((component, name) -> componentMs.put(name, totals.get(component).sum()));
    return new EmfDocument(new AwsMetadata(now, List.of(directive)), kind, now - startMs, componentMs);
  }

  private record EmfDocument(@JsonProperty("_aws") AwsMetadata aws,
                             @JsonProperty(DIMENSION) String updateKind,
                             @JsonProperty(TOTAL_METRIC) long totalMs,
                             @JsonAnyGetter Map<String, Long> componentMs) {
  }

  private record AwsMetadata(@JsonProperty("Timestamp") long timestamp,
                             @JsonProperty("CloudWatchMetrics") List<MetricDirective> cloudWatchMetrics) {
  }

  private record MetricDirective(@JsonProperty("Namespace") String namespace,
                                 @JsonProperty("Dimensions") List<List<String>> dimensions,
                                 @JsonProperty("Metrics") List<MetricDefinition> metrics) {
  }

  private record MetricDefinition(@JsonProperty("Name") String name,
                                  @JsonProperty("Unit") String unit) {
  }

}
