package org.github.akarkin1.tailscale;

/** Who starts a node: tagged on the task as {@code RunBy} (the id) and {@code RunByName}. */
public record NodeOwner(long userId, String username, Long chatId, String languageCode) {
}
