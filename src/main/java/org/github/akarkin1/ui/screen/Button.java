package org.github.akarkin1.ui.screen;

import org.github.akarkin1.ui.UiAction;

public record Button(String label, String callbackData, String url) {

  public static Button action(String label, UiAction action) {
    return new Button(label, action.encode(), null);
  }

  public static Button link(String label, String url) {
    return new Button(label, null, url);
  }

}
