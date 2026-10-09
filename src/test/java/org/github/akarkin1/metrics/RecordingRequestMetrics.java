package org.github.akarkin1.metrics;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Test double for {@link RequestMetrics}: runs every timed action and records which component it
 * was timed under. {@link #isTiming(MetricComponent)} tells whether code runs inside a timing.
 */
public class RecordingRequestMetrics implements RequestMetrics {

  private final List<MetricComponent> timed = new CopyOnWriteArrayList<>();
  private final Set<MetricComponent> active = ConcurrentHashMap.newKeySet();

  @Override
  public void start(String updateKind) {
  }

  @Override
  public <T> T time(MetricComponent component, Supplier<T> action) {
    timed.add(component);
    boolean outermost = active.add(component);
    try {
      return action.get();
    } finally {
      if (outermost) {
        active.remove(component);
      }
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
  }

  public List<MetricComponent> timed() {
    return List.copyOf(timed);
  }

  public boolean isTiming(MetricComponent component) {
    return active.contains(component);
  }

}
