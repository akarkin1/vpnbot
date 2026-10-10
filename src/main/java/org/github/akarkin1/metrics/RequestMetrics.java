package org.github.akarkin1.metrics;

import java.util.function.Supplier;

public interface RequestMetrics {

  void start(String updateKind);

  <T> T time(MetricComponent component, Supplier<T> action);

  void time(MetricComponent component, Runnable action);

  void finish();

}
