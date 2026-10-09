package org.github.akarkin1.util;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

/** A thread-safe test {@link Clock} that only moves when {@link #advance(Duration)} is called. */
public class MutableClock extends Clock {

  private final AtomicLong epochMillis;

  public MutableClock(Instant start) {
    this.epochMillis = new AtomicLong(start.toEpochMilli());
  }

  public void advance(Duration duration) {
    epochMillis.addAndGet(duration.toMillis());
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
