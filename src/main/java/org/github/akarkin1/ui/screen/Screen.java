package org.github.akarkin1.ui.screen;

import java.util.List;

public record Screen(String template, List<Object> params, List<List<Button>> keyboard) {

  public Screen {
    params = List.copyOf(params);
    keyboard = keyboard.stream()
        .map(List::copyOf)
        .toList();
  }

}
