package org.github.akarkin1.e2e.init;

import org.testcontainers.containers.ContainerState;

public interface TestContainerInitializer<S extends ContainerState> {

  void initialize(S container);

}
