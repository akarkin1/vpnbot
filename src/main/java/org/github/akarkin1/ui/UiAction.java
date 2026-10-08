package org.github.akarkin1.ui;

import java.util.Optional;

public record UiAction(Type type, String arg) {

  public enum Type { HOME, HELP, RUN }

  public static UiAction home() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public static UiAction help() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public static UiAction run(String regionId) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public String encode() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public static Optional<UiAction> decode(String data) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

}
