package org.github.akarkin1.dispatcher;

import org.github.akarkin1.auth.TgUser;

/** The system commands a user may run, as the {@code /help} list. */
public interface CommandCatalog {

  String supportedCommands(TgUser user);

}
