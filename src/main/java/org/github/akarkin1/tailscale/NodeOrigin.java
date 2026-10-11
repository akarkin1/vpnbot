package org.github.akarkin1.tailscale;


/** Describes what originates the node's start. It includes user identifier along with user preferences (languageCode)
 * and the id of the chat from which the launch was originated. Tagged on the task as {@code RunBy} (the id) and
 * {@code RunByName}. */
public record NodeOrigin(long userId, String username, Long chatId, String languageCode) {
}
