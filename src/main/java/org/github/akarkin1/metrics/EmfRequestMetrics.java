package org.github.akarkin1.metrics;

import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.util.function.Consumer;
import java.util.function.Supplier;

@RequiredArgsConstructor
public class EmfRequestMetrics implements RequestMetrics {

  private final boolean enabled;
  private final Clock clock;
  private final Consumer<String> output;

  @Override
  public void start(String updateKind) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public <T> T time(MetricComponent component, Supplier<T> action) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void time(MetricComponent component, Runnable action) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void finish() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

}
