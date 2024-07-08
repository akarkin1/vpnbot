package org.github.akarkin1.dispatcher.command;

public interface EmptyResponse extends CommandResponse {
  EmptyResponse NONE = new EmptyResponse() {
  };
}
