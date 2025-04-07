package org.github.akarkin1.dispatcher.response;

public interface EmptyResponse extends CommandResponse {
  EmptyResponse NONE = new EmptyResponse() {
  };
}
