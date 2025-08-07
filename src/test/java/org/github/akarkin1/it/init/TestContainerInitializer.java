package org.github.akarkin1.it.init;

import org.testcontainers.containers.ContainerState;

public interface TestContainerInitializer<S extends ContainerState> {

  void initialize(S container);

}
