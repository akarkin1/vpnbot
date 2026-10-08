package org.github.akarkin1.ui.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.LaunchScreens;

@Log4j2
@RequiredArgsConstructor
public class LaunchController {

  private final TailscaleNodeService nodeService;
  private final Authorizer authorizer;
  private final UiMessenger messenger;
  private final LaunchScreens launchScreens;

  public void launch(UiContext context, Integer messageId, String regionId) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

}
