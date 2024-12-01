package org.github.akarkin1.message;

@FunctionalInterface
public interface MessageConsumer {

  void accept(String message, Object ...params);

}
