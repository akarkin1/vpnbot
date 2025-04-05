package org.github.akarkin1.dispatcher.response;

public record TextCommandResponse(String text, Object ...params) implements CommandResponse {

}
