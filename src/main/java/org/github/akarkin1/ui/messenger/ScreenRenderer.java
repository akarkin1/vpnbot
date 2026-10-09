package org.github.akarkin1.ui.messenger;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.translation.Translator;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.Button;
import org.github.akarkin1.ui.screen.Screen;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@RequiredArgsConstructor
public class ScreenRenderer {

  private final Translator translator;

  public RenderedMessage render(UiContext context, Screen screen) {
    return new RenderedMessage(text(context, screen), keyboard(context, screen));
  }

  private String text(UiContext context, Screen screen) {
    Object[] escapedParams = screen.params().stream()
        .map(param -> Html.escape(String.valueOf(param)))
        .toArray();
    return translator.translate(context.languageCode(), screen.template(), escapedParams);
  }

  private InlineKeyboardMarkup keyboard(UiContext context, Screen screen) {
    if (screen.keyboard().isEmpty()) {
      return null;
    }

    List<List<InlineKeyboardButton>> rows = screen.keyboard().stream()
        .map(row -> row.stream()
            .map(button -> inlineButton(context, button))
            .toList())
        .toList();
    return new InlineKeyboardMarkup(rows);
  }

  private InlineKeyboardButton inlineButton(UiContext context, Button button) {
    InlineKeyboardButton inlineButton = new InlineKeyboardButton(
        translator.translate(context.languageCode(), button.label()));
    inlineButton.setCallbackData(button.callbackData());
    inlineButton.setUrl(button.url());
    return inlineButton;
  }

}
