package org.github.akarkin1.ui.controller;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.ui.NodeRef;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.LaunchScreens;
import org.github.akarkin1.ui.screen.NodeScreens;

@RequiredArgsConstructor
public class NodeController {

  private final TailscaleNodeService nodeService;
  private final NodeAccess nodeAccess;
  private final UiMessenger messenger;
  private final NodeScreens nodeScreens;
  private final LaunchScreens launchScreens;

  public void use(UiContext context, Integer messageId, NodeRef node) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void stop(UiContext context, Integer messageId, NodeRef node) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void confirmStop(UiContext context, Integer messageId, NodeRef node) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

}
