package org.github.akarkin1.ui;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.translation.Translator;
import org.telegram.telegrambots.meta.bots.AbsSender;

public class UiConfigurer {

  public UiRouter configure(AbsSender sender, Translator translator,
                            TailscaleNodeService nodeService, Authorizer authorizer) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

}
