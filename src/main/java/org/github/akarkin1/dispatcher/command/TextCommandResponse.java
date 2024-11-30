package org.github.akarkin1.dispatcher.command;

public record TextCommandResponse(String text, Object ...params) implements CommandResponse {

}
