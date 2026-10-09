package org.github.akarkin1.ui.messenger;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.LaunchScreens;

import java.util.Map;

@RequiredArgsConstructor
public class NodeNotifications {

  public static final String HOSTNAME_PLACEHOLDER = "{{HOSTNAME}}";
  public static final String PUBLIC_IP_PLACEHOLDER = "{{PUBLIC_IP}}";

  private final LaunchScreens launchScreens;
  private final ScreenRenderer renderer;

  public Map<String, String> build(UiContext context, Integer messageId, String regionId) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

}
