package org.github.akarkin1.exception;

public class CommandExecutionFailedException extends RuntimeException {

  public CommandExecutionFailedException(String message) {
    super(message);
  }
}
