package org.github.akarkin1.ui.messenger;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.translation.Translator;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.Screen;

@RequiredArgsConstructor
public class ScreenRenderer {

  private final Translator translator;

  public RenderedMessage render(UiContext context, Screen screen) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

}
