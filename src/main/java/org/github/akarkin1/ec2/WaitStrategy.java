package org.github.akarkin1.ec2;

public interface WaitStrategy {

  long getWaitTime(int iterationNumber);

}
