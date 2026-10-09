package org.github.akarkin1.ui.messenger;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.LaunchScreens;
import org.github.akarkin1.ui.screen.Screen;
import org.github.akarkin1.util.JsonUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the environment variables with which the node container reports its state to the chat:
 * the texts are rendered here, the container only fills in the placeholders.
 */
@RequiredArgsConstructor
public class NodeNotifications {

  public static final String HOSTNAME_PLACEHOLDER = "{{HOSTNAME}}";
  public static final String PUBLIC_IP_PLACEHOLDER = "{{PUBLIC_IP}}";
  public static final String TASK_ID_PLACEHOLDER = "{{TASK_ID}}";

  private final LaunchScreens launchScreens;
  private final ScreenRenderer renderer;

  public Map<String, String> build(UiContext context, Integer messageId, String regionId) {
    Map<String, String> environment = new LinkedHashMap<>();
    environment.put("TG_CHAT_ID", String.valueOf(context.chatId()));
    environment.put("TG_MESSAGE_ID", String.valueOf(messageId));
    put(environment, "TG_READY", context,
        launchScreens.ready(HOSTNAME_PLACEHOLDER, regionId, PUBLIC_IP_PLACEHOLDER,
                            TASK_ID_PLACEHOLDER));
    put(environment, "TG_IDLE_WARNING", context, launchScreens.idleWarning(HOSTNAME_PLACEHOLDER));
    put(environment, "TG_STOPPED", context, launchScreens.stopped(HOSTNAME_PLACEHOLDER, regionId));
    put(environment, "TG_STOPPED_CARD", context,
        launchScreens.stoppedCard(HOSTNAME_PLACEHOLDER, regionId));
    return environment;
  }

  private void put(Map<String, String> environment, String prefix, UiContext context, Screen screen) {
    RenderedMessage message = renderer.render(context, screen);
    environment.put(prefix + "_TEXT", message.text());
    if (message.keyboard() != null) {
      environment.put(prefix + "_MARKUP", JsonUtils.toJson(message.keyboard()));
    }
  }

}
