package org.github.akarkin1.ui.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.HelpScreen;
import org.github.akarkin1.ui.screen.HomeScreen;

@Log4j2
@RequiredArgsConstructor
public class HomeController {

  private final TailscaleNodeService nodeService;
  private final Authorizer authorizer;
  private final UiMessenger messenger;
  private final HomeScreen homeScreen;
  private final HelpScreen helpScreen;

  public void showHome(UiContext context) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void refreshHome(UiContext context, Integer messageId) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void showHelp(UiContext context, Integer messageId) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

}
