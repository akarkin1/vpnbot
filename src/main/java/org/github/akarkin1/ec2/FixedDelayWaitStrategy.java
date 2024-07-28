package org.github.akarkin1.ec2;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class FixedDelayWaitStrategy implements WaitStrategy {
  private final long fixedWaitTime;

  @Override
  public long getWaitTime(int iterationNumber) {
    return fixedWaitTime;
  }

  public static FixedDelayWaitStrategy create(long delay) {
    return new FixedDelayWaitStrategy(delay);
  }
}
