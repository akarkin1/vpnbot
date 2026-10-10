package org.github.akarkin1.ui.messenger;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

public record RenderedMessage(String text, InlineKeyboardMarkup keyboard) {
}
