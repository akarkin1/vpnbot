package org.github.akarkin1.e2e;

import org.testcontainers.containers.ContainerState;

public interface TestContainerInitializer<S extends ContainerState, C> {

  void initialize(S container);

  C getClient();

}
